package audio;

import audio.filetypes.TagConversion;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class AudioDataStructureTest {
    AudioDataStructure structure;

    @Test
    public void createFromFile() {
        structure = new AudioDataStructure("./data/scarlet.aif");
        assertEquals(25751698, structure.getFilesize());
        assertFalse(structure.isEmpty());
        assertEquals(16, structure.getSamplesize());
        assertEquals(1411, structure.getBitrate() / 1000);
        try {
            assertEquals((new File("./data/scarlet.aif")).getCanonicalPath(), structure.getFilename());
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
        JSONObject json = (new AudioDataStructure("./data/scarlet.aif")).encode();
        structure = AudioDataStructure.decode(json);
        assertEquals(25751698, structure.getFilesize());
        assertFalse(structure.isEmpty());
        assertEquals(16, structure.getSamplesize());
        assertEquals(1411, structure.getBitrate() / 1000);
        try {
            assertEquals((new File("./data/scarlet.aif")).getCanonicalPath(), structure.getFilename());
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
}
