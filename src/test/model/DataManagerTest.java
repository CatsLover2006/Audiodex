package model;

import audio.AudioDataStructure;
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
        assertEquals(20, database.audioListSize());
        database.sanitizeAudioDatabase();
        assertEquals(20, database.audioListSize());
        database.removeSongIndex(0);
        assertEquals(19, database.audioListSize());
        database.addFileToSongDatabase("./data/scarlet.wav");
        assertEquals(20, database.audioListSize());
        database.sanitizeAudioDatabase();
        assertEquals(20, database.audioListSize());
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
        (new File("./data/db/audioFolder")).mkdirs();
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
        assertEquals(20, database.audioListSize());
        assertNull(database.getAudioFile(-1));
        for (int i = 0; i < 20; i++) {
            assertNotNull(database.getAudioFile(i));
        }
        assertNull(database.getAudioFile(20));
    }

    @Test
    @Order(6)
    public void revertDbTest() {
        database.revertDb();
        database.cleanDb(2);
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
        assertTrue((new File("./data/db/2.audiodex.json")).delete());
        database.loadDatabase();
        database.revertDb();
        database.revertDb(); // Fails to revert
        database.loadDatabase();
        assertTrue(database.saveDatabaseFile());
        File file = new File("./data/db/2.audiodex.json");
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            System.out.println("FileInputStream...");
            byte[] fileContent = fileInputStream.readNBytes(18000);
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
        database.cleanDb("./data/readonly");
        database.sanitizeAudioDatabase();
        database.addFileToSongDatabase("\u0000");
        database.updateAudioFile(2, "\u0000");
        database.addDirToSongDatabase("./data/scarlet.mp3");
        new FileManager();
        FileManager.writeToFile("\u0000", "This fails");
        database.updateAudioFile(1, new AudioDataStructure("/data/scarlet.lol.mp3"));
        assertEquals(20, database.audioListSize());
        assertEquals(1, database.getRemovedAudioFiles().size());
        assertEquals(1, database.getRemovedAudioFiles().get(0));
        database.removeEmptyAudioFiles();
        assertEquals(19, database.audioListSize());
        assertEquals(0, database.getRemovedAudioFiles().size());
    }
}
