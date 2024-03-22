package audio;

import audio.filetypes.TagConversion;
import org.json.JSONObject;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(0)
public class AudioDataStructureTest {
    AudioDataStructure structure;

    @Test
    public void createFromFile() {
        structure = new AudioDataStructure("./data/scarlet.aif");
        assertEquals(new File("./data/scarlet.aif").length(), structure.getFilesize());
        assertFalse(structure.isEmpty());
        assertEquals(16, structure.getSamplesize());
        assertEquals(1411, structure.getBitrate() / 1000);
        try {
            assertEquals(new File("./data/scarlet.aif").getCanonicalPath(), structure.getFilename());
        } catch (IOException e) {
            fail("Where dis file at?");
        }
        assertEquals(AudioFileType.AIFF, structure.getAudioFileType());
        JSONObject json = structure.encode();
        assertEquals(structure.getFilesize(), json.get("filesize"));
        assertEquals("Scarlet Fire", structure.getId3Data().getID3Data("Title"));
        assertEquals("Scarlet Fire by Otis McDonald", structure.getPlaybackString());
        structure.updateID3(new ID3Container());
        assertEquals("scarlet.aif", structure.getPlaybackString());
    }

    @Test
    public void loadFromKnownData() {
        JSONObject json = new AudioDataStructure("./data/scarlet.aif").encode();
        structure = AudioDataStructure.decode(json);
        assertEquals(new File("./data/scarlet.aif").length(), structure.getFilesize());
        assertFalse(structure.isEmpty());
        assertEquals(16, structure.getSamplesize());
        assertEquals(1411, structure.getBitrate() / 1000);
        try {
            assertEquals(new File("./data/scarlet.aif").getCanonicalPath(), structure.getFilename());
        } catch (IOException e) {
            fail("Where dis file at?");
        }
        assertEquals(AudioFileType.AIFF, structure.getAudioFileType());
        assertEquals("Scarlet Fire", structure.getId3Data().getID3Data("Title"));
        assertEquals("Scarlet Fire by Otis McDonald", structure.getPlaybackString());
    }

    @Test
    public void emptyTest() {
        new TagConversion(); // I'm not making a test class just for this
        structure = new AudioDataStructure("./lmao");
        assertTrue(structure.isEmpty());
        // Next test is known to cause IOException for (File).getCanonicalPath()
        structure = new AudioDataStructure("\u0000/lmao");
        assertTrue(structure.isEmpty());
    }

    @Test
    public void edgeCaseTests() {
        AudioDataStructure nu = AudioDataStructure.decode(new JSONObject("{\"filetype\":\"MP3\",\"filename\":\""
                + "/Users/hanabi/Documents/Soulseek/upload/NoteBlock/Unknown Album/01 _Fall-Stars_ Fall Guys Medley.mp3"
                + "\",\"bitrate\":1411200,\"filesize\":\"6270486\",\"ID3data\":{\"Artist\":\"NoteBlock\",\"Tracks\":1,"
                + "\"Year\":2020,\"bitRate\":128,\"VBR\":\"NO\",\"Title\":\"\\\"Fall-Stars\\\" Fall Guys Medley\",\"sam"
                + "pleRate\":44100,\"Track\":1},\"samplesize\":\"NO\"}"));
        assertEquals(6270486, nu.getFilesize());
        assertEquals(0, nu.getSamplesize());
        structure = new AudioDataStructure("./data/scarlet.aif");
        assertNotEquals(structure.hashCode(), nu.hashCode());
        assertFalse(structure.equals(nu));
        structure = AudioDataStructure.decode(new JSONObject("{\"filetype\":\"MP3\",\"filename\":\""
                + "/Users/hanabi/Documents/Soulseek/upload/NoteBlock/Unknown Album/01 _Fall-Stars_ Fall Guys Medley.mp3"
                + "\",\"bitrate\":1411200,\"filesize\":\"6270486\",\"ID3data\":{\"Artist\":\"NoteBlock\",\"Tracks\":1,"
                + "\"Year\":2020,\"bitRate\":128,\"VBR\":\"NO\",\"Title\":\"\\\"Fall-Stars\\\" Fall Guys Medley\",\"sam"
                + "pleRate\":44100,\"Track\":1},\"samplesize\":\"NO\"}"));
        assertEquals(structure.hashCode(), nu.hashCode());
        assertTrue(structure.equals(nu));
        assertFalse(structure.equals(69));
        assertFalse(structure.qualityErrorAlreadyOccured());
        structure.markQualityErrorOccured();
        assertTrue(structure.qualityErrorAlreadyOccured());
    }
}
