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
public class MP4alacTest {
    MP4alac alacDecoder;

    private class ForcePauseThread extends Thread {
        @Override
        public void run() {
            alacDecoder.forceDisableDecoding();
            try {
                sleep(10);
            } catch (InterruptedException e) {
                // no
            }
            alacDecoder.forceEnableDecoding();
        }
    }

    @BeforeEach
    public void prepare() {
        alacDecoder = new MP4alac("data/scarlet.alac.m4a");
        Thread.currentThread().setPriority(2);
    }

    @Test // Test if prepareToPlayAudio and closeAudioFile work
    public void fileManagementTest() {
        assertFalse(alacDecoder.isReady());
        alacDecoder.prepareToPlayAudio();
        assertTrue(alacDecoder.isReady());
        alacDecoder.closeAudioFile();
        assertFalse(alacDecoder.isReady());
        assertEquals("scarlet.alac.m4a", alacDecoder.getFileName());
        assertEquals(6, Math.floor(alacDecoder.getFileDuration()));
        assertEquals(AudioFileType.ALAC_MP4, alacDecoder.getFileType());
    }

    @Test
    public void skipTest() {
        assertFalse(alacDecoder.isReady());
        alacDecoder.prepareToPlayAudio();
        assertTrue(alacDecoder.isReady());
        assertEquals(0, alacDecoder.getCurrentTime());
        alacDecoder.goToTime(5);
        // Error range due to timing math
        assertTrue(Math.abs(5 - alacDecoder.getCurrentTime()) < 0.05);
        alacDecoder.goToTime(2);
        // Error range due to timing math
        assertTrue(Math.abs(2 - alacDecoder.getCurrentTime()) < 0.05);
        assertFalse(alacDecoder.skipInProgress());
    }

    @Test // Test if decoding works
    public void decodeTest() {
        AudioDecoder wavDecoder = new WAV("data/scarlet.wav");
        assertFalse(alacDecoder.isReady());
        alacDecoder.prepareToPlayAudio();
        assertTrue(alacDecoder.isReady());
        AudioFormat format = alacDecoder.getAudioOutputFormat();
        assertEquals(2, format.getChannels());
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(44100, format.getSampleRate());
        alacDecoder.goToTime(1);
        wavDecoder.prepareToPlayAudio();
        wavDecoder.goToTime(alacDecoder.getCurrentTime());
        AudioSample sample = alacDecoder.getNextSample();
        AudioSample wavSample = wavDecoder.getNextSample();
        int wavOffset = 0;
        while (alacDecoder.moreSamples()) {
            // Different sample size fixing
            for (int i = 0; i < sample.getLength(); i++) {
                if (i + wavOffset == 4096) {
                    wavSample = wavDecoder.getNextSample();
                    wavOffset = -i; // this works, trust me
                }
                assertEquals(sample.getData()[i], wavSample.getData()[i + wavOffset]);
                if (i == wavOffset) {
                    new ForcePauseThread().start();
                }
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
        id3.setID3Data("Encoder", "Audiodex");
        alacDecoder.setID3(id3);
        alacDecoder.setArtwork(alacDecoder.getArtwork());
        // Error range due to math errors in scanning program
        assertTrue(Math.abs(-8.15 - alacDecoder.getReplayGain()) < 0.05);
    }
}
