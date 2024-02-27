package model;

import audio.AudioDataStructure;
import audio.ID3Container;
import org.json.*;
import ui.Main;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.Files.delete;
import static model.FileManager.readFile;

// Instantiable class to handle the file list
public class DataManager {
    private final List<AudioDataStructure> songFilelist;
    private ApplicationSettings settings;
    private long dbIndex;
    private String userDir;

    // Modifies: this
    // Effects:  fileList is empty, loads database from (user home directory)/audiodex
    public DataManager() {
        songFilelist = new ArrayList<>();
        settings = new ApplicationSettings();
        userDir = System.getProperty("user.home") + "/audiodex/";
    }

    // Effects: gets list size
    public int audioListSize() {
        return songFilelist.size();
    }

    // Effects: gets element from list
    public AudioDataStructure getAudioFile(int i) {
        if (i < 0 || i >= songFilelist.size()) {
            return null;
        }
        return songFilelist.get(i);
    }

    public enum SortingTypes {
        Title,
        Artist,
        Album,
        AlbumArtist,
        Album_Title,
        Filesize
    }

    // Modifies: this
    // Effects:  sorts music list
    public void sortSongList(String type) {
        SortingTypes sortBy;
        try {
            sortBy = SortingTypes.valueOf(type);
        } catch (Exception e) {
            return;
        }
        Main.CliInterface.println("Sorting database by " + type + "...");
        bubble(sortBy, 0, songFilelist.size());
    }

    // Modifies: this
    // Effects:  sorts music list (uses the bubble sort algorithm)
    private void bubble(SortingTypes sortBy, int start, int end) {
        for (int i = end - 1; i > 0; i--) {
            for (int j = start; j < i; j++) {
                if (outOfOrder(sortBy, songFilelist.get(j), songFilelist.get(j + 1))) {
                    swap(j, j + 1);
                }
            }
        }
    }

    // Modifies: this
    // Effects:  swaps two elements in file list
    private void swap(int first, int second) {
        AudioDataStructure firstValue = songFilelist.get(first);
        songFilelist.set(first, songFilelist.get(second));
        songFilelist.set(second, firstValue);
    }

    // Effects: returns true if audio files are out of order
    private boolean outOfOrder(SortingTypes sortBy, AudioDataStructure a, AudioDataStructure b) {
        switch (sortBy) {
            case Album_Title: {
                int albumSort = getSortingValue("Album", a).compareTo(getSortingValue("Album", b));
                if (albumSort == 0) {
                    return getSortingValue("Title", a)
                            .compareTo(getSortingValue("Title", b)) > 0;
                }
                return albumSort > 0;
            }
            case AlbumArtist:
            case Artist:
            case Title:
            case Album: {
                return getSortingValue(sortBy.toString(), a)
                        .compareTo(getSortingValue(sortBy.toString(), b)) > 0;
            }
            case Filesize: {
                return a.getFilesize() > b.getFilesize();
            }
        }
        return false;
    }

    // Effects: gets album value for sorting
    private String getSortingValue(String type, AudioDataStructure file) {
        ID3Container id3 = file.getId3Data();
        Object s = id3.getID3Data(type + "-Sort");
        if (s == null || s.toString().equals("null") || s.toString().isEmpty()) {
            s = id3.getID3Data(type);
            if (s == null || s.toString().equals("null") || s.toString().isEmpty()) {
                s = "";
            }
        }
        return s.toString().toLowerCase();
    }

    // Modifies: this
    // Effects:  removes all null values from database
    public void sanitizeAudioDatabase() {
        Main.CliInterface.println("Sanitizing Database...");
        for (int i = 0; i < songFilelist.size(); i++) {
            if (songFilelist.get(i) == null || songFilelist.get(i).isEmpty()
                    || songDbContainsDuplicate(songFilelist.get(i).getFilename())) {
                songFilelist.remove(i);
                i--;
            }
        }
    }

    // Effects: returns true if database contains file, false otherwise
    private boolean songDbContainsFile(String filename) {
        for (AudioDataStructure data : songFilelist) {
            if (data.getFilename().equals(filename)) {
                return true;
            }
        }
        return false;
    }

    // Effects: returns true if database contains two of this file, false otherwise
    private boolean songDbContainsDuplicate(String filename) {
        int i = 0;
        for (AudioDataStructure data : songFilelist) {
            if (data.getFilename().equals(filename)) {
                i++;
            }
        }
        return i >= 2;
    }

    // Modifies: this
    // Effects:  adds specified file to database
    public void addFileToSongDatabase(String filename) {
        try {
            if (songDbContainsFile((new File(filename)).getCanonicalPath())) {
                Main.CliInterface.println("File already in database, skipping.");
                return;
            }
        } catch (IOException e) {
            Main.CliInterface.println("Error while trying to get absolute path of file.");
            return;
        }
        Main.CliInterface.println("Adding file " + filename + "...");
        AudioDataStructure data = new AudioDataStructure(filename);
        if (data.isEmpty()) {
            Main.CliInterface.println("Unknown file type, ignored file.");
            return;
        }
        songFilelist.add(data);
    }

    // Modifies: this
    // Effects:  adds all files in specified directory to database
    public void addDirToSongDatabase(String dirname) {
        Main.CliInterface.println("Adding directory " + dirname + "...");
        File dir = new File(dirname);
        if (dir.isDirectory()) {
            File[] fileList = dir.listFiles();
            if (fileList == null || fileList.length == 0) {
                return;
            }
            for (File file : fileList) { // Database uses absolute file paths, otherwise it would fail to load audio
                if (file.isFile()) {
                    addFileToSongDatabase(file.getAbsolutePath());
                } else if (file.isDirectory()) {
                    addDirToSongDatabase(file.getAbsolutePath());
                }
            }
        }
    }

