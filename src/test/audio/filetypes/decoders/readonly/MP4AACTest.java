package audio.filetypes.decoders.readonly;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import audio.filetypes.decoders.MP4AAC;
import audio.filetypes.decoders.WAV;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;

import static org.junit.jupiter.api.Assertions.*;

public class MP4AACTest {
    AudioDecoder aacDecoder;

    @BeforeEach
    public void prepare() {
        aacDecoder = new MP4AAC("data/readonly/scarlet.aac.m4a");
        Thread.currentThread().setPriority(2);
    }

    @Test // Test if prepareToPlayAudio and closeAudioFile work
    public void fileManagementTest() {
        assertFalse(aacDecoder.isReady());
        aacDecoder.prepareToPlayAudio();
        assertTrue(aacDecoder.isReady());
        aacDecoder.closeAudioFile();
        assertFalse(aacDecoder.isReady());
        assertEquals("scarlet.aac.m4a", aacDecoder.getFileName());
        assertEquals(142, Math.floor(aacDecoder.getFileDuration()));
        assertEquals(AudioFileType.AAC_MP4, aacDecoder.getFileType());
    }

    @Test
    public void skipTest() {
        assertFalse(aacDecoder.isReady());
        aacDecoder.prepareToPlayAudio();
        assertTrue(aacDecoder.isReady());
        assertEquals(-1, aacDecoder.getCurrentTime());
        aacDecoder.getNextSample();
        assertEquals(0, aacDecoder.getCurrentTime());
        aacDecoder.goToTime(100);
        aacDecoder.getNextSample();
        // Error range due to timing math
        assertTrue(Math.abs(100 - aacDecoder.getCurrentTime()) < 0.06);
        aacDecoder.goToTime(10);
        aacDecoder.getNextSample();
        // Error range due to timing math
        assertTrue(Math.abs(10 - aacDecoder.getCurrentTime()) < 0.06);
        assertFalse(aacDecoder.skipInProgress());
    }

    @Test // Test if decoding works
    public void decodeTest() {
        AudioDecoder wavDecoder = new WAV("data/scarlet.aac.wav");
        assertFalse(aacDecoder.isReady());
        aacDecoder.prepareToPlayAudio();
        assertTrue(aacDecoder.isReady());
        AudioFormat format = aacDecoder.getAudioOutputFormat();
        assertEquals(2, format.getChannels());
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(44100, format.getSampleRate());
        AudioSample sample = aacDecoder.getNextSample();
        assertEquals(4096, sample.getLength());
        wavDecoder.prepareToPlayAudio();
        AudioSample wavSample = wavDecoder.getNextSample();
        while (aacDecoder.moreSamples()) {
            if (sample.getLength() == 0 || wavSample.getLength() == 0) {
                return; // We're done now!
            }
            // Endian fix
            for (int i = 0; i < sample.getLength(); i += 2) {
                byte t = sample.getData()[i];
                sample.getData()[i] = sample.getData()[i + 1];
                sample.getData()[i + 1] = t;
            }
            assertArrayEquals(wavSample.getData(), sample.getData());
            wavSample = wavDecoder.getNextSample();
            sample = aacDecoder.getNextSample();
        }
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(aacDecoder.isReady());
        aacDecoder.prepareToPlayAudio();
        assertTrue(aacDecoder.isReady());
        ID3Container id3 = aacDecoder.getID3();
        assertEquals("Scarlet Fire", id3.getID3Data("Title"));
        assertEquals("Otis McDonald", id3.getID3Data("Artist"));
        assertEquals("YouTube Audio Library", id3.getID3Data("Album"));
        assertEquals(2015L, id3.getID3Data("Year"));
        id3.setID3Data("Encoder", "Audiodex");
        System.out.println(id3.encode().toString());
        aacDecoder.setID3(id3);
        aacDecoder.setArtwork(aacDecoder.getArtwork());
    }
}
