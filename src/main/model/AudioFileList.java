package model;

import audio.AudioDataStructure;
import ui.Main;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.nio.file.Files.delete;
import static model.ListFileHandler.*;

// Instantiable class to handle the file list
public class AudioFileList {
    private List<AudioDataStructure> fileList;
    private long dbIndex;
    private String userDir;

    // Modifies: this
    // Effects:  fileList is empty, loads database from (user home directory)/audiodex
    public AudioFileList() {
        fileList = new ArrayList<AudioDataStructure>();
        userDir = System.getProperty("user.home") + "/audiodex/";
        File userDirFile = new File(userDir);
        if (!userDirFile.exists()) {
            userDirFile.mkdirs();
        }
    }

    // Modifies: this
    // Effects:  removes all null values from database
    public void sanitizeDatabase() {
        Main.CliInterface.println("Sanitizing Database...");
        for (int i = 0; i < fileList.size(); i++) {
            if (fileList.get(i) == null || fileList.get(i).isEmpty()) {
                fileList.remove(i);
                i--;
            }
        }
    }

    // Modifies: this
    // Effects:  adds specified file to database
    public void addFileToDatabase(String filename) {
        Main.CliInterface.println("Adding file " + filename + "...");
        AudioDataStructure data = new AudioDataStructure(filename);
        if (data.isEmpty()) {
            Main.CliInterface.println("Unknown file type, ignored file.");
            return;
        }
        fileList.add(data);
    }

    // Modifies: this
    // Effects:  adds all files in specified directory to database
    public void addDirToDatabase(String dirname) {
        Main.CliInterface.println("Adding directory " + dirname + "...");
        File dir = new File(dirname);
        if (dir.isDirectory()) {
            File[] fileList = dir.listFiles();
            if (fileList == null || fileList.length == 0) {
                return;
            }
            for (File file : fileList) { // Database uses absolute file paths, otherwise it would fail to load audio
                addFileToDatabase(file.getAbsolutePath());
            }
        }
    }

    // Modifies: this
    // Effects:  loads database index from (userDir)/audiodex.dbindex and reloads database
    public void loadDatabase() {
        String filename = userDir + "audiodex.dbindex";
        try {
            dbIndex = Long.parseLong(new String(Files.readAllBytes(Paths.get(filename)),
                    StandardCharsets.UTF_8), 36);
        } catch (Exception e) {
            dbIndex = 0;
            Main.CliInterface.println("New database.");
            return;
        }
        Main.CliInterface.println("Successfully loaded database index: " + Long.toString(dbIndex));
        loadDatabaseFile();
    }

    // Modifies: this
    // Effects:  replaces file list with described data file
    public void loadDatabaseFile() {
        String filename = userDir + Long.toString(dbIndex, 36) + ".audiodex.basedb";
        Main.CliInterface.println("Loading database from " + filename + "...");
        fileList.clear();
        AudioDataStructure[] data = decodeList(filename);
        Collections.addAll(fileList, data);
        sanitizeDatabase();
    }

    // Modifies: this
    // Effects:  replaces file list with described data file
    //           returns true on success, false on failure
    public boolean saveDatabaseFile() {
        if (fileList.isEmpty()) {
            Main.CliInterface.println("Nothing to save; database is empty.");
            return true;
        }
        dbIndex++;
        String filename = userDir + Long.toString(dbIndex, 36) + ".audiodex.basedb";
        List<AudioDataStructure> copyList = new ArrayList<AudioDataStructure>();
        copyList.addAll(fileList);
        Main.CliInterface.println("Saving database to " + filename + "...");
        try {
            encodeList(copyList, filename);
            return saveDatabaseIndex();
        } catch (IOException e) {
            return false;
        }
    }

    // Modifies: database files, specifically audiodex.dbindex
    // Effects:  saves current database pointer
    private boolean saveDatabaseIndex() {
        return FileManager.writeToFile(userDir + "audiodex.dbindex", Long.toString(dbIndex, 36));
    }

    // Modifies: database files
    // Effects:  reverts to previous database, if avaliable
    public void revertDb() {
        dbIndex--;
        String filename = userDir + Long.toString(dbIndex, 36) + ".audiodex.basedb";
        if ((new File(filename)).exists()) {
            loadDatabaseFile();
            saveDatabaseIndex();
            Main.CliInterface.println("Successfully reverted database!");
        } else {
            Main.CliInterface.println("Could not find previous version of database...");
            dbIndex++;
        }
    }

    // Modifies: database files
    // Effects:  cleans (deletes all files for) database with specified filename
    public void cleanDb(String filename) {
        if ((new File(filename)).exists()) {
            Main.CliInterface.println("Cleaning database at " + filename + "...");
            try {
                String f = new String(Files.readAllBytes(Paths.get(filename)));
                String[] fs = f.split(ListFileHandler.RESERVED_CHARACTERS[0]);
                if (fs.length >= 2) {
                    cleanDb(fs[1]);
                }
                try {
                    delete(Paths.get(filename));
                } catch (Exception e) {
                    // lol
                }
                Main.CliInterface.println("Cleaned database at " + filename + "!");
            } catch (IOException e) {
                // RIP
            }
        } else {
            Main.CliInterface.println("No database to clean!");
        }
    }

    // Modifies: database files
    // Effects:  cleans (deletes all files for) database for index
    public void cleanDb(long index) {
        cleanDb(userDir + Long.toString(index, 36) + ".audiodex.basedb");
    }

    // Modifies: database files
    // Effects:  cleans (deletes all files for) database for index
    public void cleanOldDb() {
        for (long i = 0; i < dbIndex; i++) {
            cleanDb(i);
        }
    }

    // Modifies: database files
    // Effects:  clean (delete all files for) all databases, reset the database pointer
    //           and saves the database again
    public void cleanDbFldr() {
        dbIndex = 0;
        File[] fileList = (new File(userDir)).listFiles();
        for (File f : fileList) {
            try {
                String filename = f.getAbsolutePath();
                delete(f.toPath());
                Main.CliInterface.println("Deleted " + filename + ".");
            } catch (IOException e) {
                // LMAO
            }
        }
        saveDatabaseFile();
        saveDatabaseIndex();
    }
}