    // Modifies: this
    // Effects:  loads database index from (userDir)/audiodex.dbindex and reloads database
    public void loadDatabase() {
        String filename = userDir + "index.audiodex.db";
        try {
            dbIndex = Long.parseLong(Files.readString(Paths.get(filename)), 36);
        } catch (Exception e) {
            dbIndex = 0;
            Main.CliInterface.println("New database.");
            return;
        }
        Main.CliInterface.println("Successfully loaded database index: " + dbIndex);
        loadDatabaseFile();
    }

    // Requires: file exists
    // Modifies: this
    // Effects:  replaces file list with described data file
    public void loadDatabaseFile() {
        String filename = userDir + Long.toString(dbIndex, 36) + ".audiodex.json";
        JSONArray array;
        System.out.println(readFile(filename));
        try {
            JSONObject decoded = new JSONObject(readFile(filename));
            settings = new ApplicationSettings((JSONObject) decoded.get("settings"));
            array = (JSONArray) decoded.get("files");
        } catch (JSONException e) {
            array = new JSONArray(readFile(filename));
            Main.CliInterface.println("Legacy-style database.");
        }
        for (Object object : array) {
            songFilelist.add(AudioDataStructure.decode((JSONObject) object));
        }
    }

    // Effects: returns the ApplicationSettings struct
    public ApplicationSettings getSettings() {
        return settings;
    }

    // Modifies: this
    // Effects:  replaces file list with described data file
    //           returns true on success, false on failure
    public boolean saveDatabaseFile() {
        File userDirFile = new File(userDir);
        if (!userDirFile.exists()) {
            userDirFile.mkdirs();
        }
        dbIndex++;
        String filename = userDir + Long.toString(dbIndex, 36) + ".audiodex.json";
        String toSave = getDatasave().toString();
        FileManager.writeToFile(filename, toSave);
        return saveDatabaseIndex();
    }

    // Effects: returns a JSONObject to be saved
    private JSONObject getDatasave() {
        JSONObject object = new JSONObject();
        object.put("settings", settings.encode());
        JSONArray array = new JSONArray();
        for (AudioDataStructure structure : songFilelist) {
            array.put(structure.encode());
        }
        object.put("files", array);
        return object;
    }

    // Modifies: database files, specifically audiodex.dbindex
    // Effects:  saves current database pointer
    private boolean saveDatabaseIndex() {
        return FileManager.writeToFile(userDir + "index.audiodex.db", Long.toString(dbIndex, 36));
    }

    // Modifies: database files
    // Effects:  reverts to previous database, if avaliable
    public void revertDb() {
        dbIndex--;
        String filename = userDir + Long.toString(dbIndex, 36) + ".audiodex.json";
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
                delete(Paths.get(filename));
            } catch (Exception e) {
                // lol
            }
        } else {
            Main.CliInterface.println("No database to clean!");
        }
    }

    // Modifies: database files
    // Effects:  cleans (deletes all files for) database for index
    public void cleanDb(long index) {
        cleanDb(userDir + Long.toString(index, 36) + ".audiodex.json");
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
        if (fileList == null) {
            return;
        }
        for (File f : fileList) {
            if (f.isDirectory()) {
                continue;
            }
            try {
                String filename = f.getAbsolutePath();
                delete(f.toPath());
                Main.CliInterface.println("Deleted " + filename + ".");
            } catch (IOException e) {
                // LMAO
            }
        }
        saveDatabaseFile();
    }

    // Modifies: this
    // Effects:  sets user directory
    public void setUserDir(String nuDir) {
        userDir = nuDir;
    }

    // Effects: returns a list of indexes to files that no longer exist
    public List<Integer> getRemovedAudioFiles() {
        List<Integer> removed = new ArrayList<>();
        File f;
        for (int i = 0; i < songFilelist.size(); i++) {
            f = new File(songFilelist.get(i).getFilename());
            if (!f.exists()) {
                removed.add(i);
            }
        }
        return removed;
    }

    // Modifies: this
    // Effects:  removes file index from database
    public void removeSongIndex(int i) {
        songFilelist.remove(i);
    }

    // Modifies: this
    // Effects:  removes unlocatable files
    public void removeEmptyAudioFiles() {
        File f; // Remove back-to-front to save decrementing the pointer
        for (int i = songFilelist.size() - 1; i >= 0; i--) {
            f = new File(songFilelist.get(i).getFilename());
            if (!f.exists()) {
                songFilelist.remove(i);
            }
        }
    }

    // Modifies: this
    // Effects:  updates file pointer for index
    public void updateAudioFile(int i, String newFileName) {
        try {
            if (songDbContainsFile((new File(newFileName)).getCanonicalPath())) {
                Main.CliInterface.println("File already in database, skipping.");
                return;
            }
        } catch (IOException e) {
            Main.CliInterface.println("Error while trying to get absolute path of file.");
            return;
        }
        Main.CliInterface.println("Swapping in file " + newFileName + "...");
        AudioDataStructure data = new AudioDataStructure(newFileName);
        if (data.isEmpty()) {
            Main.CliInterface.println("Unknown file type, cannot use file.");
            return;
        }
        songFilelist.set(i, data);
    }

    // Modifies: this
    // Effects:  updates file pointer for index
    public void updateAudioFile(int i, AudioDataStructure data) {
        songFilelist.set(i, data);
    }
}
