package model;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

// Static class
public class FileManager {
    // Modifies: file at filename
    // Effects:  writes string to file, overwriting previous contents
    public static boolean writeToFile(String filename, String data) {
        try {
            FileWriter writer = new FileWriter(filename, false);
            writer.write(data);
            writer.close();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    // Effects: reads string from file
    public static String readFile(String filename) {
        try {
            FileReader reader = new FileReader(filename);
            char[] fileContents = new char[Math.toIntExact(new File(filename).length())];
            int len = reader.read(fileContents);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < len; i++) {
                out.append(fileContents[i]);
            }
            out.trimToSize();
            return out.toString();
        } catch (IOException e) {
            return "";
        }
    }
}
