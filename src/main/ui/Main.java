package ui;

import org.apache.commons.lang3.SystemUtils;

// Bootstrap for App class
public class Main {
    public static void main(String[] args) {
        Preloader.onLoad();
        App.startApp(args);
    }
}
