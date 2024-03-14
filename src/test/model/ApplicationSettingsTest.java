package model;

import org.json.JSONObject;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class ApplicationSettingsTest {
    ApplicationSettings settings;

    @BeforeEach
    public void preTest() {
        settings = new ApplicationSettings();
    }

    @Test
    public void defaultSettingsTest() {
        assertFalse(settings.doSoundCheck());
    }

    @Test
    public void setSettingsTest() {
        settings.toggleSoundCheck();
        assertTrue(settings.doSoundCheck());
        settings.toggleSoundCheck();
        assertFalse(settings.doSoundCheck());
    }

    @Test
    public void encodeDecodeTest() {
        JSONObject settingsObj = settings.encode();
        assertEquals(settingsObj.get("soundCheck"), settings.doSoundCheck());
    }

    @Test
    public void hashCodeTest() {
        int preHash = settings.hashCode();
        settings.toggleSoundCheck();
        assertNotEquals(preHash, settings.hashCode());
    }
}
