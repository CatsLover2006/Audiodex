package audio.filetypes.decoders.nullFiles;

import audio.AudioDecoder;
import audio.ID3Container;
import audio.filetypes.decoders.WAV;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(0)
public class WAVTest {
    AudioDecoder wavDecoder;

    @BeforeEach
    public void prepare() {
        wavDecoder = new WAV("data/\u0000/scarlet.wav");
        Thread.currentThread().setPriority(2);
    }

    @Test // Test ID3 data
    public void id3Test() {
        ID3Container id3 = wavDecoder.getID3();
        assertEquals("NO", id3.getID3Data("VBR"));
        wavDecoder.setID3(id3);
        wavDecoder.setArtwork(wavDecoder.getArtwork());
    }
}
