package ui;

import org.apache.commons.lang3.SystemUtils;

// Preloader for App class (which instantly initializes Swing, that causes issues)
public class Preloader {
    public static void onLoad() {
        if (SystemUtils.IS_OS_MAC_OSX) {
            System.setProperty("apple.awt.application.appearance", "system");
            System.setProperty("apple.awt.application.name", "Audiodex");
        }
    }
}
