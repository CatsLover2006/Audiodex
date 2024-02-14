package audio;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ID3ContainerTest {
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

    @Test
    public void setID3LongTest() {
        id3Test.setID3Data("Artist", "Hanabi");
        id3Test.setID3Data("Title", "Coding Adventures");
        id3Test.setID3Long("Year", "2024");
        id3Test.setID3Long("Month", "February");
        assertEquals(2024L, id3Test.getID3Data("Year"));
        assertEquals("February", id3Test.getID3Data("Month"));
    }

    @Test
    public void toStringTest() {
        id3Test.setID3Data("Artist", "Hanabi");
        id3Test.setID3Data("Title", "Coding Adventures");
        assertEquals("Artist🜕Hanabi🜔Title🜕Coding Adventures", id3Test.toString());
    }

    @Test
    public void fromStringTest() {
        id3Test.setID3Data("Artist", "Hanabi");
        id3Test.setID3Data("Title", "Coding Adventures");
        assertEquals("Artist🜕Hanabi🜔Title🜕Coding Adventures", id3Test.toString());
        ID3Container fromString = ID3Container.fromString(id3Test.toString());
        assertEquals("Hanabi", fromString.getID3Data("Artist"));
        assertEquals("Coding Adventures", fromString.getID3Data("Title"));
    }
}
