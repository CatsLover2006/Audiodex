package audio.filetypes.decoders.nullFiles;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import audio.filetypes.decoders.MP4AAC;
import audio.filetypes.decoders.WAV;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;

import java.nio.file.InvalidPathException;

import static org.junit.jupiter.api.Assertions.*;

public class MP4AACTest {
    AudioDecoder aacDecoder;

    @BeforeEach
    public void prepare() {
        aacDecoder = new MP4AAC("data/\u0000/scarlet.aac.m4a");
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(aacDecoder.isReady());
        aacDecoder.prepareToPlayAudio();
        assertFalse(aacDecoder.isReady());
        ID3Container id3 = null;
        try {
            id3 = aacDecoder.getID3();
            fail("Null Pointer Exception Where?");
        } catch (NullPointerException e) {
            // LOL
        }
        aacDecoder.setID3(id3);
        aacDecoder.setArtwork(aacDecoder.getArtwork());
    }
}
