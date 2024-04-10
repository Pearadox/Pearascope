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
            
            // first line of output, headers of columns
            String[] headers = { "# of Brownouts" };
            
            // CSVWriter w = new CSVWriter(new FileWriter("../dcmp_output/" + trim(fileName)));
            // w.writeNext(headers); // writes headers to first row of output
            
            ArrayList<String> output = new ArrayList<>();

            boolean brownedOut = false; 
            int brownOutCounter = 0;
            int lineCounter = 0;
            double totalPDP_sum = 0;

            while ((nextLine = r.readNext()) != null) { 
                lineCounter++;
                if (nextLine[columnIndexes.get("Brownout")].equals("True")) {
                    brownOutCounter++;
                }
                totalPDP_sum += Double.parseDouble(nextLine[columnIndexes.get("Total PDP")]) * Double.parseDouble(nextLine[columnIndexes.get("Voltage")]);
            }
            totalPDP_sum *= 0.00000556 * 3.6;

            System.out.println("Found " + brownOutCounter + " brownouts");
            System.out.println("Total PDP " + totalPDP_sum + " kJ");
            System.out.println("Checked " + lineCounter + " lines");
            
            r.close();
            // w.close();
            
            System.out.println("Took " + ((System.currentTimeMillis() - time) / 1000.0) + "s");
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