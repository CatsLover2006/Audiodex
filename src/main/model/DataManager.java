package model;

import audio.AudioDataStructure;
import audio.ID3Container;
import org.json.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.Files.delete;
import static model.FileManager.readFile;
import static java.io.File.separatorChar;

// Instantiable class to handle the file list
public class DataManager {
    private final List<AudioDataStructure> songFilelist;
    private ApplicationSettings settings;
    private long dbIndex;
    private String userDir;
    private boolean modified = false;
    private static EventLog logger = EventLog.getInstance();

    // Modifies: this
    // Effects:  fileList is empty, loads database from (user home directory)/audiodex
    public DataManager() {
        songFilelist = new ArrayList<>();
        settings = new ApplicationSettings();
        userDir = System.getProperty("user.home") + separatorChar + "audiodex" + separatorChar;
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
        Default,
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
        logger.logEvent(new Event("Sorting database by " + type + "..."));
        bubble(sortBy, songFilelist.size());
        logger.logEvent(new Event("Sorted database by " + type + "."));
    }

    // Modifies: this
    // Effects:  sorts music list (uses the bubble sort algorithm)
    private void bubble(SortingTypes sortBy, int end) {
        for (int i = end - 1; i > 0; i--) {
            for (int j = 0; j < i; j++) {
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
    
    // Effects: parses long without exception
    private static long parseLong(String str) {
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Effects: returns true if audio files are out of order
    //          I can't believe this is 25 lines
    private static boolean outOfOrder(SortingTypes sortBy, AudioDataStructure a, AudioDataStructure b) {
        switch (sortBy) {
            case Default:
            case Album:
                int albumSort = getSortingValue("Album", a).compareTo(getSortingValue("Album", b));
                if (albumSort == 0) {
                    long discDif = parseLong(getSortingValue("Disc", a)) - parseLong(getSortingValue("Disc", b));
                    return discDif == 0 ? parseLong(getSortingValue("Track", a))
                            > parseLong(getSortingValue("Track", b)) : discDif > 0;
                }
                return albumSort > 0;
            case AlbumArtist:
            case Artist:
            case Title:
                int dif = getSortingValue(sortBy.toString(), a).compareTo(getSortingValue(sortBy.toString(), b));
                return dif == 0 ? outOfOrder(SortingTypes.Default, a, b) : dif > 0;
            case Filesize:
                return a.getFilesize() > b.getFilesize();
        }
        return false;
    }

    // Effects: gets album value for sorting
    private static String getSortingValue(String type, AudioDataStructure file) {
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
        logger.logEvent(new Event("Sanitizing Database..."));
        for (int i = 0; i < songFilelist.size(); i++) {
            if (songFilelist.get(i) == null || songFilelist.get(i).isEmpty()
                    || songDbContainsDuplicate(songFilelist.get(i).getFilename())) {
                songFilelist.remove(i);
                i--;
            }
        }
        logger.logEvent(new Event("Sanitized Database!"));
    }

    // Effects: returns true if database contains file, false otherwise
    private boolean songDbContainsFile(String filename) {
        return songFilelist.stream().anyMatch(data -> data.getFilename().equals(filename));
    }

    // Effects: returns true if database contains two of this file, false otherwise
    private boolean songDbContainsDuplicate(String filename) {
        int i = (int) songFilelist.stream().filter(data -> data.getFilename().equals(filename)).count();
        return i >= 2;
    }

    // Modifies: this
    // Effects:  adds specified file to database
    public void addFileToSongDatabase(String filename) {
        if (new File(filename).getName().startsWith("._") && new File(new File(filename).getParent()
                + separatorChar + new File(filename).getName().substring(2)).exists()) {
            logger.logEvent(new Event(String.format("File %s is a macOS resource fork, skipping.", filename)));
            return;
        }
        logger.logEvent(new Event("Adding file " + filename + "..."));
        try {
            if (songDbContainsFile(new File(filename).getCanonicalPath())) {
                logger.logEvent(new Event("File already in database, skipping."));
                return;
            }
        } catch (IOException e) {
            logger.logEvent(new Event("Error while trying to get absolute path of file."));
            return;
        }
        AudioDataStructure data = new AudioDataStructure(filename);
        if (data.isEmpty()) {
            logger.logEvent(new Event("Unknown file type, ignored file."));
            return;
        }
        modified = true;
        songFilelist.add(data);
        logger.logEvent(new Event("Added file " + filename + "!"));
    }

    // Modifies: this
    // Effects:  adds all files in specified directory to database
    public void addDirToSongDatabase(String dirname) {
        logger.logEvent(new Event("Adding directory " + dirname + "..."));
        File dir = new File(dirname);
        if (dir.isDirectory()) {
            File[] fileList = dir.listFiles();
            if (fileList == null || fileList.length == 0) {
                return;
            }
            for (File file : fileList) { // Database uses absolute file paths, otherwise it would fail to load audio
                if (file.isFile()) {
                    ExceptionIgnore.ignoreExc(() -> addFileToSongDatabase(file.getAbsolutePath()));
                } else if (file.isDirectory()) {
                    addDirToSongDatabase(file.getAbsolutePath());
                }
            }
        }
        logger.logEvent(new Event("Added directory " + dirname + "!"));
    }

    // Modifies: this
    // Effects:  loads database index from (userDir)/audiodex.dbindex and reloads database
    public void loadDatabase() {
        String filename = userDir + "index.audiodex.db";
        logger.logEvent(new Event("Loading database index from " + filename + "..."));
        songFilelist.clear();
        try {
            dbIndex = Long.parseLong(readFile(filename), 36);
        } catch (Exception e) {
            dbIndex = 0;
            logger.logEvent(new Event("New database."));
            return;
        }
        logger.logEvent(new Event("Successfully loaded database index: " + dbIndex));
        loadDatabaseFile();
    }

    // Effects: returns if database has been modified since last save
    public boolean beenModified() {
        return modified;
    }

    // Requires: file exists
    // Modifies: this
    // Effects:  replaces file list with described data file
    public void loadDatabaseFile() {
        String filename = userDir + Long.toString(dbIndex, 36) + ".audiodex.json";
        logger.logEvent(new Event("Attempting to load database..."));
        JSONArray array;
        String file = readFile(filename);
        try {
            JSONObject decoded = new JSONObject(file);
            settings = new ApplicationSettings((JSONObject) decoded.get("settings"));
            array = (JSONArray) decoded.get("files");
        } catch (JSONException e) {
            try {
                array = new JSONArray(file);
                logger.logEvent(new Event("Legacy-style database."));
            } catch (JSONException e2) {
                logger.logEvent(new Event("Error while decoding database."));
                return;
            }
        }
        for (Object object : array) {
            songFilelist.add(AudioDataStructure.decode((JSONObject) object));
        }
        logger.logEvent(new Event("Loaded database!"));
        modified = false;
    }

    // Effects: returns the ApplicationSettings struct
    public ApplicationSettings getSettings() {
        return settings;
    }

    // Modifies: this
    // Effects:  replaces file list with described data file
    //           returns true on success, false on failure
    public boolean saveDatabaseFile() {
        logger.logEvent(new Event("Saving database file..."));
        if (!modified) {
            logger.logEvent(new Event("Database file already up to date! No need to save."));
            return true;
        }
        File userDirFile = new File(userDir);
        if (!userDirFile.exists()) {
            if (!userDirFile.mkdirs()) {
                throw new RuntimeException(new IOException("Failed to create user directory"));
            }
        }
        dbIndex++;
        String filename = userDir + Long.toString(dbIndex, 36) + ".audiodex.json";
        String toSave = getDatasave().toString();
        FileManager.writeToFile(filename, toSave);
        logger.logEvent(new Event("Saved database file!"));
        if (saveDatabaseIndex()) {
            modified = false;
            return true;
        }
        return false;
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
        logger.logEvent(new Event("Updating database index..."));
        if (FileManager.writeToFile(userDir + "index.audiodex.db", Long.toString(dbIndex, 36))) {
            logger.logEvent(new Event("Updated database index!"));
            return true;
        }
        logger.logEvent(new Event("Error updating database index."));
        return false;
    }

    // Modifies: database files
    // Effects:  reverts to previous database, if avaliable
    public void revertDb() {
        if (modified) {
            logger.logEvent(new Event("Reverting unsaved changes..."));
            loadDatabase();
            modified = false;
            return;
        }
        logger.logEvent(new Event("Reverting database..."));
        dbIndex--;
        String filename = userDir + Long.toString(dbIndex, 36) + ".audiodex.json";
        if (new File(filename).exists()) {
            songFilelist.clear();
            loadDatabaseFile();
            saveDatabaseIndex();
            logger.logEvent(new Event("Successfully reverted database!"));
        } else {
            logger.logEvent(new Event("Could not find previous version of database..."));
            dbIndex++;
        }
    }

    // Modifies: database files
    // Effects:  cleans (deletes all files for) database with specified filename
    public static void cleanDb(String filename) {
        if (new File(filename).exists()) {
            logger.logEvent(new Event("Cleaning database at " + filename + "..."));
            ExceptionIgnore.ignoreExc(() -> delete(Paths.get(filename)));
            logger.logEvent(new Event("Cleaned database at " + filename + "!"));
        } else {
            logger.logEvent(new Event("No database to clean!"));
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
        logger.logEvent(new Event("Cleaning unused databases..."));
        for (long i = 0; i < dbIndex; i++) {
            cleanDb(i);
        }
        logger.logEvent(new Event("Cleaned unused databases!"));
    }

    // Modifies: database files
    // Effects:  clean (delete all files for) all databases, reset the database pointer
    //           and saves the database again
    public void cleanDbFldr() {
        logger.logEvent(new Event("Cleaning database folder..."));
        dbIndex = 0;
        File[] fileList = new File(userDir).listFiles();
        if (fileList == null) {
            logger.logEvent(new Event("Folder empty."));
            return;
        }
        for (File f : fileList) {
            if (f.isDirectory()) {
                continue;
            }
            ExceptionIgnore.ignoreExc(() -> {
                String filename = f.getAbsolutePath();
                delete(f.toPath());
                logger.logEvent(new Event("Deleted " + filename + "."));
            });
        }
        saveDatabaseFile();
        logger.logEvent(new Event("Cleaning database folder..."));
    }

    // Modifies: this
    // Effects:  sets user directory
    public void setUserDir(String nuDir) {
        userDir = nuDir;
        logger.logEvent(new Event("Updated active directory to: " + userDir));
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
        logger.logEvent(new Event("Removed song at index " + i));
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
            if (songDbContainsFile(new File(newFileName).getCanonicalPath())) {
                logger.logEvent(new Event("File already in database, skipping."));
                return;
            }
        } catch (IOException e) {
            logger.logEvent(new Event("Error while trying to get absolute path of file."));
            return;
        }
        logger.logEvent(new Event("Swapping in file " + newFileName + "..."));
        AudioDataStructure data = new AudioDataStructure(newFileName);
        if (data.isEmpty()) {
            logger.logEvent(new Event("Unknown file type, cannot use file."));
            return;
        }
        songFilelist.set(i, data);
        logger.logEvent(new Event("Updated file for index " + i + "!"));
    }

    // Modifies: this
    // Effects:  updates file pointer for index
    public void updateAudioFile(int i, AudioDataStructure data) {
        songFilelist.set(i, data);
    }
}
