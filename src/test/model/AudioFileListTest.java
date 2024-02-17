package model;

import org.junit.jupiter.api.*;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AudioFileListTest {
    static AudioFileList database;

    @BeforeAll
    public static void preTest() {
        File dbDir = new File("./data/db");
        assertNotNull(dbDir);
        for (File f : dbDir.listFiles()) {
            assertTrue(f.delete());
        }
        assertTrue(dbDir.delete());
        database = new AudioFileList();
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
        assertEquals(0, database.listSize());
        database.addFileToDatabase("./data/scarlet.aif");
        assertEquals(1, database.listSize());
        database.addDirToDatabase("./data/");
        assertEquals(20, database.listSize());
        database.addFileToDatabase("./data/scarlet.aif");
        assertEquals(20, database.listSize());
        database.sanitizeDatabase();
        assertEquals(20, database.listSize());
    }

    @Test
    @Order(3)
    public void saveDatabaseTest() {
        assertTrue(database.saveDatabaseFile());
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
        database.sortList("Filesize");
        database.sortList("Artist");
        database.sortList("Album");
        database.sortList("AlbumArtist");
        database.sortList("Title");
        database.sortList("Album_Title");
        database.sanitizeDatabase();
        assertEquals(20, database.listSize());
        assertNull(database.get(-1));
        for (int i = 0; i < 20; i++) {
            assertNotNull(database.get(i));
        }
        assertNull(database.get(20));
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
    }
}
