package audio.filetypes.encoders;

import audio.AudioDecoder;
import audio.AudioEncoder;
import audio.filetypes.decoders.Aiff;
import org.junit.jupiter.api.Test;
import ui.AudioFilePlaybackBackend;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class MP3Test {
    AudioDecoder decoder;
    AudioEncoder encoder;

    private class EncodeThread extends Thread {
        public void run() {
            assertTrue(encoder.encodeAudio("./data/out/scarlet.mp3"));
        }
    }

    @Test
    public void doEncodeTest() {
        decoder = new Aiff("./data/scarlet.aif");
        encoder = new MP3();
        encoder.setSource(decoder);
        decoder.prepareToPlayAudio();
        HashMap<String, String> options = new HashMap<>();
        options.put("mono bitrate", "160 kbps");
        options.put("variable bit rate", "No");
        options.put("stereo", "Yes");
        encoder.setAudioFormat(decoder.getAudioOutputFormat(), options);
        assertEquals(0, encoder.encodedPercent());
        assertNotNull(encoder.getEncoderSpecificSelectors());
        EncodeThread thread = new EncodeThread();
        thread.start();
        assertEquals(0, encoder.encodedPercent());
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // LMAO next
            }
        }
        decoder.closeAudioFile();
        decoder = new audio.filetypes.decoders.MP3("./data/out/scarlet.mp3");
        decoder.prepareToPlayAudio();
        assertEquals("Scarlet Fire", decoder.getID3().getID3Data("Title"));
        decoder.closeAudioFile();
        System.out.println("Playing back encoded audio...");
        AudioFilePlaybackBackend player = new AudioFilePlaybackBackend();
        player.loadAudio("./data/out/scarlet.mp3");
        player.startAudioDecoderThread();
        player.playAudio();
        player.waitForAudioFinish();
        player.cleanBackend();
    }

    @Test
    public void failEncodeTest() {
        decoder = new Aiff("./data/scarlet.aif");
        encoder = new audio.filetypes.encoders.Aiff();
        encoder.setSource(decoder);
        decoder.prepareToPlayAudio();
        HashMap<String, String> options = new HashMap<>();
        options.put("mono bitrate", "16 kbps");
        options.put("variable bit rate", "Yes");
        options.put("stereo", "No"); // LMAO settings
        encoder.setAudioFormat(decoder.getAudioOutputFormat(), options);
        assertEquals(0, encoder.encodedPercent());
        assertNull(encoder.getEncoderSpecificSelectors()); // Japanese directory name
        assertFalse(encoder.encodeAudio("./data/お/scarlet.mp3"));
    }
}
