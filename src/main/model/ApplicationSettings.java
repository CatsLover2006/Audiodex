package model;

import org.json.simple.JSONObject;

// Class to store application settings
public class ApplicationSettings {
    private boolean soundCheck; // ReplayGain alias

    // Effects: initializes default settings
    ApplicationSettings() {
        soundCheck = false;
    }

    // Effects: loads settings from JSONObject
    ApplicationSettings(JSONObject obj) {
        soundCheck = (boolean) obj.get("soundCheck");
    }

    // Effects: getSoundCheck();
    public boolean doSoundCheck() {
        return soundCheck;
    }

    // Modifies: this
    // Effects:  toggles sound check;
    public void toggleSoundCheck() {
        soundCheck = !soundCheck;
    }

    // Effects: returns JSONObject, which can be
    // loaded by ApplicationSettings(JSONObject);
    public JSONObject encode() {
        JSONObject out = new JSONObject();
        out.put("soundCheck", soundCheck);
        return out;
    }
}
