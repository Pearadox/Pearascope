import java.io.*;
import java.util.*;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;  

public class PearaDiSe {
    public static void main(String[] args) {
        String fileName = "../dslogs/csvs/2024/2024_04_04 16_25_46 Thu.csv"; // <-- Change to use
        long time = System.currentTimeMillis();
        try {
            CSVReader r = new CSVReader(new FileReader(fileName));
            String[] nextLine = r.readNext();

            // creates a hashmap of indexes of the logs' columns (which column has what)
            HashMap<String, Integer> columnIndexes = new HashMap<>();
            for (int i = 0; i < nextLine.length; i++) { columnIndexes.put(nextLine[i], i); }

            final double kConv = 0.00000556 * 3.6; // pls explain what these constants mean
            int brownOutCounter = 0, lineCounter = 0;
            double totalPDP_sum = 0, kJ_first_brownout = 0;            
            double[] PDP_sums = new double[24];
            
            while ((nextLine = r.readNext()) != null) { 
                lineCounter++;

                if (nextLine[columnIndexes.get("Brownout")].equals("True")) {
                    brownOutCounter++;
                }
                
                double voltage = Double.parseDouble(nextLine[columnIndexes.get("Voltage")]);                
                if (voltage < 30) {
                    totalPDP_sum += Double.parseDouble(nextLine[columnIndexes.get("Total PDP")]) 
                    * voltage
                    * kConv;
                    
                    for (int i = 0; i <= 23; i++) {
                        PDP_sums[i] += Double.parseDouble(nextLine[
                            columnIndexes.get("PDP " + String.valueOf(i))]) 
                            * voltage
                            * kConv;
                    }
                }

                if (brownOutCounter == 0) { 
                    // updates until brownout counter > 0
                    kJ_first_brownout = totalPDP_sum;
                }
            }

            // first line of output, headers of columns
            String[] headers = new String[27];
            headers[0] = "Total kJ";
            headers[1] = "# of Brownouts";
            headers[2] = "kJ @ 1st BO";

            for (int i = 0; i <= 23; i++) { 
                headers[i + 3] = "PDP " + String.valueOf(i); 
            }
            
            ArrayList<String> output = new ArrayList<>();
            
            output.add(String.valueOf(totalPDP_sum));
            output.add(String.valueOf(brownOutCounter));
            output.add(String.valueOf(kJ_first_brownout));
            
            for (int i = 0; i <= 23; i++) {
                System.out.println("PDP " + i + ": " + PDP_sums[i] + " kJ");
                output.add(String.valueOf(PDP_sums[i]));
            }

            CSVWriter w = new CSVWriter(new FileWriter("output.csv"));
            w.writeNext(headers); // writes headers to first row of output
            w.writeNext(Arrays.copyOf(output.toArray(), output.size(), String[].class));
            
            System.out.println("\nTotal PDP " + totalPDP_sum + " kJ");
            System.out.println("kJ @ 1st BO " + kJ_first_brownout + " kJ");
            System.out.println("Found " + brownOutCounter + " brownouts");
            
            System.out.println("Checked " + lineCounter + " lines");
            System.out.println("Took " + ((System.currentTimeMillis() - time) / 1000.0) + "s");
            
            r.close();
            w.close();
        } catch (Exception e) {
            e.printStackTrace();
        }        
    }

    public static String trim(String s) {
        if (s.lastIndexOf("q") != -1) {
            return s.substring(s.lastIndexOf("q"));
        } else if (s.lastIndexOf("e") != -1) {
            return s.substring(s.lastIndexOf("e"));
        } else if (s.lastIndexOf("/") != -1) {
            return s.substring(s.lastIndexOf("/") + 1);
        } else {
            return s;
        }
    }
}