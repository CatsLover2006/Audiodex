package audio.filetypes.decoders.nullFiles;

import audio.AudioDecoder;
import audio.ID3Container;
import audio.filetypes.decoders.Aiff;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(0)
public class AiffTest {
    AudioDecoder aiffDecoder;

    @BeforeEach
    public void prepare() {
        aiffDecoder = new Aiff("data/\u0000/scarlet.aif");
        Thread.currentThread().setPriority(2);
    }

    @Test // Test ID3 data
    public void id3Test() {
        assertFalse(aiffDecoder.isReady());
        try {
            aiffDecoder.prepareToPlayAudio();
            fail("That's not a valid path!");
        } catch (RuntimeException e) {
            // Well that worked
        }
        assertFalse(aiffDecoder.isReady());
        ID3Container id3 = aiffDecoder.getID3();
        assertNotEquals("Scarlet Fire", id3.getID3Data("Title"));
        assertNotEquals("Otis McDonald", id3.getID3Data("Artist"));
        assertNotEquals("YouTube Audio Library", id3.getID3Data("Album"));
        assertNotEquals(2015L, id3.getID3Data("Year"));
        id3.setID3Data("Encoder", "Audiodex");
        aiffDecoder.setID3(id3);
        aiffDecoder.setArtwork(aiffDecoder.getArtwork());
    }
}
