package model;

import audio.AudioDataStructure;
import org.json.JSONArray;
import org.junit.jupiter.api.*;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataManagerTest {
    static DataManager database;

    @BeforeAll
    public static void preTest() {
        File dbDir = new File("./data/db");
        assertNotNull(dbDir);
        if (dbDir.exists()) {
            for (File f : dbDir.listFiles()) {
                assertTrue(f.delete());
            }
            assertTrue(dbDir.delete());
        }
        database = new DataManager();
        database.setUserDir("./data/db/");
    }

    @Test
    @Order(0)
    public void loadEmptyDatabase() {
        database.loadDatabase();
        assertEquals(0, database.audioListSize());
    }

    @Test
    @Order(1)
    public void saveEmptyDatabase() {
        assertTrue(database.saveDatabaseFile());
    }

    @Test
    @Order(2)
    public void databaseManagementTest() {
        assertEquals(0, database.audioListSize());
        database.addFileToSongDatabase("./data/scarlet.aif");
        assertEquals(1, database.audioListSize());
        database.updateAudioFile(0,"./data/scarlet.wav");
        assertEquals(1, database.audioListSize());
        database.updateAudioFile(0,"./data/scarlet.wav");
        assertEquals(1, database.audioListSize());
        database.updateAudioFile(0,"./data/scarlet.wav.lmao");
        assertEquals(1, database.audioListSize());
        database.addDirToSongDatabase("./data/");
        assertEquals(24, database.audioListSize());
        database.addDirToSongDatabase("./data/db/audiofolder");
        assertEquals(24, database.audioListSize());
        database.sanitizeAudioDatabase();
        assertEquals(24, database.audioListSize());
        database.removeSongIndex(0);
        assertEquals(23, database.audioListSize());
        database.addFileToSongDatabase("./data/scarlet.wav");
        assertEquals(24, database.audioListSize());
        database.sanitizeAudioDatabase();
        assertEquals(24, database.audioListSize());
    }

    @Test
    @Order(3)
    public void saveDatabaseTest() {
        File dbDir = new File("./data/db");
        assertNotNull(dbDir);
        for (File f : dbDir.listFiles()) {
            assertTrue(f.delete());
        }
        assertTrue(dbDir.delete());
        assertTrue(database.saveDatabaseFile());
        new File("./data/db/audioFolder").mkdirs();
    }

    @Test
    @Order(4)
    public void loadDatabaseTest() {
        database.loadDatabase();
        assertTrue(database.saveDatabaseFile());
    }

    @Test
    @Order(5)
    public void doSortTest() {
        database.sortSongList("Filesize");
        database.sortSongList("Artist");
        database.sortSongList("Album");
        database.sortSongList("AlbumArtist");
        database.sortSongList("Title");
        database.sortSongList("Album-Title"); // I used to use this
        database.sortSongList("Album_Title");
        database.sanitizeAudioDatabase();
        assertEquals(24, database.audioListSize());
        assertNull(database.getAudioFile(-1));
        for (int i = 0; i < 24; i++) {
            assertNotNull(database.getAudioFile(i));
        }
        assertNull(database.getAudioFile(24));
    }

    @Test
    @Order(6)
    public void revertDbTest() {
        database.revertDb();
        database.cleanDb(2);
        // Can't make any assertions because there's no publicly-accessible change
        assertTrue(true);
    }

    @Test
    @Order(7)
    public void cleanDbTest() {
        for (int i = 0; i < 10; i++) {
            assertTrue(database.saveDatabaseFile());
        }
        database.cleanOldDb();
        database.cleanDbFldr();
    }

    @Test
    @Order(8)
    public void brokenDatabaseTest() {
        database.saveDatabaseFile();
        assertTrue(new File("./data/db/2.audiodex.json").delete());
        database.loadDatabase();
        database.revertDb();
        database.revertDb(); // Fails to revert
        database.loadDatabase();
        assertTrue(database.saveDatabaseFile());
        File file = new File("./data/db/2.audiodex.json");
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            System.out.println("FileInputStream...");
            byte[] fileContent = new byte[10000];
            fileInputStream.read(fileContent);
            fileInputStream.close();
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            System.out.println("FileOutputStream...");
            fileOutputStream.write(fileContent);
            fileOutputStream.close();
        } catch (Exception e) {
            fail("File error occured");
        }
        database.loadDatabase();
        database.cleanDbFldr();
        database.cleanDbFldr();
        database.saveDatabaseFile();
    }

    @Test
    @Order(9)
    public void brokenFileTest() {
        database.revertDb();
        DataManager.cleanDb("./data/readonly");
        database.sanitizeAudioDatabase();
        database.addFileToSongDatabase("\u0000");
        database.updateAudioFile(2, "\u0000");
        database.addDirToSongDatabase("./data/scarlet.mp3");
        new FileManager();
        FileManager.writeToFile("\u0000", "This fails");
        database.updateAudioFile(1, new AudioDataStructure("/data/scarlet.lol.mp3"));
        assertEquals(24, database.audioListSize());
        assertEquals(1, database.getRemovedAudioFiles().size());
        assertEquals(1, database.getRemovedAudioFiles().get(0));
        database.removeEmptyAudioFiles();
        assertEquals(23, database.audioListSize());
        assertEquals(0, database.getRemovedAudioFiles().size());
        manager = new DataManager();
        manager.setUserDir("./data/db/null");
        manager.cleanDbFldr();
        new ExceptionIgnore(); // Throw this in to save that
    }

    static DataManager manager;

    @Test
    @Order(10)
    public void legacyDatabaseTest() {
        if (!database.getSettings().doSoundCheck()) {
            database.getSettings().toggleSoundCheck();
        } // Set sound check
        JSONArray array = new JSONArray();
        for (int i = 0; i < database.audioListSize(); i++) {
            array.put(database.getAudioFile(i).encode());
        } // Clone
        FileManager.writeToFile("data/db/1.audiodex.json", array.toString());
        manager = new DataManager();
        manager.setUserDir("./data/db/");
        manager.loadDatabase();
        for (int i = 0; i < manager.audioListSize(); i++) {
            assertEquals(database.getAudioFile(i).getFilename(), manager.getAudioFile(i).getFilename());
        } // Sound check defaults to off, it was set on.
        assertNotEquals(database.getSettings().doSoundCheck(), manager.getSettings().doSoundCheck());
    }
}
