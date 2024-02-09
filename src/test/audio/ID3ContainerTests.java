package audio;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ID3ContainerTests {
    ID3Container id3Test;

    @BeforeEach
    public void prepare() {
        id3Test = new ID3Container();
    }

    @Test
    public void setID3DataTest() {
        id3Test.setID3Data("Artist", "Hanabi");
        assertEquals("Hanabi", id3Test.getID3Data("Artist"));
        id3Test.setID3Data("Title", "Coding Adventures");
        assertEquals("Hanabi", id3Test.getID3Data("Artist"));
        assertEquals("Coding Adventures", id3Test.getID3Data("Title"));
        id3Test.setID3Data("Artist", "Littens4Life");
        assertEquals("Coding Adventures", id3Test.getID3Data("Title"));
        assertEquals("Littens4Life", id3Test.getID3Data("Artist"));
    }
}
