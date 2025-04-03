package com.WPILogParser;

import edu.wpi.first.util.datalog.DataLogReader;
import edu.wpi.first.util.datalog.DataLogRecord;

import java.io.IOException;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Map;

public final class PrintLog {
    // private static final DateTimeFormatter m_timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Main function. */
    public static void main(String[] args) {
        // if (args.length != 1) {
        // System.err.println("Usage: printlog <file>");
        // System.exit(1);
        // return;
        // }
        int records = 0;
        long lastTimestamp = -1;
        DataLogReader reader;
        try {
            reader = new DataLogReader("./test.wpilog");
        } catch (IOException ex) {
            System.err.println("could not open file: " + ex.getMessage());
            System.exit(1);
            return;
        }
        if (!reader.isValid()) {
            System.err.println("not a log file");
            System.exit(1);
            return;
        }

        // LocalDateTime now = LocalDateTime.now();
        // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss");
        // String formattedDateTime = now.format(formatter);
        // CSVWriter w;
        // try {
        //     w = new CSVWriter(new FileWriter("./output." + formattedDateTime + ".csv"));
        // } catch(IOException ex) {
        //     System.err.println("could not create output file");
        //     return;
        // }

        Map<Integer, DataLogRecord.StartRecordData> entries = new HashMap<>();
        for (DataLogRecord record : reader) {
            if (lastTimestamp != record.getTimestamp()) {
                records++;
                lastTimestamp = record.getTimestamp();
            }
            if (record.isStart()) {
                try {
                    DataLogRecord.StartRecordData data = record.getStartData();
                    // System.out.println("Start(" + data.entry + ", name='" + data.name + "', type='" + data.type + "', metadata='" + data.metadata + "') [" + (record.getTimestamp() / 1000000.0) + "]");
                    // if (entries.containsKey(data.entry)) { System.out.println("...DUPLICATE entry ID, overriding"); }
                    entries.put(data.entry, data);
                } catch (InputMismatchException ex) {
                    System.out.println("Start(INVALID)");
                }
            } else if (record.isFinish()) {
                try {
                    int entry = record.getFinishEntry();
                    System.out.println("Finish(" + entry + ") [" + (record.getTimestamp() / 1000000.0) + "]");
                    if (!entries.containsKey(entry)) {
                        System.out.println("...ID not found");
                    } else {
                        entries.remove(entry);
                    }
                } catch (InputMismatchException ex) {
                    System.out.println("Finish(INVALID)");
                }
            } else if (record.isSetMetadata()) {
                try {
                    DataLogRecord.MetadataRecordData data = record.getSetMetadataData();
                    System.out.println("SetMetadata(" + data.entry + ", '" + data.metadata + "') [" + (record.getTimestamp() / 1000000.0) + "]");
                    if (!entries.containsKey(data.entry)) {
                        System.out.println("...ID not found");
                    }
                } catch (InputMismatchException ex) {
                    System.out.println("SetMetadata(INVALID)");
                }
            } else if (record.isControl()) {
                System.out.println("Unrecognized control record");
            } else {
                // System.out.print("Data(" + record.getEntry() + ", size=" + record.getSize() + ") ");
                DataLogRecord.StartRecordData entry = entries.get(record.getEntry());
                if (entry == null) {
                    System.out.println("<ID not found>");
                    continue;
                }
                // System.out.print("<name='" + entry.name + "', type='" + entry.type + "'> [" + (record.getTimestamp() / 1000000.0) + "] : ");

                //gather a list of the names of entries we're interested in; iterate through the log and dump just those entries to a csv
                // /DriverStation/Joystick0/POVs[0] - autodrive to target
                // /DriverStation/Joystick0/ButtonValues - 16 = intake, 32 = outtake, 
                // /DriverStation/Joystick0/AxisValues/2 - strafe left
                // /DriverStation/Joystick0/AxisValues/3 - strafe right

                try {
                    // // handle systemTime specially
                    // if ("systemTime".equals(entry.name) && "int64".equals(entry.type)) {
                    //     long val = record.getInteger();
                    //     System.out.println("  " + m_timeFormatter.format(LocalDateTime.ofEpochSecond(val / 1000000, 0, ZoneOffset.UTC)) + "." + String.format("%06d", val % 1000000));
                    //     continue;
                    // }

                    // switch (entry.type) {
                    //     case "float" -> System.out.println(" " + record.getFloat());
                    //     case "double" -> System.out.println(" " + record.getDouble());
                    //     case "int64" -> System.out.println(" " + record.getInteger());
                    //     case "string", "json" -> System.out.println(" '" + record.getString() + "'");
                    //     case "boolean" -> System.out.println(" " + record.getBoolean());
                    //     case "double[]" -> System.out.println(" " +
                    //     List.of(record.getDoubleArray()));
                    //     case "int64[]" -> System.out.println(" " +
                    //     List.of(record.getIntegerArray()));
                    //     case "string[]" -> System.out.println(" " +
                    //     List.of(record.getStringArray()));
                    //     default -> {
                    //         System.out.println();
                    //     }
                    // }

                    // if (entry.type.equals("int64[]")) {
                    //    System.out.println(entry.name);
                    //    long[] vals = record.getIntegerArray();
                    //     for (int i = 0; i < vals.length; i++) {
                    //         System.out.println(vals[i]);
                    //     }
                    // }

                    // if (entry.type.equals("double[]")) {
                    //    System.out.println(entry.name);
                    //    double[] vals = record.getDoubleArray();
                    //     for (int i = 0; i < vals.length; i++) {
                    //         System.out.println(vals[i]);
                    //     }
                    // }   // only includes /PowerDistribution/ChannelCurrent

                    // if (entry.type.equals("string[]")) {
                    //     System.out.println(entry.name);
                    //     String[] vals = record.getStringArray();
                    //     for (int i = 0; i < vals.length; i++) {
                    //         System.out.println(vals[i]);
                    //     }
                    //  }   // >> only includes /RealOutputs/Alerts
                } catch (InputMismatchException ex) {
                    System.out.println("  invalid");
                }

                // ArrayList<String> output = new ArrayList<>();
                // output.add(String.valueOf(record.getTimestamp()));
                // output.add(String.valueOf(record.getEntry()));
                // output.add(entry.name);
                // output.add(entry.type);
                // w.writeNext(output.toArray(new String[output.size()]));
            }
        }

        System.out.println(records);
    }

    private PrintLog() {
    }
}