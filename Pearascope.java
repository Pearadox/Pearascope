import java.io.*;
import java.util.*;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;  

public class Pearascope {
    public static void main(String[] args) {
        String fileName = "Log_24-04-04_19-04-17_q27_re.csv"; // <-- Change to use
        long time = System.currentTimeMillis();
        try {
            CSVReader r = new CSVReader(new FileReader("../" + fileName));
            String[] nextLine = r.readNext();

            // creates a hashmap of indexes of the logs' columns (which column has what)
            HashMap<String, Integer> columnIndexes = new HashMap<>();
            for (int i = 0; i < nextLine.length; i++) { columnIndexes.put(nextLine[i], i); }
            
            // first line of output, headers of columns
            String[] headers = { "Intook Time", "Intook Pose X", "Intook Pose Y", "Intook Pose θ", 
                    "Shot Time", "Shot X", "Shot Y", "Shot θ", 
                    "Autonomous/Teleop", "Speaker/Amp",
                    "Pivot Intended Position", "Pivot Actual Position",
                    "Left Shooter RPM", "Right Shooter RPM",
                    "Match Number", "Alliance", 
                    "Battery Voltage",
                    // "Limelight Ambiguity", 
            };
            
            CSVWriter w = new CSVWriter(new FileWriter(fileName.substring(fileName.lastIndexOf("q"))));
            w.writeNext(headers); // writes headers to first row of output
            
            ArrayList<String> output = new ArrayList<>();
            for (int i = 0; i < 4; i++) { output.add("PRELOAD"); }

            boolean hasNote = true; // starts with preload

            while ((nextLine = r.readNext()) != null) { // reads next line until it runs out of rows in logs
                // if in match and whether the robot has a note changes
                if (Integer.parseInt(nextLine[columnIndexes.get("/DriverStation/MatchTime")]) > 0 
                    && nextLine[columnIndexes.get("/DriverStation/Enabled")].equals("true")) {
                    if (nextLine[columnIndexes.get("/RealOutputs/Transport/Ir Sensor")].equals("true") != hasNote) {
                        hasNote = !hasNote; // updates local variable

                        // logs time and pose when robot intakes or shoots
                        output.add(nextLine[columnIndexes.get("/DriverStation/MatchTime")]);
                        output.add(nextLine[columnIndexes.get("/RealOutputs/Drivetrain/Odometry/translation/x")]);
                        output.add(nextLine[columnIndexes.get("/RealOutputs/Drivetrain/Odometry/translation/y")]);
                        output.add(nextLine[columnIndexes.get("/RealOutputs/Drivetrain/Odometry/rotation/value")]);
                        
                        if (!hasNote) { // logs these when the robot shoots and no longer has a note
                            output.add(nextLine[columnIndexes.get("/DriverStation/Autonomous")]
                            .equals("true") ? "Autonomous" : "Teleop");
                            output.add(Double.parseDouble(nextLine[columnIndexes.get
                                ("/RealOutputs/Amp Bar/Amp Bar Position")]) > -5 ? "Amp" : "Speaker"); 
                            // beta goes from -20 to 0
                            output.add(nextLine[columnIndexes.get("/RealOutputs/Shooter/Shooter Pivot Intended Position")]);
                            output.add(nextLine[columnIndexes.get("/RealOutputs/Shooter/Shooter Pivot Position")]);
                            output.add(nextLine[columnIndexes.get("/RealOutputs/Shooter/Shooter Left Speed")]);
                            output.add(nextLine[columnIndexes.get("/RealOutputs/Shooter/Shooter Right Speed")]);
                            output.add(nextLine[columnIndexes.get("/DriverStation/MatchNumber")]);
                            output.add(Integer.parseInt(nextLine[columnIndexes.get
                                ("/DriverStation/AllianceStation")]) <= 3 ? "Red" : "Blue");
                            output.add(nextLine[columnIndexes.get("/SystemStats/BatteryVoltage")]);                            
                            // output.add(nextLine[columnIndexes.get("/RealOutputs/Limelight/Single Tag Ambiguity")]);

                            // converts output to String[] and writes it to output
                            w.writeNext(Arrays.copyOf(output.toArray(), output.size(), String[].class));
                            output = new ArrayList<>(); // clears variable for new line
                        }
                    }
                }
            }
            
            r.close();
            w.close();
            
            System.out.println("Done! Check " + fileName.substring(fileName.lastIndexOf("q")));
            System.out.println("Took " + ((System.currentTimeMillis() - time) / 1000.0) + "s");
        } catch (Exception e) {
            e.printStackTrace();
        }        
    }
}