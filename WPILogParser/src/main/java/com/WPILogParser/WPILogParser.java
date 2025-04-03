package com.WPILogParser;

import edu.wpi.first.util.datalog.DataLogIterator;
import edu.wpi.first.util.datalog.DataLogReader;
import edu.wpi.first.util.datalog.DataLogRecord;

import java.io.IOException;

// borrowed heavily from the AdvantageKit class WPILOGReader

public class WPILogParser {
    public static void main(String[] args) {
        String logFilePath = "./test.wpilog";
        DataLogReader reader = null;
        DataLogIterator iterator;
        boolean isValid = false;

        try {
            reader = new DataLogReader(logFilePath);
        } catch (IOException e) {
            System.out.println("failed to open file");
        }

        if (!reader.isValid()) {
            System.out.println("log is not a valid WPILOG file");
        } else if (!reader.getExtraHeader().equals("AdvantageKit")) {
            System.out.println("log was not produced by AdvantageKit");
        } else {
            isValid = true;
        }

        if (!isValid)
            return;

        iterator = reader.iterator();

        String typeStr = "";
        while (iterator.hasNext()) {
            DataLogRecord record;
            try {
                record = iterator.next();
            } catch (Exception e) {
                break;
            }

            if (record.isControl()) {
                if (record.isStart()) { // Ignore other control records
                    if (record.getStartData().metadata.equals("{\"source\":\"AdvantageKit\"}")) {
                        System.out.print(record.getStartData().entry + " / " + record.getStartData().name + " / "
                                + record.getStartData().type + " / ");
                        typeStr = record.getStartData().type;
                        if (typeStr.startsWith("proto:") || typeStr.startsWith("struct:")
                                || typeStr.equals("structschema")) {
                            typeStr = "raw";
                        }
                    }
                }
            } else {
                switch (typeStr) {
                    case "raw":
                        System.out.println(record.getRaw());
                        break;
                    case "boolean":
                        System.out.println(record.getBoolean());
                        break;
                    case "int64":
                        System.out.println(record.getInteger());
                        break;
                    case "float":
                        System.out.println(record.getFloat());
                        break;
                    case "double":
                        System.out.println(record.getDouble());
                        break;
                    case "string":
                        System.out.println(record.getString());
                        break;
                    case "boolean[]":
                        System.out.println(record.getBooleanArray());
                        break;
                    case "int64[]":
                        System.out.println(record.getIntegerArray());
                        break;
                    case "float[]":
                        System.out.println(record.getFloatArray());
                        break;
                    case "double[]":
                        System.out.println(record.getDoubleArray());
                        break;
                    case "string[]":
                        System.out.println(record.getStringArray());
                        break;
                }
            }
        }
    }
}
