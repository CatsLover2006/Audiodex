package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioSample;
import audio.ID3Container;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;

import static org.junit.jupiter.api.Assertions.*;

public class MP4alacTest {
    AudioDecoder alacDecoder;

    @BeforeEach
    public void prepare() {
        alacDecoder = new MP4alac("./data/scarlet.alac.m4a");
    }

    @Test // Test if prepareToPlayAudio and closeAudioFile work
    public void fileManagementTest() {
        assertFalse(alacDecoder.isReady());
        alacDecoder.prepareToPlayAudio();
        assertTrue(alacDecoder.isReady());
        alacDecoder.closeAudioFile();
        assertFalse(alacDecoder.isReady());
    }

    @Test
    public void skipTest() {
        assertFalse(alacDecoder.isReady());
        alacDecoder.prepareToPlayAudio();
        assertTrue(alacDecoder.isReady());
        assertEquals(0, alacDecoder.getCurrentTime());
        alacDecoder.goToTime(100);
        assertEquals(100, alacDecoder.getCurrentTime());
    }

    @Test // Test if decoding works
    public void decodeTest() {
        AudioDecoder wavDecoder = new WAV("./data/scarlet.wav");
        assertFalse(alacDecoder.isReady());
        alacDecoder.prepareToPlayAudio();
        assertTrue(alacDecoder.isReady());
        AudioFormat format = alacDecoder.getAudioOutputFormat();
        assertEquals(2, format.getChannels());
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(44100, format.getSampleRate());
        alacDecoder.goToTime(1);
        AudioSample sample = alacDecoder.getNextSample();
        wavDecoder.prepareToPlayAudio();
        wavDecoder.goToTime(1);
        AudioSample wavSample = wavDecoder.getNextSample();
        int wavOffset = 0;
        while (alacDecoder.moreSamples()) {
            // Different sample size fixing
            for (int i = 0; i < sample.getLength(); i++) {
                if ((i + wavOffset) == 4096) {
                    wavSample = wavDecoder.getNextSample();
                    wavOffset = -i; // this works, trust me
                }
                assertEquals(sample.getData()[i], wavSample.getData()[i + wavOffset]);
            }
            wavOffset += sample.getLength();
            sample = alacDecoder.getNextSample();
        }
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(alacDecoder.isReady());
        alacDecoder.prepareToPlayAudio();
        assertTrue(alacDecoder.isReady());
        ID3Container id3 = alacDecoder.getID3();
        assertEquals("Scarlet Fire", id3.getID3Data("Title"));
        assertEquals("Otis McDonald", id3.getID3Data("Artist"));
        assertEquals("YouTube Audio Library", id3.getID3Data("Album"));
        assertEquals(2015L, id3.getID3Data("Year"));
    }
}
