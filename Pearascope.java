import java.io.*;
import java.util.*;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;  

public class Pearascope {
    public static void main(String[] args) {
        try {
            CSVReader r = new CSVReader(new FileReader("Log_24-03-23_17-54-44_q44.csv"));
            String[] nextLine = r.readNext();

            String[] columns = {
                "/DriverStation/Matchnumber",
                "/DriverStation/AllianceStation",
                "/DriverStation/Autonomous", 
                "/RealOutputs/Amp Bar/Amp Bar Position", 
                "/DriverStation/MatchTime", 
                "/RealOutputs/Transport/Ir Sensor", 
                "/RealOutputs/Drivetrain/Odometry/rotation/value",
                "/RealOutputs/Drivetrain/Odometry/translation/x", 
                "/RealOutputs/Drivetrain/Odometry/translation/y",
                "/RealOutputs/Shooter/NoteVelocity",
                "/RealOutputs/Shooter/Shooter Pivot Position",
                "/RealOutputs/Shooter/Shooter Pivot Intended Position",
            };

            HashMap<String, Integer> columnIndexes = new HashMap<>();
            for (int i = 0; i < nextLine.length; i++) {
                for (String c : columns) {
                    if (nextLine[i].equals(c)) {
                        columnIndexes.put(c, i);
                    }
                }
            }
            // System.err.println(beamBreakIdx + " " + timestampIdx);
            boolean hasNote = true; // preload
            // System.out.print("preload\t");

            CSVWriter w = new CSVWriter(new FileWriter("Output.csv"));

            String[] headers = { "time intook", "intook x", "intook y", "intook theta", 
                "time shot", "shot x", "shot y", "shot theta", 
                "autonomous", "speaker/amp" 
                //"shooter rpm", "pivot position", 
                // "match number", "isRedAlliance" 
            };
            w.writeNext(headers);
            
            ArrayList<String> output = new ArrayList<>();
            output.add("preload");
            output.add("preload");
            output.add("preload");
            output.add("preload");

            while ((nextLine = r.readNext()) != null) {
                if (Integer.parseInt(nextLine[columnIndexes.get("/DriverStation/MatchTime")]) > 0) {
                    if (nextLine[columnIndexes.get("/RealOutputs/Transport/Ir Sensor")].equals("true") != hasNote) {
                        hasNote = !hasNote;
                        // System.out.print(hasNote ? "Intook @ " : "Shot @ ");
                        // System.out.print(Integer.parseInt(nextLine[columnIndexes.get("/DriverStation/MatchTime")]));
                        // System.out.print(hasNote ? "\t" : "\n");
                        if (hasNote) {
                            output.add(nextLine[columnIndexes.get("/DriverStation/MatchTime")]);
                            output.add(nextLine[columnIndexes.get("/RealOutputs/Drivetrain/Odometry/translation/x")]);
                            output.add(nextLine[columnIndexes.get("/RealOutputs/Drivetrain/Odometry/translation/y")]);
                            output.add(nextLine[columnIndexes.get("/RealOutputs/Drivetrain/Odometry/rotation/value")]);
                        } else {
                            output.add(nextLine[columnIndexes.get("/DriverStation/MatchTime")]);
                            output.add(nextLine[columnIndexes.get("/RealOutputs/Drivetrain/Odometry/translation/x")]);
                            output.add(nextLine[columnIndexes.get("/RealOutputs/Drivetrain/Odometry/translation/y")]);
                            output.add(nextLine[columnIndexes.get("/RealOutputs/Drivetrain/Odometry/rotation/value")]);

                            output.add(nextLine[columnIndexes.get("/DriverStation/Autonomous")]);
                            output.add(Double.parseDouble(nextLine[
                                columnIndexes.get("/RealOutputs/Amp Bar/Amp Bar Position")]) > 5 ? "true" : "false");
                            // System.err.println(Double.parseDouble(nextLine[
                            //     columnIndexes.get("/RealOutputs/Amp Bar/Amp Bar Position")]) > 5);

                            w.writeNext(Arrays.copyOf(output.toArray(), output.size(), String[].class));
                            output = new ArrayList<>();
                        }
                    }
                }
                // System.err.println(Integer.parseInt(nextLine[timestampIdx]));
            }
            
            r.close();
            w.close();
            
            System.out.println("Done! Check Output.csv");
        } catch (Exception e) {
            e.printStackTrace();
        }        
    }
}