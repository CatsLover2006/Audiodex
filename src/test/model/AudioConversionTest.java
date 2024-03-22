package model;

import audio.AudioDataStructure;
import audio.filetypes.encoders.AiffTest;
import audio.filetypes.encoders.MP3Test;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(0)
public class AudioConversionTest {
    AudioConversion converter;

    // Make a lambda for this
    private interface RunnableFloat {
        double run();
    }

    // Percentage display class using lambda
    private static class PercentDisp extends Thread {
        final RunnableFloat thing;
        PercentDisp(RunnableFloat yay) {
            thing = yay;
        }

        @Override
        public void run() {
            while (true) {
                ExceptionIgnore.ignoreExc(() -> sleep(100));
                System.out.println(thing.run() * 100 + "%");
                if (thing.run() >= 1) return;
            }
        }
    }

    @Test
    public void baseTest() throws IOException {
        converter = new AudioConversion(new AudioDataStructure("data/scarlet.aif"),
                "data/out/scarlet.conv.wav");
        converter.setAudioSettings(new HashMap<>());
        assertNull(converter.getOptions());
        assertEquals(new File("data/out/scarlet.conv.wav").getCanonicalPath(), converter.getTarget());
        assertFalse(converter.isFinished());
        assertFalse(converter.errorOccurred());
        new PercentDisp(() -> converter.getComplete()).start();
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
        assertEquals(new File("data/out/scarlet.conv.aif").getCanonicalPath(), converter.getTarget());
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
        assertEquals(0, converter.getComplete());
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
