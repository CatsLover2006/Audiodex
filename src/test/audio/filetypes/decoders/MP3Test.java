package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioSample;
import audio.ID3Container;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;

import static org.junit.jupiter.api.Assertions.*;

public class MP3Test {
    AudioDecoder mp3Decoder;

    @BeforeEach
    public void prepare() {
        mp3Decoder = new MP3("data/scarlet.mp3");
    }

    @Test // Test if prepareToPlayAudio and closeAudioFile work
    public void fileManagementTest() {
        assertFalse(mp3Decoder.isReady());
        mp3Decoder.prepareToPlayAudio();
        assertTrue(mp3Decoder.isReady());
        mp3Decoder.closeAudioFile();
        assertFalse(mp3Decoder.isReady());
        assertEquals("scarlet.mp3", mp3Decoder.getFileName());
    }

    @Test
    public void skipTest() {
        assertFalse(mp3Decoder.isReady());
        mp3Decoder.prepareToPlayAudio();
        assertTrue(mp3Decoder.isReady());
        assertEquals(0, mp3Decoder.getCurrentTime());
        mp3Decoder.getNextSample(); // Crash fix
        mp3Decoder.goToTime(100);
        mp3Decoder.getNextSample(); // Timer update

        // Error range due to timing math
        assertTrue(Math.abs(100 - mp3Decoder.getCurrentTime()) < 0.05);
    }

    @Test // Test if decoding works
    public void decodeTest() {
        AudioDecoder wavDecoder = new WAV("data/scarlet.mp3.wav");
        assertFalse(mp3Decoder.isReady());
        mp3Decoder.prepareToPlayAudio();
        assertTrue(mp3Decoder.isReady());
        AudioFormat format = mp3Decoder.getAudioOutputFormat();
        assertEquals(2, format.getChannels());
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(44100, format.getSampleRate());
        AudioSample sample = mp3Decoder.getNextSample();
        assertEquals(4096, sample.getLength());
        wavDecoder.prepareToPlayAudio();
        AudioSample wavSample = wavDecoder.getNextSample();
        while (mp3Decoder.moreSamples()) {
            if (sample.getLength() == 0 || wavSample.getLength() == 0) {
                return; // We're done now!
            }
            assertArrayEquals(wavSample.getData(), sample.getData());
            wavSample = wavDecoder.getNextSample();
            sample = mp3Decoder.getNextSample();
        }
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(mp3Decoder.isReady());
        mp3Decoder.prepareToPlayAudio();
        assertTrue(mp3Decoder.isReady());
        ID3Container id3 = mp3Decoder.getID3();
        assertEquals("Scarlet Fire", id3.getID3Data("Title"));
        assertEquals("Otis McDonald", id3.getID3Data("Artist"));
        assertEquals("YouTube Audio Library", id3.getID3Data("Album"));
        assertEquals(2015L, id3.getID3Data("Year"));
    }
}
