package com.WPILogParser;

import edu.wpi.first.util.datalog.DataLogReader;
import edu.wpi.first.util.datalog.DataLogRecord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.*;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.*;

// ****************************************************************************************
// A NOTE REGARDING WPILOG STRUCTURE
// The general structure of the log is such that each data element is a different 'record'.
// This is NOT like a tablular data set.  A 'start' record establish a data element - 
// notably the name and data type.  We must gather these into a dictionary as we go, the
// key of which is an int Entry ID (data.entry).  This ID will then appear on data records,
// allowing us to determine the data record/element's name and data type.  It's absolutely
// necessary to know the data type, so we call the right get function.
// ****************************************************************************************

public final class PrintLogSimplified {
    private enum COLUMN {
        TIMESTAMP,
        MATCHPERIOD,
        MATCHTIME,
        ID,
        ENTRY,
        DATATYPE,
        VALUE,
        VALUERAW,
        PIECE,
        ACTION,
        ACTIONDATA,
        CYCLETIME,
        OUTCOME;
    }

    public static void main(String[] args) {
        boolean enableMonitoring = (args.length > 0 && args[0].toLowerCase().equals("-monitor"));
        if (enableMonitoring) {
            // just monitor the input folder for new logs
            Path folder = Paths.get("./input");

            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                folder.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

                System.out.println("Monitoring folder: " + folder.toAbsolutePath());

                while (true) {
                    WatchKey key = watchService.take();

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            Path filename = (Path) event.context();
                            Path filePath = folder.resolve(filename);
                            if (filePath.toAbsolutePath().toString().toLowerCase().endsWith(".wpilog")) {
                                while (!isFileCompletelyWritten(filePath)) {
                                    System.out.println("File " + filePath + " is still being written...");
                                    Thread.sleep(500);
                                }
                                processLogs(new String[] { filePath.toAbsolutePath().toString() });
                            }
                        }
                    }

                    // Reset the key to receive further watch events
                    boolean valid = key.reset();
                    if (!valid) {
                        System.out.println("Watch key no longer valid, exiting...");
                        break;
                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            processLogs(args);
        }
    }

