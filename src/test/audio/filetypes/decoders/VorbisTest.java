package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioSample;
import audio.ID3Container;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;

import static org.junit.jupiter.api.Assertions.*;

public class VorbisTest {
    AudioDecoder vorbisDecoder;

    @BeforeEach
    public void prepare() {
        vorbisDecoder = new Vorbis("./data/scarlet.vorbis.ogg");
    }

    @Test // Test if prepareToPlayAudio and closeAudioFile work
    public void fileManagementTest() {
        assertFalse(vorbisDecoder.isReady());
        vorbisDecoder.prepareToPlayAudio();
        assertTrue(vorbisDecoder.isReady());
        vorbisDecoder.closeAudioFile();
        assertFalse(vorbisDecoder.isReady());
    }

    @Test
    public void skipTest() {
        assertFalse(vorbisDecoder.isReady());
        vorbisDecoder.prepareToPlayAudio();
        assertTrue(vorbisDecoder.isReady());
        assertEquals(0, vorbisDecoder.getCurrentTime());
        vorbisDecoder.getNextSample(); // Crash fix
        vorbisDecoder.goToTime(100);
        vorbisDecoder.getNextSample(); // Timer update

        // Error range due to timing math
        assertTrue(Math.abs(100 - vorbisDecoder.getCurrentTime()) < 0.05);
    }

    @Test // Test if decoding works
    public void decodeTest() {
        AudioDecoder wavDecoder = new WAV("./data/scarlet.vorbis.wav");
        assertFalse(vorbisDecoder.isReady());
        vorbisDecoder.prepareToPlayAudio();
        assertTrue(vorbisDecoder.isReady());
        AudioFormat format = vorbisDecoder.getAudioOutputFormat();
        assertEquals(2, format.getChannels());
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(44100, format.getSampleRate());
        AudioSample sample = vorbisDecoder.getNextSample();
        wavDecoder.prepareToPlayAudio();
        AudioSample wavSample = wavDecoder.getNextSample();
        int faults = 0; // Lossy decoder so some fault tolerance has to exist
        int wavOffset = 0;
        while (vorbisDecoder.moreSamples()) {
            // Vorbis decoding doesn't always have a same
            // length sample, so I've had to improvise
            for (int i = 0; i < sample.getLength(); i++) {
                if ((i + wavOffset) == 4096) {
                    wavSample = wavDecoder.getNextSample();
                    wavOffset = -i; // this works, trust me
                }
                if (sample.getData()[i] != wavSample.getData()[i + wavOffset]) {
                    faults++;
                }
            }
            wavOffset += sample.getLength();
            sample = vorbisDecoder.getNextSample();
        }
        assertTrue(faults < 4);
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(vorbisDecoder.isReady());
        vorbisDecoder.prepareToPlayAudio();
        assertTrue(vorbisDecoder.isReady());
        ID3Container id3 = vorbisDecoder.getID3();
        assertEquals("Scarlet Fire", id3.getID3Data("Title"));
        assertEquals("Otis McDonald", id3.getID3Data("Artist"));
        assertEquals("YouTube Audio Library", id3.getID3Data("Album"));
    }
}
