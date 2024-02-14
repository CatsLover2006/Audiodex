package audio;

import java.util.HashMap;

// Container for ID3 data
public class ID3Container {
    private final HashMap<String, Object> id3data;

    public static final String[] knownKeys = {
            "Artist", "Title", "Album", "Track", "Year", "GenreInt",
            "GenreString", "Comment", "Lyrics", "Composer", "Tracks",
            "Publisher", "OriginalArtist", "AlbumArtist", "Copyright",
            "URL", "Encoder", "VBR", "Disc", "Discs", "PreviewText"
    }; // Known keys in ID3 data

    // Effects: creates an empty hashmap to place data
    public ID3Container() {
        id3data = new HashMap<>();
    }

    // Effects: gets the data at a specified key
    //          we don't know what kind of object it is,
    //          so we have to return a generic object.
    //          the only two object types used are
    //          Long and String, everything else is up
    //          to somebody else
    public Object getID3Data(String key) {
        return id3data.get(key);
    }

    // Modifies: this
    // Effects:  sets specified key to specified value
    public void setID3Data(String key, Object value) {
        if (value == null) {
            return;
        }
        if (id3data.containsKey(key)) {
            id3data.replace(key, value);
        } else {
            id3data.put(key, value);
        }
    }

    // Modifies: this
    // Effects:  sets specified key to specified long
    public void setID3Long(String key, String value) {
        if (value == null) {
            return;
        }
        try {
            setID3Data(key, Long.parseLong(value));
        } catch (NumberFormatException e) {
            setID3Data(key, value);
        }
    }

    @Override
    // Effects: encodes data into string
    //          yes I'm making one of these myself
    public String toString() {
        StringBuilder out = new StringBuilder();
        for (String k : id3data.keySet()) {
            if (id3data.get(k) == null || id3data.get(k).toString().isEmpty()
                    || id3data.get(k).toString().equals("null")) {
                continue;
            }
            out.append(k);
            out.append(RESERVED_CHARACTERS[1]); // Value separator
            out.append(id3data.get(k));
            out.append(RESERVED_CHARACTERS[0]); // Key separator
        }
        String outStr = out.toString();
        // Delete the last key separator
        return outStr.substring(0, outStr.length() - RESERVED_CHARACTERS[0].length());
    }

    // Requires: inputting valid input (from toString)
    // Effects:  returns a *VALID* ID3 container object
    //           with all ID3 data decoded from string
    public static ID3Container fromString(String data) {
        ID3Container base = new ID3Container();
        String[] keys = data.split(RESERVED_CHARACTERS[0]); // Key separator
        for (String key : keys) {
            String[] value = key.split(RESERVED_CHARACTERS[1]); // Value separator
            try { // We don't know if it's a number or not
                long integerVal = Long.parseLong(value[1]);
                base.setID3Data(value[0], integerVal);
            } catch (NumberFormatException e) {
                base.setID3Data(value[0], value[1]);
            } catch (IndexOutOfBoundsException e) {
                // LMAO its a null value
            }
        }
        return base;
    }

    public static final String[] RESERVED_CHARACTERS = {
            "🜔", // Key separator
            "🜕"  // Value separator
    };
}
