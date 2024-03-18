package model;

import oshi.SystemInfo;
import oshi.software.os.OSFileStore;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Static class
public class FileManager {
    // Modifies: file at filename
    // Effects:  writes string to file, overwriting previous contents
    public static boolean writeToFile(String filename, String data) {
        try {
            FileWriter writer = new FileWriter(filename, StandardCharsets.UTF_8, false);
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
            FileReader reader = new FileReader(filename, StandardCharsets.UTF_8);
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

    private static SystemInfo sysInfo = new SystemInfo();
    private static List<OSFileStore> fileStores;

    // Effects: checks if directory/file is a mount point
    public static boolean isRoot(File file) {
        if (file.getParentFile() == null) {
            return true;
        }
        fileStores = sysInfo.getOperatingSystem().getFileSystem().getFileStores();
        return fileStores.stream().anyMatch(store -> file.getAbsolutePath().equals(store.getMount()));
    }
}
