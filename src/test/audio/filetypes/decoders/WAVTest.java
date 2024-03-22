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
public class  WAVTest {
    AudioDecoder wavDecoder;

    @BeforeEach
    public void prepare() {
        wavDecoder = new WAV("data/scarlet.wav");
        Thread.currentThread().setPriority(2);
    }

    @Test // Test if prepareToPlayAudio and closeAudioFile work
    public void fileManagementTest() {
        assertFalse(wavDecoder.isReady());
        wavDecoder.prepareToPlayAudio();
        assertTrue(wavDecoder.isReady());
        wavDecoder.closeAudioFile();
        assertFalse(wavDecoder.isReady());
        assertEquals("scarlet.wav", wavDecoder.getFileName());
        assertEquals(7, Math.floor(wavDecoder.getFileDuration()));
        assertEquals(AudioFileType.PCM_WAV, wavDecoder.getFileType());
    }

    @Test
    public void skipTest() {
        assertFalse(wavDecoder.isReady());
        wavDecoder.prepareToPlayAudio();
        assertTrue(wavDecoder.isReady());
        assertEquals(0, wavDecoder.getCurrentTime());
        wavDecoder.goToTime(5);
        assertEquals(5, wavDecoder.getCurrentTime());
        wavDecoder.goToTime(2);
        assertEquals(2, wavDecoder.getCurrentTime());
        wavDecoder.goToTime(10);
        assertFalse(wavDecoder.moreSamples());
        assertFalse(wavDecoder.skipInProgress());
    }

    @Test // Test if decoding works
    public void decodeTest() {
        assertFalse(wavDecoder.isReady());
        wavDecoder.prepareToPlayAudio();
        assertTrue(wavDecoder.isReady());
        AudioFormat format = wavDecoder.getAudioOutputFormat();
        assertEquals(2, format.getChannels());
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(44100, format.getSampleRate());
        AudioSample sample = wavDecoder.getNextSample();
        assertEquals(4096, sample.getLength());
        // It's impossible for me to check audio data,
        // I can't use a regular input stream on the file
    }

    @Test // Test ID3 data
    public void id3Test() {
        ID3Container id3 = wavDecoder.getID3();
        assertEquals("NO", id3.getID3Data("VBR"));
        wavDecoder.setID3(id3);
        wavDecoder.setArtwork(wavDecoder.getArtwork());
        assertEquals(-6, wavDecoder.getReplayGain());
    }
}
