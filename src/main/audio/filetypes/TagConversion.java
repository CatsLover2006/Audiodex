package audio.filetypes;

import org.jaudiotagger.tag.FieldKey;

import java.util.HashMap;

public class TagConversion {

    public static final HashMap<FieldKey, String> keyConv;

    static {
        keyConv = new HashMap<>();
        keyConv.put(FieldKey.ARTIST, "Artist");
        keyConv.put(FieldKey.ALBUM, "Album");
        keyConv.put(FieldKey.ALBUM_ARTIST, "AlbumArtist");
        keyConv.put(FieldKey.TITLE, "Title");
        keyConv.put(FieldKey.TRACK, "Track");
        keyConv.put(FieldKey.TRACK_TOTAL, "Tracks");
        keyConv.put(FieldKey.DISC_NO, "Disc");
        keyConv.put(FieldKey.DISC_TOTAL, "Discs");
        keyConv.put(FieldKey.YEAR, "Year");
        keyConv.put(FieldKey.GENRE, "GenreString");
        keyConv.put(FieldKey.COMMENT, "Comment");
        keyConv.put(FieldKey.LYRICS, "Lyrics");
        keyConv.put(FieldKey.COMPOSER, "Composer");
        keyConv.put(FieldKey.RECORD_LABEL, "Publisher");
        keyConv.put(FieldKey.COPYRIGHT, "Copyright");
        keyConv.put(FieldKey.ENCODER, "Encoder");
    }
}
