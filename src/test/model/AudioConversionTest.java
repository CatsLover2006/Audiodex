package model;

import audio.AudioDataStructure;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class AudioConversionTest {
    AudioConversion converter;

    @Test
    public void baseTest() throws IOException {
        converter = new AudioConversion(new AudioDataStructure("data/scarlet.aif"),
                "data/out/scarlet.conv.wav");
        converter.setAudioSettings(new HashMap<>());
        assertNull(converter.getOptions());
        assertEquals(new File("data/out/scarlet.conv.wav").getCanonicalFile(), converter.getTarget());
        assertFalse(converter.isFinished());
        assertFalse(converter.errorOccurred());
        converter.start();
        converter.waitForEncoderFinish();
        assertTrue(converter.isFinished());
        assertFalse(converter.errorOccurred());
    }

    @Test
    public void aifOutTest() throws IOException {
        converter = new AudioConversion(new AudioDataStructure("data/scarlet.aif"),
                "data/out/scarlet.conv.aif");
        assertNull(converter.getOptions());
        assertEquals(new File("data/out/scarlet.conv.aif").getCanonicalFile(), converter.getTarget());
    }

    @Test
    public void mp3OutTest() throws IOException {
        converter = new AudioConversion(new AudioDataStructure("data/scarlet.aif"),
                "data/out/scarlet.conv.mp3");
        assertNotNull(converter.getOptions());
        assertEquals(new File("data/out/scarlet.conv.mp3").getCanonicalPath(), converter.getTarget());
    }

    @Test
    public void noOutTest() {
        converter = new AudioConversion(new AudioDataStructure("data/scarlet.aif"),
                "data/out/scarlet.conv");
        assertTrue(converter.errorOccurred());
        assertNull(converter.getOptions());
    }

    @Test
    public void noInTest() {
        converter = new AudioConversion(new AudioDataStructure("data/scarlet"), "lmao");
        assertTrue(converter.errorOccurred());
    }

    @Test
    public void invalidDirectoryTest() {
        converter = new AudioConversion(new AudioDataStructure("data/scarlet.mp3"),
                "data/\u3042/scarlet.wav");
        converter.start();
        converter.waitForEncoderFinish();
        assertTrue(converter.errorOccurred());
    }
}
