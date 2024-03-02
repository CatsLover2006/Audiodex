package audio.filetypes.decoders.nullFiles;

import audio.ID3Container;
import audio.filetypes.decoders.Vorbis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VorbisTest {
    Vorbis vorbisDecoder;

    @BeforeEach
    public void prepare() {
        vorbisDecoder = new Vorbis("data/\u0000/scarlet.vorbis.ogg");
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(vorbisDecoder.isReady());
        try {
            vorbisDecoder.prepareToPlayAudio();
            fail("That's not a valid path!");
        } catch (RuntimeException e) {
            // Well that worked
        }
        assertFalse(vorbisDecoder.isReady());
        ID3Container id3 = vorbisDecoder.getID3();
        assertNotEquals("Scarlet Fire", id3.getID3Data("Title"));
        assertNotEquals("Otis McDonald", id3.getID3Data("Artist"));
        assertNotEquals("YouTube Audio Library", id3.getID3Data("Album"));
        assertNotEquals(2015L, id3.getID3Data("Year"));
        id3.setID3Data("Encoder", "Audiodex");
        vorbisDecoder.setID3(id3);
        vorbisDecoder.setArtwork(vorbisDecoder.getArtwork());
    }
}
