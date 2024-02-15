package audio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class AudioDataStructureTest {
    AudioDataStructure structure;

    private static final String[] knownID3Keys = {
            "Artist", "Title", "Album", "Track", "Year", "GenreInt",
            "GenreString", "Comment", "Lyrics", "Composer", "Tracks",
            "Publisher", "OriginalArtist", "AlbumArtist", "Copyright",
            "URL", "Encoder", "VBR", "Disc", "Discs", "PreviewText"
    }; // Known keys in ID3 data

    @BeforeEach
    public void prepare() {
        structure = new AudioDataStructure("./data/scarlet.mp3");
    }

    @Test
    public void toStringTest() {
        try {
            assertEquals("fn🜃" + (new File("")).getCanonicalPath()
                    + "/data/scarlet.mp3🜁t🜃MP3🜁id3🜃Artist🜕Otis McDonald🜔VBR🜕NO🜔" +
                    "Album🜕YouTube Audio Library🜔Title🜕Scarlet Fire🜔sampleRate🜕44100🜔GenreString🜕" +
                    "Hip Hop & Rap🜔Year🜕2015🜔bitRate🜕320🜁br🜃0🜁ss🜃16🜁fs🜃5865551", structure.toString());
        } catch (IOException e) {
            fail("IOException occured; test invalid");
        }
    }

    @Test
    public void fromStringTest() {
        try {
            assertEquals("fn🜃" + (new File("")).getCanonicalPath()
                    + "/data/scarlet.mp3🜁t🜃MP3🜁id3🜃Artist🜕Otis McDonald🜔VBR🜕NO🜔" +
                    "Album🜕YouTube Audio Library🜔Title🜕Scarlet Fire🜔sampleRate🜕44100🜔GenreString🜕" +
                    "Hip Hop & Rap🜔Year🜕2015🜔bitRate🜕320🜁br🜃0🜁ss🜃16🜁fs🜃5865551", structure.toString());
            AudioDataStructure fromString = AudioDataStructure.fromString(structure.toString());
            assertEquals(structure.getBitrate(), fromString.getBitrate());
            assertEquals(structure.getFilename(), fromString.getFilename());
            assertEquals(structure.getSampleSize(), fromString.getSampleSize());
            assertEquals(structure.getAudioFileType(), fromString.getAudioFileType());
            assertEquals(structure.getFileSize(), fromString.getFileSize());
            assertEquals(structure.getPlaybackString(), fromString.getPlaybackString());
            ID3Container structureID3 = structure.getId3Data();
            ID3Container fromStringID3 = fromString.getId3Data();
            for (String key : knownID3Keys) {
                if (fromStringID3.getID3Data(key) == null && structureID3.getID3Data(key) == null) {
                    assertTrue(true);
                    continue;
                }
                if (structureID3.getID3Data(key) == null) {
                    assertEquals("", fromStringID3.getID3Data(key));
                    continue;
                }
                if (fromStringID3.getID3Data(key) == null) {
                    assertEquals(structureID3.getID3Data(key), "");
                    continue;
                }
                assertEquals(structureID3.getID3Data(key), fromStringID3.getID3Data(key));
            }
        } catch (IOException e) {
            fail("IOException occured; test invalid");
        }
    }
}
