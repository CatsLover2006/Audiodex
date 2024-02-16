package model;

import audio.AudioDataStructure;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class AudioConversionTest {
    AudioConversion converter;

    @Test
    public void baseTest() {
        converter = new AudioConversion(new AudioDataStructure("data/scarlet.aif"),
                "data/out/scarlet.conv.wav");
        converter.setAudioSettings(new HashMap<>());
        assertFalse(converter.isFinished());
        assertFalse(converter.errorOccurred());
        converter.start();
        converter.waitForEncoderFinish();
        assertTrue(converter.isFinished());
        assertFalse(converter.errorOccurred());
    }

    @Test
    public void aifOutTest() {
        converter = new AudioConversion(new AudioDataStructure("data/scarlet.aif"),
                "data/out/scarlet.conv.aif");
    }

    @Test
    public void mp3OutTest() {
        converter = new AudioConversion(new AudioDataStructure("data/scarlet.aif"),
                "data/out/scarlet.conv.mp3");
        assertNotNull(converter.getOptions());
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
                "data/お/scarlet.wav");
        converter.start();
        converter.waitForEncoderFinish();
        assertTrue(converter.errorOccurred());
    }
}
