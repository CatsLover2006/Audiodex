package audio.filetypes.decoders.readonly;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import audio.filetypes.decoders.MpegType;
import audio.filetypes.decoders.WAV;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;

import static org.junit.jupiter.api.Assertions.*;

public class MpegTypeTest {
    AudioDecoder mp3Decoder;

    @BeforeEach
    public void prepare() {
        mp3Decoder = new MpegType("data/readonly/scarlet.mp3");
        Thread.currentThread().setPriority(2);
    }

    @Test // Test if prepareToPlayAudio and closeAudioFile work
    public void fileManagementTest() {
        assertFalse(mp3Decoder.isReady());
        mp3Decoder.prepareToPlayAudio();
        assertTrue(mp3Decoder.isReady());
        mp3Decoder.closeAudioFile();
        assertFalse(mp3Decoder.isReady());
        assertEquals("scarlet.mp3", mp3Decoder.getFileName());
        assertEquals(143, Math.floor(mp3Decoder.getFileDuration()));
        assertEquals(AudioFileType.MP3, mp3Decoder.getFileType());
    }

    @Test
    public void skipTest() {
        assertFalse(mp3Decoder.isReady());
        mp3Decoder.prepareToPlayAudio();
        assertTrue(mp3Decoder.isReady());
        assertFalse(mp3Decoder.skipInProgress());
        assertEquals(0, mp3Decoder.getCurrentTime());
        mp3Decoder.getNextSample(); // Crash fix
        mp3Decoder.goToTime(100);
        mp3Decoder.getNextSample(); // Timer update
        // Error range due to timing math
        assertTrue(Math.abs(100 - mp3Decoder.getCurrentTime()) < 0.05);
        mp3Decoder.getNextSample(); // Crash fix
        mp3Decoder.goToTime(10);
        mp3Decoder.getNextSample(); // Timer update
        // Error range due to timing math
        assertTrue(Math.abs(10 - mp3Decoder.getCurrentTime()) < 0.05);
        assertFalse(mp3Decoder.skipInProgress());
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
        id3.setID3Data("Encoder", "Audiodex");
        mp3Decoder.setID3(id3);
        mp3Decoder.setArtwork(mp3Decoder.getArtwork());
    }
}
