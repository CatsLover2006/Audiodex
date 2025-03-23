package model;

import org.json.JSONObject;

import java.util.Objects;

// Class to store application settings
public class ApplicationSettings {
    private boolean soundCheck; // ReplayGain alias
    private boolean saveOnClose;
    private boolean saveOnImport;

    // Effects: initializes default settings
    ApplicationSettings() {
        soundCheck = false;
        saveOnClose = true;
        saveOnImport = true;
    }

    // Effects: loads settings from JSONObject
    ApplicationSettings(JSONObject obj) {
        if (obj.has("soundCheck")) soundCheck = (boolean) obj.get("soundCheck");
        else soundCheck = false;
        if (obj.has("saveOnClose")) saveOnClose = (boolean) obj.get("saveOnClose");
        else saveOnClose = true;
        if (obj.has("saveOnImport")) saveOnImport = (boolean) obj.get("saveOnImport");
        else saveOnImport = true;
    }

    // Effects: getSoundCheck();
    public boolean doSoundCheck() {
        return soundCheck;
    }

    // Modifies: this
    // Effects:  toggles sound check
    public void toggleSoundCheck() {
        soundCheck = !soundCheck;
    }
    
    // Effects: getSaveOnClose();
    public boolean doSaveOnClose() {
        return saveOnClose;
    }
    
    // Modifies: this
    // Effects:  toggles save on close
    public void toggleSaveOnClose() {
        saveOnClose = !saveOnClose;
    }
    
    // Effects: getSaveOnImport();
    public boolean doSaveOnImport() {
        return saveOnImport;
    }
    
    // Modifies: this
    // Effects:  toggles save on import
    public void toggleSaveOnImport() {
        saveOnImport = !saveOnImport;
    }

    // Effects: returns JSONObject, which can be
    // loaded by ApplicationSettings(JSONObject);
    public JSONObject encode() {
        JSONObject out = new JSONObject();
        out.put("soundCheck", soundCheck);
        out.put("saveOnClose", saveOnClose);
        out.put("saveOnImport", saveOnImport);
        return out;
    }

    // Effects: generates a hash code using all settings
    @Override
    public int hashCode() {
        return (soundCheck ? 1 : 0) + (saveOnClose ? 2 : 0) + (saveOnImport ? 4 : 0);
    }
}
