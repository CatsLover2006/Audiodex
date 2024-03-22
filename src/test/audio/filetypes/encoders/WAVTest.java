package audio.filetypes.encoders;

import audio.AudioDecoder;
import audio.AudioEncoder;
import audio.filetypes.decoders.Aiff;
import audio.filetypes.decoders.MP4alac;
import model.ExceptionIgnore;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import ui.AudioFilePlaybackBackend;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(2)
public class WAVTest {
    AudioDecoder decoder;
    AudioEncoder encoder;

    private class EncodeThread extends Thread {
        @Override
        public void run() {
            assertTrue(encoder.encodeAudio("./data/out/scarlet.wav"));
        }
    }

    @Test
    public void doEncodeTest() {
        decoder = new MP4alac("./data/scarlet.alac.m4a");
        encoder = new audio.filetypes.encoders.WAV();
        assertEquals(0, encoder.encodedPercent());
        encoder.setSource(decoder);
        decoder.prepareToPlayAudio();
        while (decoder.moreSamples()) {
            decoder.getNextSample();
        }
        assertEquals(1, encoder.encodedPercent());
        decoder.goToTime(0);
        HashMap<String, String> options = new HashMap<>();
        encoder.setAudioFormat(decoder.getAudioOutputFormat(), options);
        assertNull(encoder.getEncoderSpecificSelectors());
        assertEquals(0, encoder.encodedPercent());
        EncodeThread thread = new EncodeThread();
        new PercentDisp(() -> encoder.encodedPercent()).start();
        thread.start();
        assertEquals(0, encoder.encodedPercent());
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // LMAO next
            }
        }
        assertEquals(1, encoder.encodedPercent());
        decoder.closeAudioFile();
        decoder = new audio.filetypes.decoders.WAV("./data/out/scarlet.wav");
        decoder.prepareToPlayAudio();
        assertNotEquals("Scarlet Fire", decoder.getID3().getID3Data("Title"));
        decoder.closeAudioFile();
        System.out.println("Playing back encoded audio...");
        AudioFilePlaybackBackend player = new AudioFilePlaybackBackend();
        player.loadAudio("./data/out/scarlet.wav");
        player.startAudioDecoderThread();
        player.playAudio();
        new PercentDisp(() -> player.getPercentPlayed()).start();
        ExceptionIgnore.ignoreExc(() -> Thread.sleep(1000));
        player.seekTo(player.getFileDuration() - 1);
        player.waitForAudioFinish();
        player.cleanBackend();
    }

    @Test
    public void failEncodeTest() {
        decoder = new Aiff("./data/scarlet.aif");
        encoder = new audio.filetypes.encoders.WAV();
        encoder.setSource(decoder);
        decoder.prepareToPlayAudio();
        HashMap<String, String> options = new HashMap<>();
        encoder.setAudioFormat(decoder.getAudioOutputFormat(), options);
        assertEquals(0, encoder.encodedPercent());
        assertNull(encoder.getEncoderSpecificSelectors()); // Japanese directory name
        assertFalse(encoder.encodeAudio("./data/\u3042/scarlet.wav"));
    }
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
}
