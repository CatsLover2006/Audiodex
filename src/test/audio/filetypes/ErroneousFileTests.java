package audio.filetypes;

import audio.AudioDecoder;
import audio.ID3Container;
import audio.filetypes.decoders.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ErroneousFileTests {
    @Test
    public void FlacAsVorbisTest() {
        try {
            AudioDecoder flacAsVorbis = new Vorbis("data/scarlet.flac.oga");
            flacAsVorbis.prepareToPlayAudio();
            fail("Wrong filetype");
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void WeirdMP3Test() {
        AudioDecoder funniMP3 = new MpegType("data/scarlet.bad.mp3");
        funniMP3.prepareToPlayAudio();
        ID3Container container = funniMP3.getID3();
        assertEquals(null, container.getID3Data("Album"));
        assertTrue(Math.abs(-8.15 - funniMP3.getReplayGain()) < 0.05);
    }

    @Test
    public void NullGainTest() {
        assertEquals(-6, TagConversion.getReplayGain(null));
    }

    @Test
    public void IncorrectFileTypeTest() {
        AudioDecoder decoder = null;
        try {
            decoder = new Aiff("data/scarlet.mp3");
            decoder.prepareToPlayAudio();
            fail("Wrong filetype");
        } catch (Exception e) {
            assertFalse(decoder.isReady());
        }
        try {
            decoder = new WAV("data/scarlet.mp3");
            decoder.prepareToPlayAudio();
            fail("Wrong filetype");
        } catch (Exception e) {
            assertFalse(decoder.isReady());
        }
        try {
            decoder = new MP4AAC("data/scarlet.mp3");
            decoder.prepareToPlayAudio();
            fail("Wrong filetype");
        } catch (Exception e) {
            assertFalse(decoder.isReady());
        }
        try {
            decoder = new MP4alac("data/scarlet.mp3");
            decoder.prepareToPlayAudio();
            fail("Wrong filetype");
        } catch (Exception e) {
            assertFalse(decoder.isReady());
        }
        try {
            decoder = new Flac("data/scarlet.mp3");
            decoder.prepareToPlayAudio();
            fail("Wrong filetype");
        } catch (Exception e) {
            assertFalse(decoder.isReady());
        }
        try {
            decoder = new Vorbis("data/scarlet.mp3");
            decoder.prepareToPlayAudio();
            fail("Wrong filetype");
        } catch (Exception e) {
            assertFalse(decoder.isReady());
        }
        try {
            decoder = new MpegType("data/scarlet.aac.m4a");
            decoder.prepareToPlayAudio();
            fail("Wrong filetype");
        } catch (Exception e) {
            assertFalse(decoder.isReady());
        }
    }
}
