package audio.filetypes.encoders;

import audio.AudioDecoder;
import audio.AudioEncoder;
import audio.filetypes.decoders.Aiff;
import audio.filetypes.decoders.MP4alac;
import audio.filetypes.decoders.MpegType;
import model.ExceptionIgnore;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import ui.AudioFilePlaybackBackend;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(3)
public class MP3Test {
    AudioDecoder decoder;
    AudioEncoder encoder;

    private class EncodeThread extends Thread {
        @Override
        public void run() {
            assertTrue(encoder.encodeAudio("./data/out/scarlet.mp3"));
        }
    }

    @Test
    public void doEncodeTest() {
        decoder = new MP4alac("./data/scarlet.alac.m4a");
        encoder = new MP3();
        encoder.setSource(decoder);
        decoder.prepareToPlayAudio();
        assertEquals(0, encoder.encodedPercent());
        while (decoder.moreSamples()) {
            decoder.getNextSample();
        }
        assertEquals(1, encoder.encodedPercent());
        HashMap<String, String> options = new HashMap<>();
        options.put("Bitrate", "160 kbps");
        options.put("VBR", "No");
        options.put("Stereo", "Yes");
        encoder.setAudioFormat(decoder.getAudioOutputFormat(), options);
        for (String qual : encoder.getEncoderSpecificSelectors().get("Quality")) {
            options.put("Quality", qual);
            encoder.setAudioFormat(decoder.getAudioOutputFormat(), options);
        }
        options.put("Quality", "Lowest"); // Speedup
        encoder.setAudioFormat(decoder.getAudioOutputFormat(), options);
        decoder.goToTime(0);
        assertNotNull(encoder.getEncoderSpecificSelectors());
        EncodeThread thread = new EncodeThread();
        new PercentDisp(() -> encoder.encodedPercent()).start();
        assertEquals(0, encoder.encodedPercent());
        thread.start();
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // LMAO next
            }
        }
        decoder.closeAudioFile();
        decoder = new MpegType("./data/out/scarlet.mp3");
        decoder.prepareToPlayAudio();
        assertEquals("Scarlet Fire", decoder.getID3().getID3Data("Title"));
        decoder.closeAudioFile();
        System.out.println("Playing back encoded audio...");
        AudioFilePlaybackBackend player = new AudioFilePlaybackBackend();
        player.loadAudio("./data/out/scarlet.mp3");
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
        encoder = new audio.filetypes.encoders.MP3();
        assertFalse(encoder.encodeAudio("./data/out/scarlet.lol.mp3"));
        encoder.setSource(decoder);
        decoder.prepareToPlayAudio();
        HashMap<String, String> options = new HashMap<>();
        options.put("Bitrate", "16 kbps");
        options.put("VBR", "Yes");
        options.put("Stereo", "No"); // LMAO settings
        encoder.setAudioFormat(decoder.getAudioOutputFormat(), options);
        assertEquals(0, encoder.encodedPercent());
        assertNotNull(encoder.getEncoderSpecificSelectors()); // Japanese directory name
        assertFalse(encoder.encodeAudio("./data/\u3042/scarlet.mp3"));
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
