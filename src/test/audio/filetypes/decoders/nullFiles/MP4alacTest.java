package audio.filetypes.decoders.nullFiles;

import audio.ID3Container;
import audio.filetypes.decoders.MP4alac;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MP4alacTest {
    MP4alac alacDecoder;

    @BeforeEach
    public void prepare() {
        alacDecoder = new MP4alac("data/\u0000/scarlet.alac.m4a");
        Thread.currentThread().setPriority(2);
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(alacDecoder.isReady());
        try {
            alacDecoder.prepareToPlayAudio();
            fail("That's not a valid path!");
        } catch (RuntimeException e) {
            // Well that worked
        }
        assertFalse(alacDecoder.isReady());
        ID3Container id3 = alacDecoder.getID3();
        assertNotEquals("Scarlet Fire", id3.getID3Data("Title"));
        assertNotEquals("Otis McDonald", id3.getID3Data("Artist"));
        assertNotEquals("YouTube Audio Library", id3.getID3Data("Album"));
        assertNotEquals(2015L, id3.getID3Data("Year"));
        id3.setID3Data("Encoder", "Audiodex");
        alacDecoder.setID3(id3);
        alacDecoder.setArtwork(alacDecoder.getArtwork());
    }
}
