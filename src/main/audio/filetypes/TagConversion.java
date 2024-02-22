package audio.filetypes;

import org.jaudiotagger.tag.FieldKey;

import java.util.HashMap;
import java.util.Map;

// Static class to assist with tag conversion
public class TagConversion {
    // Effects: returns true if arr contains item
    private static boolean arrContains(FieldKey[] arr, FieldKey item) {
        for (FieldKey arrI : arr) {
            if (arrI == item) {
                return true;
            }
        }
        return false;
    }

    public static final HashMap<FieldKey, String> keyConv;
    public static final HashMap<String, FieldKey> valConv;

    static {
        FieldKey[] ignoreList = {
                FieldKey.COPYRIGHT
        }; // Don't overwrite these
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
        keyConv.put(FieldKey.PRODUCER, "Producer");
        keyConv.put(FieldKey.BPM, "BPM");
        keyConv.put(FieldKey.FBPM, "FloatingBPM");
        keyConv.put(FieldKey.RATING, "Rating");
        keyConv.put(FieldKey.ARRANGER, "Arranger");
        keyConv.put(FieldKey.IS_COMPILATION, "IsCompilation");
        keyConv.put(FieldKey.ARRANGER_SORT, "Arranger-Sort");
        keyConv.put(FieldKey.TITLE_SORT, "Title-Sort");
        keyConv.put(FieldKey.ARTIST_SORT, "Artist-Sort");
        keyConv.put(FieldKey.ALBUM_SORT, "Album-Sort");
        keyConv.put(FieldKey.ALBUM_ARTIST_SORT, "AlbumArtist-Sort");
        valConv = new HashMap<>();
        for (Map.Entry<FieldKey, String> entry : keyConv.entrySet()) {
            if (!arrContains(ignoreList, entry.getKey())) {
                valConv.put(entry.getValue(), entry.getKey());
            }
        }
    }
}
