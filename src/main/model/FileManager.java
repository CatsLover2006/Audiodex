package model;

import java.io.FileWriter;
import java.io.IOException;

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
}
