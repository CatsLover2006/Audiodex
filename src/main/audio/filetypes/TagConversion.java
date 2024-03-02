package audio.filetypes;

import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.ID3v24Frames;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Static class to assist with tag conversion
public class TagConversion {
    // Effects: returns true if arr contains item
    private static boolean arrContains(Object[] arr, Object item) {
        return Arrays.stream(arr).anyMatch(arrI -> arrI.equals(item));
    }

    public static final HashMap<FieldKey, String> keyConv;
    public static final HashMap<String, FieldKey> valConv;
    public static final HashMap<String, String> id3v2keyConv;
    public static final HashMap<String, String> id3v2valConv;

    // Initalizes values since you can't do that at compile time for a hashmap
    static {
        FieldKey[] ignoreList = {
                FieldKey.COPYRIGHT
        }; // Don't overwrite these
        String[] id3v2ignoreList = {
                ID3v24Frames.FRAME_ID_COPYRIGHTINFO
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
        id3v2keyConv = new HashMap<>();
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_ARTIST, "Artist");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_ALBUM, "Album");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_ACCOMPANIMENT, "AlbumArtist");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_TITLE, "Title");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_TRACK, "Track");
        // id3v2keyConv.put(null, "Tracks"); // Field doesn't exist
        id3v2keyConv.put("TPOS", "Disc");
        // id3v2keyConv.put(null, "Discs"); // Field doesn't exist
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_YEAR, "Year");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_GENRE, "GenreString");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_COMMENT, "Comment");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_UNSYNC_LYRICS, "Lyrics");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_COMPOSER, "Composer");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_PUBLISHER, "Publisher");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_COPYRIGHTINFO, "Copyright");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_ENCODEDBY, "Encoder");
        // id3v2keyConv.put(null, "Producer"); // Field doesn't exist
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_BPM, "BPM");
        // id3v2keyConv.put(null, "FloatingBPM"); // Field doesn't exist
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_POPULARIMETER, "Rating");
        // id3v2keyConv.put(null, "Arranger"); // Field doesn't exist
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_IS_COMPILATION, "IsCompilation");
        // id3v2keyConv.put(null, "Arranger-Sort"); // Field doesn't exist
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_TITLE_SORT_ORDER, "Title-Sort");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_ARTIST_SORT_ORDER, "Artist-Sort");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_ALBUM_SORT_ORDER, "Album-Sort");
        id3v2keyConv.put(ID3v24Frames.FRAME_ID_ALBUM_ARTIST_SORT_ORDER_ITUNES, "AlbumArtist-Sort");
        valConv = new HashMap<>();
        for (Map.Entry<FieldKey, String> entry : keyConv.entrySet()) {
            if (!arrContains(ignoreList, entry.getKey())) {
                valConv.put(entry.getValue(), entry.getKey());
            }
        }
        id3v2valConv = new HashMap<>();
        for (Map.Entry<String, String> entry : id3v2keyConv.entrySet()) {
            if (!arrContains(ignoreList, entry.getKey())) {
                id3v2valConv.put(entry.getValue(), entry.getKey());
            }
        }
    }

    // Effects: gets replaygain of tag
    //          returns -6 as default
    public static float getReplayGain(Tag tag) {
        Pattern gainPattern = Pattern.compile("-?[.\\d]+");
        Iterator<TagField> fields = tag.getFields();
        float albumGain = -6;
        try {
            while (fields.hasNext()) {
                TagField field = fields.next();
                if ((field.getId() + field.toString()).contains("replaygain_track_gain")) {
                    Matcher m = gainPattern.matcher(field.toString());
                    if (m.find()) {
                        return Float.parseFloat(m.group());
                    }
                }
                if ((field.getId() + field.toString()).contains("replaygain_album_gain")) {
                    Matcher m = gainPattern.matcher(field.toString());
                    if (m.find()) {
                        albumGain = Float.parseFloat(m.group());
                    }
                }
            }
        } catch (Exception e) {
            // LMAO
        }
        return albumGain;
    }
}
