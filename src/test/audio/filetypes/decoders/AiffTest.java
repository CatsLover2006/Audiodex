package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioSample;
import audio.ID3Container;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;

import static org.junit.jupiter.api.Assertions.*;

public class AiffTest {
    AudioDecoder aiffDecoder;

    @BeforeEach
    public void prepare() {
        aiffDecoder = new Aiff("./data/scarlet.aif");
    }

    @Test // Test if prepareToPlayAudio and closeAudioFile work
    public void fileManagementTest() {
        assertFalse(aiffDecoder.isReady());
        aiffDecoder.prepareToPlayAudio();
        assertTrue(aiffDecoder.isReady());
        aiffDecoder.closeAudioFile();
        assertFalse(aiffDecoder.isReady());
    }

    @Test
    public void skipTest() {
        assertFalse(aiffDecoder.isReady());
        aiffDecoder.prepareToPlayAudio();
        assertTrue(aiffDecoder.isReady());
        assertEquals(0, aiffDecoder.getCurrentTime());
        aiffDecoder.goToTime(100);
        assertEquals(100, aiffDecoder.getCurrentTime());
    }

    @Test // Test if decoding works
    public void decodeTest() {
        AudioDecoder wavDecoder = new WAV("./data/scarlet.wav");
        assertFalse(aiffDecoder.isReady());
        aiffDecoder.prepareToPlayAudio();
        assertTrue(aiffDecoder.isReady());
        AudioFormat format = aiffDecoder.getAudioOutputFormat();
        assertEquals(2, format.getChannels());
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(44100, format.getSampleRate());
        aiffDecoder.goToTime(1);
        AudioSample sample = aiffDecoder.getNextSample();
        assertEquals(4096, sample.getLength());
        wavDecoder.prepareToPlayAudio();
        wavDecoder.goToTime(1);
        AudioSample wavSample = wavDecoder.getNextSample();
        while (aiffDecoder.moreSamples()) {
            // Endian fix
            for (int i = 0; i < sample.getLength(); i += 2) {
                byte t = sample.getData()[i];
                sample.getData()[i] = sample.getData()[i + 1];
                sample.getData()[i + 1] = t;
            }
            assertArrayEquals(wavSample.getData(), sample.getData());
            wavSample = wavDecoder.getNextSample();
            sample = aiffDecoder.getNextSample();
        }
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(aiffDecoder.isReady());
        aiffDecoder.prepareToPlayAudio();
        assertTrue(aiffDecoder.isReady());
        ID3Container id3 = aiffDecoder.getID3();
        assertEquals("Scarlet Fire", id3.getID3Data("Title"));
        assertEquals("Otis McDonald", id3.getID3Data("Artist"));
        assertEquals("YouTube Audio Library", id3.getID3Data("Album"));
        assertEquals(2015L, id3.getID3Data("Year"));
    }
}
