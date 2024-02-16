package audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ID3ContainerTest {
    @Test
    public void id3Test() {
        ID3Container container = new ID3Container();
        container.setID3Data("Title", "ID3 Test");
        container.setID3Data("Disc", 1);
        container.setID3Data("Discs", 420);
        container.setID3Long("Artist", "Hanabi"); // Will set as string
        container.setID3Long("Year", "2024"); // Will set as long
        assertEquals(2024L, container.getID3Data("Year"));
        assertEquals(1L, container.getID3Data("Disc"));
        assertEquals(420L, container.getID3Data("Discs"));
        assertEquals("Hanabi", container.getID3Data("Artist"));
        assertEquals("ID3 Test", container.getID3Data("Title"));
        ID3Container nu = new ID3Container(container.encode());
        assertEquals(2024L, nu.getID3Data("Year"));
        assertEquals(1L, nu.getID3Data("Disc"));
        assertEquals(420L, nu.getID3Data("Discs"));
        assertEquals("Hanabi", nu.getID3Data("Artist"));
        assertEquals("ID3 Test", nu.getID3Data("Title"));
    }
}
