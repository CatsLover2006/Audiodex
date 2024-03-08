package audio.filetypes.decoders.nullFiles;

import audio.AudioDecoder;
import audio.ID3Container;
import audio.filetypes.decoders.MpegType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MpegTypeTest {
    AudioDecoder mp3Decoder;

    @BeforeEach
    public void prepare() {
        mp3Decoder = new MpegType("data/\u0000/scarlet.mp3");
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(mp3Decoder.isReady());
        try {
            mp3Decoder.prepareToPlayAudio();
            fail("That's not a valid path!");
        } catch (RuntimeException e) {
            // Well that worked
        }
        assertFalse(mp3Decoder.isReady());
        ID3Container id3 = mp3Decoder.getID3();
        assertNull(id3);
        id3 = new ID3Container();
        id3.setID3Data("Encoder", "Audiodex");
        mp3Decoder.setID3(id3);
        mp3Decoder.setArtwork(mp3Decoder.getArtwork());
    }
}
