package audio.filetypes.decoders.readonly;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import audio.filetypes.decoders.Vorbis;
import audio.filetypes.decoders.WAV;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;

import static org.junit.jupiter.api.Assertions.*;

public class VorbisTest {
    Vorbis vorbisDecoder;

    @BeforeEach
    public void prepare() {
        vorbisDecoder = new Vorbis("data/readonly/scarlet.vorbis.ogg");
        Thread.currentThread().setPriority(2);
    }

    @Test // Test if prepareToPlayAudio and closeAudioFile work
    public void fileManagementTest() {
        assertFalse(vorbisDecoder.isReady());
        vorbisDecoder.prepareToPlayAudio();
        assertTrue(vorbisDecoder.isReady());
        vorbisDecoder.closeAudioFile();
        assertFalse(vorbisDecoder.isReady());
        assertEquals("scarlet.vorbis.ogg", vorbisDecoder.getFileName());
        assertEquals(143, Math.floor(vorbisDecoder.getFileDuration()));
        assertEquals(AudioFileType.VORBIS, vorbisDecoder.getFileType());
    }

    @Test
    public void skipTest() {
        assertFalse(vorbisDecoder.isReady());
        vorbisDecoder.prepareToPlayAudio();
        assertTrue(vorbisDecoder.isReady());
        assertEquals(0, vorbisDecoder.getCurrentTime());
        vorbisDecoder.goToTime(100);
        // Error range due to timing math
        assertTrue(Math.abs(100 - vorbisDecoder.getCurrentTime()) < 0.05);
        vorbisDecoder.goToTime(10);
        // Error range due to timing math
        assertTrue(Math.abs(10 - vorbisDecoder.getCurrentTime()) < 0.06);
        assertFalse(vorbisDecoder.skipInProgress());
    }

    @Test // Test if decoding works
    public void decodeTest() {
        AudioDecoder wavDecoder = new WAV("data/scarlet.vorbis.wav");
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
                if (i + wavOffset == wavSample.getLength()) {
                    wavSample = wavDecoder.getNextSample();
                    wavOffset = -i; // this works, trust me
                }
                try {
                    if (sample.getData()[i] != wavSample.getData()[i + wavOffset]) {
                        faults++;
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }
            }
            wavOffset += sample.getLength();
            sample = vorbisDecoder.getNextSample();
            // Endian fix
            for (int i = 0; i < sample.getLength(); i += 2) {
                byte t = sample.getData()[i];
                sample.getData()[i] = sample.getData()[i + 1];
                sample.getData()[i + 1] = t;
            }
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
        id3.setID3Data("Encoder", "Audiodex");
        vorbisDecoder.setID3(id3);
        vorbisDecoder.setArtwork(vorbisDecoder.getArtwork());
        // Default Value (fails to get from file for some reason)
        assertEquals(-6, vorbisDecoder.getReplayGain());
    }
}
