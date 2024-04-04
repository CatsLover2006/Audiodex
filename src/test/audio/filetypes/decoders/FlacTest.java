package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import org.junit.jupiter.api.*;

import javax.sound.sampled.AudioFormat;

import static org.junit.jupiter.api.Assertions.*;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(1)
public class FlacTest {
    Flac flacDecoder;

    @BeforeEach
    public void prepare() {
        flacDecoder = new Flac("data/scarlet.flac");
        Thread.currentThread().setPriority(2);
    }

    @Test // Test if prepareToPlayAudio and closeAudioFile work
    public void fileManagementTest() {
        assertFalse(flacDecoder.isReady());
        flacDecoder.prepareToPlayAudio();
        assertTrue(flacDecoder.isReady());
        flacDecoder.closeAudioFile();
        assertFalse(flacDecoder.isReady());
        assertEquals("scarlet.flac", flacDecoder.getFileName());
        assertEquals(6, Math.floor(flacDecoder.getFileDuration()));
        assertEquals(AudioFileType.FLAC, flacDecoder.getFileType());
    }

    @Test
    public void skipTest() {
        assertFalse(flacDecoder.isReady());
        assertEquals(-1, flacDecoder.getCurrentTime());
        flacDecoder.prepareToPlayAudio();
        assertTrue(flacDecoder.isReady());
        assertEquals(0, flacDecoder.getCurrentTime());
        flacDecoder.goToTime(5);
        // Error range due to timing math
        assertTrue(Math.abs(5 - flacDecoder.getCurrentTime()) < 0.05);
        flacDecoder.goToTime(2);
        // Error range due to timing math
        assertTrue(Math.abs(2 - flacDecoder.getCurrentTime()) < 0.05);
        flacDecoder.goToTime(10);
        assertFalse(flacDecoder.moreSamples());
        assertFalse(flacDecoder.skipInProgress());
    }

    @Test // Test if decoding works
    public void decodeTest() {
        AudioDecoder wavDecoder = new WAV("data/scarlet.wav");
        assertFalse(flacDecoder.isReady());
        flacDecoder.prepareToPlayAudio();
        assertTrue(flacDecoder.isReady());
        AudioFormat format = flacDecoder.getAudioOutputFormat();
        assertEquals(2, format.getChannels());
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(44100, format.getSampleRate());
        flacDecoder.goToTime(1);
        wavDecoder.prepareToPlayAudio();
        wavDecoder.goToTime(flacDecoder.getCurrentTime());
        AudioSample sample = flacDecoder.getNextSample();
        AudioSample wavSample = wavDecoder.getNextSample();
        int wavOffset = 0;
        while (flacDecoder.moreSamples()) {
            // Different sample size fixing
            for (int i = 0; i < sample.getLength(); i++) {
                if (i + wavOffset == 4096) {
                    wavSample = wavDecoder.getNextSample();
                    wavOffset = -i; // this works, trust me
                }
                assertEquals(sample.getData()[i], wavSample.getData()[i + wavOffset]);
            }
            wavOffset += sample.getLength();
            sample = flacDecoder.getNextSample();
        }
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(flacDecoder.isReady());
        flacDecoder.prepareToPlayAudio();
        assertTrue(flacDecoder.isReady());
        ID3Container id3 = flacDecoder.getID3();
        assertEquals("Scarlet Fire", id3.getID3Data("Title"));
        assertEquals("Otis McDonald", id3.getID3Data("Artist"));
        assertEquals("YouTube Audio Library", id3.getID3Data("Album"));
        assertEquals(2015L, id3.getID3Data("Year"));
        id3.setID3Data("Encoder", "Audiodex");
        flacDecoder.setID3(id3);
        flacDecoder.setArtwork(flacDecoder.getArtwork());
        // Default Value (fails to get from file for some reason)
        assertEquals(-6, flacDecoder.getReplayGain());
    }
}