    private static boolean isFileCompletelyWritten(Path path) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            FileLock lock = channel.tryLock(0, Long.MAX_VALUE, true);
            if (lock != null) {
                lock.release();
                channel.close();
                return true;
            }
            channel.close();
        } catch (IOException e) {
            // File is likely still being written
        }
        return false;
    }

    private static void processLogs(String []args) {
        boolean generateRawDump = (args.length > 0 && args[0].toLowerCase().equals("-raw"));
        String[] finalArgs = (generateRawDump ? Arrays.copyOfRange(args, 1, args.length) : args);

        List<String> filePaths = getFilePaths(finalArgs);
        if (filePaths.size() == 0) {
            System.err.println("Either provide one or more paths to log files on the command line or place files in the 'input' folder.");
            return;
        }

        for (String logFilePath : filePaths) {
            if (generateRawDump) dumpRawLog(logFilePath);

            if (!logFilePath.endsWith(".wpilog")) { continue; }

            String outputFilePath = getOutputFilePath(logFilePath);

            File file = new File(outputFilePath + ".xlsx");
            if (file.exists()) {
                System.out.println("Output already exists for " + logFilePath);
                continue;
            }

            System.out.println("Processing " + logFilePath);

            DataLogReader reader;
            try {
                reader = new DataLogReader(logFilePath);
            } catch (IOException ex) {
                System.err.println("ERROR: could not open file: " + ex.getMessage());
                return;
            }
            if (!reader.isValid()) {
                System.err.println("ERROR: not a log file");
                return;
            }

            XSSFWorkbook workbook = new XSSFWorkbook();
            XSSFSheet sheet = workbook.createSheet("data");
            String[] headers = new String[] {"Timestamp", "Period", "M_Time", "ID", "Entry", "Type", "Value", "Value_RAW", "Piece", "Action", "T/Val", "Cycle", "Outcome"};
            int rowIndex = addOutputHeader(sheet, 0, headers);

            int records = 0;
            long timestamp = -1;
            String matchPeriod = "";
            double matchTime = 0.0;
            boolean auto = false;
            Map<Integer, DataLogRecord.StartRecordData> entries = new HashMap<>();
            try {
                for (DataLogRecord record : reader) {
                    // Keeping track of how many records we're processing - this is only used for console output to inform the user.
                    if (timestamp != record.getTimestamp()) {
                        records++;
                        timestamp = record.getTimestamp();
                    }
                    // The wpilog spec allows for other record types, but our logs only seem to carry start and data records.
                    if (record.isStart()) {
                        try {
                            DataLogRecord.StartRecordData data = record.getStartData();
                            entries.put(data.entry, data);
                        } catch (InputMismatchException ex) {
                            System.err.println("WARNING: Start(INVALID)");
                        }
                    } else {
                        DataLogRecord.StartRecordData entry = entries.get(record.getEntry());
                        if (entry == null) {
                            System.err.println("WARNING: <ID not found: " + record.getEntry() + ">");
                            continue;
                        }

                        if (entry.name.equals("/DriverStation/Autonomous")) auto = record.getBoolean();
                        if (entry.name.equals("/DriverStation/MatchTime")) matchTime = record.getDouble();                        
                        matchPeriod = updateMatchPeriod(matchPeriod, auto, record, entry);
                        
                        if (matchPeriod.equals("teleop")) {
                            // this ensures we're only analyzing teleop - we could || "auto" to also look at auto
                            rowIndex = outputEntriesOfInterest(sheet, rowIndex, matchPeriod, matchTime, record, entry);
                        } else if (entry.name.equals("/DriverStation/Enabled") && matchPeriod.equals("match end")) {
                            // once we've reached match end, output a final row - all entries after this will get skipped
                            addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, matchPeriod);
                        }
                    }
                }
            } catch (IllegalArgumentException ex) {
                System.err.println("WARNING: IllegalArgumentException (might be fine - check the output)");
            }

            addCycleTimes(sheet);
            addIntakeTimes(sheet);
            addStrafingTimes(sheet);
            addIntakeToOuttakeTimes(sheet);
            addAlignTimes(sheet);
            addPieceLabels(sheet);
            addElevatorHoming(sheet);
            addClimbTimes(sheet);

            formatOutput(workbook, sheet);
            int maxRow = sheet.getLastRowNum() + 1;

            addSummaryAnalysis(sheet);

            try (FileOutputStream fileOut = new FileOutputStream(outputFilePath + ".xlsx")) {
                workbook.write(fileOut);
                workbook.close();
                System.out.println("Excel file created successfully: " + outputFilePath + ".xlsx");
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println(records + " records processed [" + maxRow + " rows in output]");
        }

        // this should not be necessary, but if monitoring is enabled, because DataLogReader doesn't properly close and dispose of the file handle, we need to encourage garbage collection to dispose of the handle
        System.gc(); 
        try {
            Thread.sleep(100); // Let GC settle
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    private static int outputEntriesOfInterest(XSSFSheet sheet, int rowIndex, String matchPeriod, double matchTime, DataLogRecord record, DataLogRecord.StartRecordData entry) {
        if(entry.name.equals("/RealOutputs/EE/Has Coral")) {
            if(record.getBoolean()) {
                addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, "intake done");
            }
        }

        // autodrive to target; 90 = right; 270 = left; 180 = algae; 0 = station; -1 = no button pressed
        if(entry.name.equals("/DriverStation/Joystick0/POVs")) {
            long[] vals = record.getIntegerArray();
            if(vals.length > 0) {
                String alignStr = "";
                if(vals[0] == 0) {
                    alignStr = "align station";
                } else if(vals[0] == 90) {
                    alignStr = "align right";
                } else if(vals[0] == 180) {
                    alignStr = "align algae";
                } else if(vals[0] == 270) {
                    alignStr = "align left";
                } else if(vals[0] == -1) {
                    alignStr = "(align release)";
                }
                addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, alignStr, vals[0]);
            }
        }

        if(entry.name.equals("/RealOutputs/Align/Error/IsAligned") || entry.name.equals("/RealOutputs/Align/Error/IsAlignedTest")) {
            String aligned = (record.getBoolean() ? "Aligned" : "Not aligned");
            Row row = addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, String.valueOf(record.getBoolean()));
            // setCellValue(row, COLUMN.ACTION, aligned);
        }

        // 16 = intake, 32 = outtake, 1 = slow toggle
        // (This is actually a bitmask, but unless they press two buttons at once, we can just check the int values for simplicity.  If we do ever use the bitmask: 0 A, 1 B, 2 X, 3 Y, 4 LBump, 5 RBump, 6 Back, 7 Start, 8 LStick, 9 RStick.
        if(entry.name.equals("/DriverStation/Joystick0/ButtonValues")) {
            long button = record.getInteger();
            if (button == 16 || button == 32) {
                addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, (button == 16 ? "intake" : "outtake"), button);
            } else if (button == 1) {
                Row row = addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, "SLOW TOGGLE", button);
                setCellValue(row, COLUMN.ACTION, "Slow toggle");
            }
        }

        // /2 - strafe left; /3 - strafe right
        if(entry.name.equals("/DriverStation/Joystick0/AxisValues")) {
            float[] axisValues = record.getFloatArray();
            if(axisValues.length >= 4 && (axisValues[2] > 0.0 || axisValues[3] > 0.0)) {
                if (axisValues[2] > 0.0) {
                    addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, "<- strafe", axisValues[2]);
                } else {
                    addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, "strafe ->", axisValues[3]);
                }
            }
        }

        // climb; 180 = deploy; 0 = retract 
        if(entry.name.equals("/DriverStation/Joystick1/POVs")) {
            long[] vals = record.getIntegerArray();
            if(vals.length > 0 && (vals[0] == 0 || vals[0] == 180)) {
                String alignStr = "";
                if(vals[0] == 0) {
                    alignStr = "climb retract";
                } else if(vals[0] == 180) {
                    alignStr = "climb deploy";
                }
                addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, alignStr, vals[0]);
            }
        }

        // 128 = home elevator
        // (this is actually a bitmask, but unless they press two buttons at once, we can just check the int values for simplicity)
        if(entry.name.equals("/DriverStation/Joystick1/ButtonValues")) {
            long button = record.getInteger();
            if (button == 128) {
                addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, "ELEV HOME", button);
            }
        }

        // could monitor for joystick1 axisvalues, but easier to just monitor the offset - this might be an issue if we comment out logging for elevator offset (joystick axisvalues is lower layer)
        if(entry.name.equals("/RealOutputs/Elevator/Offset")) {
            addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, "ELEV OFFSET", record.getDouble());
        }

        if(entry.name.equals("/RealOutputs/Arm/Mode") || entry.name.equals("/RealOutputs/Elevator Mode")) {
            addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, record.getString().toUpperCase());
        }

        return rowIndex;
    }

    private static String getOutputFilePath(String logFilePath) {
        Pattern pattern = Pattern.compile("akit_(\\d{2})-.*_(\\w+_\\w+)\\.wpilog");
        Matcher matcher = pattern.matcher(logFilePath);
        String matchPrefix = "";
        if (matcher.find()) {
            String year = matcher.group(1);
            String match = matcher.group(2);
            matchPrefix = "20" + year + match;
        }

        File file = new File(logFilePath);
        String folderPath = file.getParent();

        return folderPath + "\\" + matchPrefix;
    }

    private static List<String> getFilePaths(String[] args) {
        List<String> filePaths = new ArrayList<>();
        if(args.length > 0) {
            for (String filePath : args) {
                filePaths.add(filePath);
            }
        } else {
            // No command line input - process everything in the ./input/ folder
            File inputFolder = new File(System.getProperty("user.dir"), "input");
            if (inputFolder.exists() && inputFolder.isDirectory()) {
                File[] files = inputFolder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            filePaths.add(file.getAbsolutePath());
                        }
                    }
                }
            }
        }
        return filePaths;
    }

    private static String updateMatchPeriod(String matchPeriod, boolean auto, DataLogRecord record, DataLogRecord.StartRecordData entry) {
        // Once the robot is connected to the FMS, autonomous will be TRUE and enabled will be FALSE.  So, once we see enabled go TRUE while auto is TRUE, we know the Auto period has begun.  After Auto period, there will be a momentary (~2 seconds) Disabled period.  When enabled goes TRUE again, Teleop period has begun - autonomous will be false, but we don't need to check that as we're watching for the transition from Disabled period.
        if(entry.name.equals("/DriverStation/Enabled") && record.getBoolean() && auto) {
            matchPeriod = "auto";
        } else if (entry.name.equals("/DriverStation/Enabled") && !record.getBoolean() && matchPeriod.equals("auto")) {
            matchPeriod = "disabled";
        } else if (entry.name.equals("/DriverStation/Enabled") && record.getBoolean() && matchPeriod.equals("disabled")) {
            matchPeriod = "teleop";
        } else if (entry.name.equals("/DriverStation/Enabled") && !record.getBoolean() && matchPeriod.equals("teleop")) {
            matchPeriod = "match end";
        }
        return matchPeriod;
    }

    private static int addOutputHeader(Sheet sheet, int rowIndex, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
        return row.getRowNum();
    }

    private static Row addOutputRow(Sheet sheet, int rowIndex, String matchPeriod, double matchTime, DataLogRecord record, DataLogRecord.StartRecordData entry, String valueStr) {
        return addOutputRow(sheet, rowIndex, matchPeriod, matchTime, record, entry, valueStr, Double.MIN_VALUE);
    }

    private static Row addOutputRow(Sheet sheet, int rowIndex, String matchPeriod, double matchTime, DataLogRecord record, DataLogRecord.StartRecordData entry, String valueStr, double valueRaw) {
        Row row = sheet.createRow(rowIndex);
        setCellValue(row, COLUMN.TIMESTAMP, record.getTimestamp() / 1000000.0);
        setCellValue(row, COLUMN.MATCHPERIOD, matchPeriod);
        setCellValue(row, COLUMN.MATCHTIME, matchTime);
        setCellValue(row, COLUMN.MATCHTIME, String.format("%02d:%02d", (int)matchTime / 60, (int)matchTime % 60));
        setCellValue(row, COLUMN.ID, record.getEntry());
        setCellValue(row, COLUMN.ENTRY, entry.name);
        setCellValue(row, COLUMN.DATATYPE, entry.type);
        setCellValue(row, COLUMN.VALUE, valueStr);
        if (Math.abs(valueRaw) != Double.MIN_VALUE)
            setCellValue(row, COLUMN.VALUERAW, valueRaw);

        return row; 
    }

    private static void addCycleTimes(Sheet sheet) {
        int maxRow = sheet.getLastRowNum() + 1;
        int startRow = 0;
        
        for (int r = 2; r <= maxRow; r++) {
            Row row = sheet.getRow(r - 1);
            if(getCellString(row, COLUMN.VALUE) == "outtake") {
                if(startRow > 0) {
                    setCellFormula(row, COLUMN.CYCLETIME, "A" + r + "-A" + startRow);
                }
                startRow = r;
            }
        }
    }

    private static void addIntakeTimes(Sheet sheet) {
        int maxRow = sheet.getLastRowNum() + 1;
        int startRow = 0;
        
        for (int r = 2; r <= maxRow; r++) {
            Row row = sheet.getRow(r - 1);
            if(getCellString(row, COLUMN.VALUE) == "intake" && startRow == 0) {
                startRow = r;
            }
            else if(getCellString(row, COLUMN.VALUE) == "intake done" && startRow > 0) {
                setCellValue(row, COLUMN.ACTION, "Time to intake");
                setCellFormula(row, COLUMN.ACTIONDATA, "A" + r + "-A" + startRow);
                startRow = 0;
            }
        }
    }

    private static void addStrafingTimes(Sheet sheet) {
        int maxRow = sheet.getLastRowNum() + 1;
        int startRow = 0;
        int endRow = 0;
        
        for (int r = 2; r <= maxRow; r++) {
            Row row = sheet.getRow(r - 1);
            if(getCellString(row, COLUMN.VALUE).contains("strafe") && startRow == 0) {
                startRow = r;
            } else if((getCellString(row, COLUMN.VALUE) == "intake done" || getCellString(row, COLUMN.VALUE) == "outtake") && startRow > 0 && endRow > startRow) {
                Row row2 = sheet.getRow(endRow - 1);
                setCellValue(row2, COLUMN.ACTION, "Time spent strafing");
                setCellFormula(row2, COLUMN.ACTIONDATA, "A" + endRow + "-A" + startRow);
                startRow = 0;
                endRow = 0;
            } else if (getCellString(row, COLUMN.VALUE).contains("strafe")) {
                endRow = r;
            }
        }
    }

    private static void addIntakeToOuttakeTimes(Sheet sheet) {
        int maxRow = sheet.getLastRowNum() + 1;
        int startRow = 0;
        String piece = "";
        String level = "";
        
        for (int r = 2; r <= maxRow; r++) {
            Row row = sheet.getRow(r - 1);
            if (getCellString(row, COLUMN.VALUE).endsWith("algae")) {
                piece = "ALGAE";
            } else if (getCellString(row, COLUMN.VALUE).startsWith("align")) {
                piece = "CORAL";
            } else if (getCellString(row, COLUMN.ENTRY).equals("/RealOutputs/Arm/Mode")) {
                level = getCellString(row, COLUMN.VALUE);
            }
            if (getCellString(row, COLUMN.VALUE) == "intake") {
                startRow = 0;
            } else if(getCellString(row, COLUMN.VALUE) == "intake done" && startRow == 0) {
                startRow = r;
            } else if (getCellString(row, COLUMN.VALUE) == "outtake" && startRow > 0) {
                setCellValue(row, COLUMN.ACTION, "In to out");
                setCellFormula(row, COLUMN.ACTIONDATA, "A" + r + "-A" + startRow);
                String outcome = piece;
                if (piece.equals("CORAL") && level.length() > 0) outcome += " " + level;
                setCellValue(row, COLUMN.OUTCOME, outcome);
                startRow = 0;
            }
        }
    }

    private static void addAlignTimes(Sheet sheet) {
        int maxRow = sheet.getLastRowNum() + 1;
        int startRow = 0;
        int endRow = 0;
        
        for (int r = 2; r <= maxRow; r++) {
            Row row = sheet.getRow(r - 1);
            if(!(getCellString(row, COLUMN.ENTRY).equals("/RealOutputs/Arm/Mode") || getCellString(row, COLUMN.ENTRY).equals("/RealOutputs/Elevator Mode"))) {
                if(getCellString(row, COLUMN.VALUE).startsWith("align") && startRow == 0) {
                    startRow = r;
                }
                else if(getCellString(row, COLUMN.VALUE).startsWith("align") && endRow > startRow) {
                    endRow = 0;
                } else if (!getCellString(row, COLUMN.VALUE).contains("align") && startRow > 0 && endRow > startRow) {
                    Row row2 = sheet.getRow(endRow - 1);
                    setCellValue(row2, COLUMN.ACTION, "Time spent aligning");
                    setCellFormula(row2, COLUMN.ACTIONDATA, "A" + endRow + "-A" + startRow);
                    startRow = 0;
                    endRow = 0;
                } else if (getCellString(row, COLUMN.VALUE) == "(align release)") {
                    endRow = r;
                }
            }
        }
    }

    private static void addPieceLabels(Sheet sheet) {
        int maxRow = sheet.getLastRowNum() + 1;
        int piece = 1;

        for (int r = 2; r <= maxRow; r++) {
            Row row = sheet.getRow(r - 1);
            if (row.getCell(COLUMN.ACTION.ordinal()) != null && !getCellString(row, COLUMN.VALUE).equals("SLOW TOGGLE")) {
                setCellValue(row, COLUMN.PIECE, "Piece " + piece);
                if (getCellString(row, COLUMN.VALUE) == "outtake") piece++;
            }
        }
    }

    private static void addElevatorHoming(Sheet sheet) {
        int maxRow = sheet.getLastRowNum() + 1;
        double offset = 0.0;

        for (int r = 2; r <= maxRow; r++) {
            Row row = sheet.getRow(r - 1);
            if(getCellString(row, COLUMN.VALUE).equals("ELEV OFFSET")) {
                double valraw = getCellNumber(row, COLUMN.VALUERAW);
                if(Math.abs(valraw) > 0.0) {
                    offset = getCellNumber(row, COLUMN.VALUERAW);
                }
            } else if(getCellString(row, COLUMN.VALUE).equals("ELEV HOME")) {
                setCellValue(row, COLUMN.ACTION, "Elevator homed/zeroed");
                setCellValue(row, COLUMN.ACTIONDATA, offset);
            }
        }
    }

    private static void addClimbTimes(Sheet sheet) {
        int maxRow = sheet.getLastRowNum() + 1;
        double firstTimestamp = getCellNumber(sheet.getRow(1), COLUMN.TIMESTAMP);
        int startRow = 0;
        int endRow = 0;
        
        for (int r = 2; r <= maxRow; r++) {
            Row row = sheet.getRow(r - 1);
            if (getCellNumber(row, COLUMN.TIMESTAMP) > firstTimestamp + 60.0) {
                if (getCellString(row, COLUMN.VALUE).startsWith("climb") && startRow == 0) {
                    startRow = r;
                } else if ((!getCellString(row, COLUMN.VALUE).startsWith("climb") && endRow > startRow)) {
                    Row row2 = sheet.getRow(endRow - 1);
                    setCellValue(row2, COLUMN.ACTION, "Time spent climbing");
                    setCellFormula(row2, COLUMN.ACTIONDATA, "A" + endRow + "-A" + startRow);
                    startRow = 0;
                    endRow = 0;
                } else if (r == maxRow && startRow > 0) {
                    setCellValue(row, COLUMN.ACTION, "Time spent climbing");
                    setCellFormula(row, COLUMN.ACTIONDATA, "A" + r + "-A" + startRow);
                    startRow = 0;
                    endRow = 0;
                } else if (getCellString(row, COLUMN.VALUE).startsWith("climb") && r < maxRow) {
                    endRow = r;
                }
            }
        }
    }

    private static String getCellString(Row row, COLUMN column) {
        Cell cell = row.getCell(column.ordinal());
        return (cell != null ? cell.getStringCellValue() : "");
    }

    private static double getCellNumber(Row row, COLUMN column) {
        Cell cell = row.getCell(column.ordinal());
        return (cell != null ? cell.getNumericCellValue() : 0.0);
    }

    private static void setCellValue(Row row, COLUMN column, String value) {
        Cell cell = row.createCell(column.ordinal());
        if (cell != null ) cell.setCellValue(value);
    }

    private static void setCellValue(Row row, COLUMN column, double value) {
        Cell cell = row.createCell(column.ordinal());
        if (cell != null ) cell.setCellValue(value);
    }

    private static void setCellFormula(Row row, COLUMN column, String formula) {
        Cell cell = row.createCell(column.ordinal());
        if (cell != null ) cell.setCellFormula(formula);
    }

    private static void formatOutput(XSSFWorkbook workbook, XSSFSheet sheet) {
        byte[] clr_lightred = new byte[] {(byte)255, (byte)199, (byte)206};
        byte[] clr_darkred = new byte[] {(byte)156, (byte)0, (byte)6};
        byte[] clr_lightyellow = new byte[] {(byte)255, (byte)235, (byte)156};
        byte[] clr_darkyellow = new byte[] {(byte)156, (byte)87, (byte)0};
        byte[] clr_lightgreen = new byte[] {(byte)198, (byte)239, (byte)206};
        byte[] clr_darkgreen = new byte[] {(byte)0, (byte)97, (byte)0};
        byte[] clr_lightblue = new byte[] {(byte)166, (byte)201, (byte)236};
        byte[] clr_darkblue = new byte[] {(byte)21, (byte)61, (byte)100};

        SheetConditionalFormatting sheetCF = sheet.getSheetConditionalFormatting();

        ConditionalFormattingRule strafRule = createConditionalFormattingRule(sheetCF, "straf", clr_lightred, clr_darkred);  //purposefully "straf" to catch "strafe" and "strafing"
        ConditionalFormattingRule intakeRule = createConditionalFormattingRule(sheetCF, "intake", clr_lightyellow, clr_darkyellow);
        ConditionalFormattingRule alignRule = createConditionalFormattingRule(sheetCF, "align", clr_lightgreen, clr_darkgreen);
        ConditionalFormattingRule outtakeRule = createConditionalFormattingRule(sheetCF, "out", clr_lightblue, clr_darkblue);

        int maxRow = sheet.getLastRowNum() + 1;
        CellRangeAddress[] regions = {
            CellRangeAddress.valueOf("G1:G" + maxRow),
            CellRangeAddress.valueOf("J1:J" + maxRow)
        };
        sheetCF.addConditionalFormatting(regions, new ConditionalFormattingRule[] { outtakeRule, strafRule, intakeRule, alignRule });

        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        for (int i = 0; i < maxRow; i++) {
            Cell cell = sheet.getRow(i).getCell(COLUMN.VALUE.ordinal());
            if (cell != null) cell.setCellStyle(style);
        }

        CellStyle cellStyle = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        cellStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));
        for (int i = 1; i < maxRow; i++) {
            Cell time = sheet.getRow(i).getCell(COLUMN.ACTIONDATA.ordinal());
            if (time != null) time.setCellStyle(cellStyle);
            Cell cycle = sheet.getRow(i).getCell(COLUMN.CYCLETIME.ordinal());
            if (cycle != null) cycle.setCellStyle(cellStyle);
        }

        sheet.setColumnWidth(COLUMN.TIMESTAMP.ordinal(), 11 * 256);
        sheet.setColumnWidth(COLUMN.MATCHPERIOD.ordinal(), 9 * 256);
        sheet.setColumnWidth(COLUMN.MATCHTIME.ordinal(), 7 * 256);
        sheet.setColumnWidth(COLUMN.ID.ordinal(), 0);
        sheet.setColumnWidth(COLUMN.ENTRY.ordinal(), 31 * 256);
        sheet.setColumnWidth(COLUMN.DATATYPE.ordinal(), 0);
        sheet.setColumnWidth(COLUMN.VALUE.ordinal(), 13 * 256);
        sheet.setColumnWidth(COLUMN.VALUERAW.ordinal(), 12 * 256);
        sheet.setColumnWidth(COLUMN.PIECE.ordinal(), 8 * 256);
        sheet.setColumnWidth(COLUMN.ACTION.ordinal(), 17 * 256);
        sheet.setColumnWidth(COLUMN.ACTIONDATA.ordinal(), 7 * 256);
        sheet.setColumnWidth(COLUMN.CYCLETIME.ordinal(), 7 * 256);
        sheet.setColumnWidth(COLUMN.OUTCOME.ordinal(), 20 * 256);

        sheet.createFreezePane(0, 1);
        
        convertToTable(workbook, sheet);
    }

    private static ConditionalFormattingRule createConditionalFormattingRule(SheetConditionalFormatting sheetCF, String textToMatch, byte[] backgroundColor, byte[] foregroundColor) {
        ConditionalFormattingRule rule = sheetCF.createConditionalFormattingRule("ISNUMBER(SEARCH(\"" + textToMatch + "\", G1))");
        PatternFormatting pattern = rule.createPatternFormatting();
        pattern.setFillBackgroundColor(new XSSFColor(backgroundColor));
        pattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
        rule.createFontFormatting().setFontColor(new XSSFColor(foregroundColor));
        return rule;
    }

    private static void convertToTable(XSSFWorkbook workbook, XSSFSheet sheet) {
        int lastRow = sheet.getLastRowNum();
        int lastCol = sheet.getRow(0).getLastCellNum() - 1;

        CellReference topLeft = new CellReference(0, 0);
        CellReference bottomRight = new CellReference(lastRow, lastCol);
        AreaReference area = new AreaReference(topLeft, bottomRight, workbook.getSpreadsheetVersion());

        XSSFTable table = sheet.createTable(area);
        table.setDisplayName("Analysis");

        CTTable ctTable = table.getCTTable();
        CTTableStyleInfo style = ctTable.addNewTableStyleInfo();
        style.setName("TableStyleMedium1");
        style.setShowRowStripes(true);
        style.setShowColumnStripes(false);
        
        filterValues(sheet, COLUMN.ACTION, "Time to intake", "Time spent aligning", "In to out", "Time spent strafing", "Time spent climbing", "Elevator homed/zeroed", "Slow toggle");
    }

    private static void filterValues(XSSFSheet sheet, COLUMN column, String... filterValues) {
        XSSFTable table = sheet.getTables().get(0);
        CTTable ctTable = table.getCTTable();
        CTAutoFilter filter = ctTable.addNewAutoFilter();
        CTFilterColumn filterColumn = filter.addNewFilterColumn();
        filterColumn.setColId(column.ordinal());
        CTCustomFilters customFilters = filterColumn.addNewCustomFilters();

        for (String filterValue : filterValues) {
            CTCustomFilter filterCriteria = customFilters.addNewCustomFilter();
            filterCriteria.setOperator(STFilterOperator.Enum.forInt(1));
            filterCriteria.setVal(filterValue);
        }

        for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            XSSFRow row = sheet.getRow(rowNum);
            if (row != null) {
                XSSFCell cell = row.getCell(column.ordinal());
                boolean match = false;
                if (cell != null) {
                    String cellValue = cell.getStringCellValue();
                    for (String filterValue : filterValues) {
                        if (cellValue.equals(filterValue)) {
                            match = true;
                            break;
                        }
                    }
                }
                if (!match) {
                    row.getCTRow().setHidden(true);
                }
            }
        }
    }

    private static void addSummaryAnalysis(XSSFSheet sheet) {
        int maxRow = sheet.getLastRowNum();

        Row row = sheet.createRow(maxRow + 2);
        row.createCell(0).setCellValue("Avg Cycle Time");
        row.createCell(2).setCellFormula("AVERAGE(L:L)");
        
        row = sheet.createRow(maxRow + 3);
        row.createCell(0).setCellValue("Avg Time Spent Intaking");
        row.createCell(2).setCellFormula("AVERAGEIF(J:J, \"Time to intake\", K:K)");

        row = sheet.createRow(maxRow + 4);
        row.createCell(0).setCellValue("Avg Time Spent Strafing");
        row.createCell(2).setCellFormula("AVERAGEIF(J:J, \"Time spent strafing\", K:K)");

        row = sheet.createRow(maxRow + 5);
        row.createCell(0).setCellValue("Avg Time Spent Aligning");
        row.createCell(2).setCellFormula("AVERAGEIF(J:J, \"Time spent aligning\", K:K)");

        row = sheet.createRow(maxRow + 6);
        row.createCell(0).setCellValue("Game pieces handled");
        row.createCell(2).setCellFormula("SUMPRODUCT((I2:I" + maxRow + "<>\"\")/COUNTIF(I2:I" + maxRow + ", I2:I" + maxRow + "&\"\"))");
    }

    private static void dumpRawLog(String logFilePath) {
        System.out.println("Processing log (RAW) " + logFilePath);
            
        DataLogReader reader;
        try {
            reader = new DataLogReader(logFilePath);
        } catch (IOException ex) {
            System.err.println("ERROR: could not open file: " + ex.getMessage());
            return;
        }
        if (!reader.isValid()) {
            System.err.println("ERROR: not a log file");
            return;
        }

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("data");
        String[] headers = new String[] {"Timestamp", "Period", "M_Time", "ID", "Entry", "Type", "Value_RAW"};
        int rowIndex = addOutputHeader(sheet, 0, headers);

        int records = 0;
        long timestamp = -1;
        String matchPeriod = "";
        double matchTime = 0.0;
        boolean auto = false;
        Map<Integer, DataLogRecord.StartRecordData> entries = new HashMap<>();
        try {
            for (DataLogRecord record : reader) {
                if (timestamp != record.getTimestamp()) {
                    records++;
                    timestamp = record.getTimestamp();
                }
                if (record.isStart()) {
                    try {
                        DataLogRecord.StartRecordData data = record.getStartData();
                        entries.put(data.entry, data);
                    } catch (InputMismatchException ex) {
                        System.err.println("WARNING: Start(INVALID)");
                    }
                } else {
                    DataLogRecord.StartRecordData entry = entries.get(record.getEntry());
                    if (entry == null) {
                        System.err.println("WARNING: <ID not found: " + record.getEntry() + ">");
                        continue;
                    }

                    if(entry.name.equals("/DriverStation/Autonomous")) auto = record.getBoolean();
                    matchPeriod = updateMatchPeriod(matchPeriod, auto, record, entry);

                    if (entry.name.equals("/DriverStation/MatchTime")) matchTime = record.getDouble();                        

                    // The logic below ensures we only dump log for the match itself, not junk before or after while the bot is still powered on.
                    if(matchPeriod.equals("auto") || matchPeriod.equals("disabled") || matchPeriod.equals("teleop") || entry.name.equals("/DriverStation/Enabled")) {
                        // This does not handle custom structs, which currently includes ChassisSpeeds, Pose2d, Rotation2d, SwerveModulePosition, SwerveModuleState, Transform2d, & Translation2d.  Processing these takes special handling that I haven't yet sussed out.
                        switch (entry.type) {
                            case "float" -> addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, String.valueOf(record.getFloat())); 
                            case "double" -> addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, String.valueOf(record.getDouble()));
                            case "int64" -> addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, String.valueOf(record.getInteger()));
                            case "string", "json" -> addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, record.getString());
                            case "boolean" -> addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, String.valueOf(record.getBoolean()));
                            case "float[]" -> {
                                String values = Arrays.toString(record.getFloatArray()).replaceAll("[\\[\\] ]", "");
                                addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, values);
                            }
                            case "double[]" -> {
                                String values = Arrays.toString(record.getDoubleArray()).replaceAll("[\\[\\] ]", "");
                                addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, values);
                            }
                            case "int64[]" -> {
                                String values = Arrays.toString(record.getIntegerArray()).replaceAll("[\\[\\] ]", "");
                                addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, values);
                            }
                            case "string[]" -> addOutputRow(sheet, ++rowIndex, matchPeriod, matchTime, record, entry, String.join(",", record.getStringArray()));
                            default -> { }
                        }
                    }
                }
            }
        } catch (IllegalArgumentException ex) {
            System.err.println("WARNING: IllegalArgumentException (might be fine - check the output)");
        }

        String outputFilePath = getOutputFilePath(logFilePath);

        int maxRow = sheet.getLastRowNum() + 1;

        try (FileOutputStream fileOut = new FileOutputStream(outputFilePath + ".RAW.xlsx")) {
            workbook.write(fileOut);
            workbook.close();
            System.out.println("Excel file (RAW) created successfully: " + outputFilePath + ".RAW.xlsx");
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(records + " records processed (RAW) [" + maxRow+ " rows in output]");
    }

    private PrintLogSimplified() { }
}