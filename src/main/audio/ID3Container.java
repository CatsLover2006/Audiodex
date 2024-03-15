package audio;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// Container for ID3 data
public class ID3Container {
    private final Map<String, Object> id3data;

    // Effects: creates an empty JSON object to place data
    public ID3Container() {
        id3data = new HashMap<>();
    }

    // Effects: use ds an existing JSON object
    public ID3Container(JSONObject obj) {
        id3data = new HashMap<>();
        id3data.putAll(obj.toMap());
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
    //           specific definition for long fixes issues with integers
    public void setID3Data(String key, long value) {
        id3data.put(key, value);
    }

    // Modifies: this
    // Effects:  sets specified key to specified value
    public void setID3Data(String key, Object value) {
        if (value == null || value.toString().isEmpty()) {
            return;
        }
        id3data.put(key, value);
    }

    // Modifies: this
    // Effects:  sets specified key to specified long (parsed from String)
    public void setID3Long(String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        try {
            id3data.put(key, Long.parseLong(value));
        } catch (NumberFormatException e) {
            id3data.put(key, value);
        }
    }

    // Effects: encodes data into string
    //          yes I'm making one of these myself
    public JSONObject encode() {
        return new JSONObject(id3data);
    }

    // Effects: Gets hashcode
    @Override
    public int hashCode() {
        return Objects.hash(id3data.hashCode(), id3data.size());
    }

    // Effects: compares hashcodes as there's too much else to compare
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ID3Container) {
            return obj.hashCode() == hashCode();
        }
        return false;
    }
}
