package audio.filetypes.decoders.nullFiles;

import audio.AudioDecoder;
import audio.ID3Container;
import audio.filetypes.decoders.MP4AAC;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(0)
public class MP4AACTest {
    AudioDecoder aacDecoder;

    @BeforeEach
    public void prepare() {
        aacDecoder = new MP4AAC("data/\u0000/scarlet.aac.m4a");
        Thread.currentThread().setPriority(2);
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(aacDecoder.isReady());
        try {
            aacDecoder.prepareToPlayAudio();
            fail("That's not a valid path!");
        } catch (RuntimeException e) {
            // Well that worked
        }
        assertFalse(aacDecoder.isReady());
        ID3Container id3 = aacDecoder.getID3();
        assertNotEquals("Scarlet Fire", id3.getID3Data("Title"));
        assertNotEquals("Otis McDonald", id3.getID3Data("Artist"));
        assertNotEquals("YouTube Audio Library", id3.getID3Data("Album"));
        assertNotEquals(2015L, id3.getID3Data("Year"));
        id3.setID3Data("Encoder", "Audiodex");
        aacDecoder.setID3(id3);
        aacDecoder.setArtwork(aacDecoder.getArtwork());
    }
}
