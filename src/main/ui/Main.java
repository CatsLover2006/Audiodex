package ui;

import audio.AudioDataStructure;
import audio.AudioFilePlaybackBackend;

import java.io.File;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

import audio.ID3Container;
import model.AudioFileList;
import org.fusesource.jansi.*;

import static java.lang.Math.floor;
import static java.lang.Thread.*;

public class Main {
    private static ID3Container id3;
    private static AudioFilePlaybackBackend playbackManager;
    private static boolean USE_CLI = true;
    private static boolean end = false;
    private static String filename = "";
    private static AudioFileList database;


    // This is probably the lowest-level I still consider UI-level;
    // we're still not interacting with actual data on the filesystem
    // and this program basically is a file management program
    private static List<AudioDataStructure> songQueue;

    // Effects: returns true if arr contains item, false otherwise
    //          this function is null-safe
    private static boolean strArrContains(String[] arr, String item) {
        for (String cur : arr) {
            if (cur.equals(item)) {
                return true;
            }
        }
        return false;
    }

    // Modifies: this
    // Effects:  Checks when song is finished and redraws screen if necessary.
    public static void finishedSong() {
        if (USE_CLI) {
            Cli.finishedSong();
        } else {
            if (!songQueue.isEmpty()) {
                // Run the playDbFile() function for
            }
        }
    }

    public static void main(String[] args) {
        songQueue = new ArrayList<>();
        database = new AudioFileList();
        database.loadDatabase();
        playbackManager = new AudioFilePlaybackBackend();
        if (USE_CLI) {
            Cli.main(args);
        }
    }

    // Modifies: audio playback manager
    // Effects:  toggles pause/play status
    private static void togglePlayback() {
        if (playbackManager.paused()) {
            playbackManager.playAudio();
        } else {
            playbackManager.pauseAudio();
        }
    }

    // public interface to the private CLI class
    public static class CliInterface {
        public static void updatePlaybackStatus() {
            Cli.doPlaybackStatusWrite();
        }

        public static void println(Object in) {
            AnsiConsole.out().println(in);
        }
    }

    // CLI mode
    private static class Cli {
        private enum MenuState {
            CLI_MAINMENU,
            CLI_BROWSEMENU,
            CLI_OTHER
        }

        private static MenuState state = MenuState.CLI_OTHER;

        private static PlaybackThread visualizerThread;

        // No other class needs to know this
        // class for the thread which handles the playback indicator
        // in CLI mode
        private static class PlaybackThread extends Thread {
            private boolean run = true;

            // Modifies: this
            // Effects:  ends thread
            public void killThread() {
                run = false;
                safeJoin();
            }

            // Effects: join() but no try-catch
            public void safeJoin() {
                try {
                    join();
                } catch (InterruptedException e) {
                    // lol
                }
            }

            // Effects: join(long millis) but no try-catch
            public void safeJoin(long millis) {
                try {
                    join(millis);
                } catch (InterruptedException e) {
                    // lol
                }
            }

            // Effects: join(long millis, int nanos) but no try-catch
            public void safeJoin(long millis, int nanos) {
                try {
                    join(millis, nanos);
                } catch (InterruptedException e) {
                    // lol
                }
            }

            // Effects: plays audio in file loadedFile
            public void run() {
                if (AnsiConsole.getTerminalWidth() == 0) {
                    return;
                }
                while (run) {
                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (playbackManager == null) {
                        return;
                    }
                    Cli.doPlaybackStatusWrite();
                }
            }
        }

        // main function for the CLI
        private static void main(String[] args) {
            AnsiConsole.systemInstall();
            if (AnsiConsole.getTerminalWidth() == 0) {
                AnsiConsole.out().println("This application works better in a regular java console.");
            }
            visualizerThread = new PlaybackThread();
            visualizerThread.start();
            Scanner inputScanner = new Scanner(System.in);
            while (true) {
                state = MenuState.CLI_MAINMENU;
                printMenu();
                doPlaybackStatusWrite();
                cliMain(inputScanner, inputScanner.nextLine());
                if (end) {
                    AnsiConsole.out().println("Goodbye!");
                    return;
                }
            }
        }

        // Modifies: this
        // Effects:  finishedSong() extension for CLI
        private static void finishedSong() {
            if (!songQueue.isEmpty()) {
                playDbFile(songQueue.get(0));
                songQueue.remove(0);
            }
            switch (Cli.state) {
                case CLI_BROWSEMENU: {
                    Cli.printBrowseMenu();
                    break;
                }
                case CLI_MAINMENU: {
                    Cli.printMenu();
                    break;
                }
            }
            Cli.doPlaybackStatusWrite();
        }

        @SuppressWarnings("methodlength") // Large switch/case
        // CLI switch manager
        // calls corresponding functions for different actions
        private static void cliMain(Scanner inputScanner, String selected) {
            state = MenuState.CLI_OTHER;
            switch (selected.toLowerCase()) {
                case "1": {
                    playFile(inputScanner);
                    break;
                }
                case "2": {
                    addDatabaseFile(inputScanner);
                    break;
                }
                case "3": {
                    addDatabaseDir(inputScanner);
                    break;
                }
                case "4": {
                    browseMenu(inputScanner);
                    break;
                }
                case "5": {
                    database.saveDatabaseFile();
                    break;
                }
                case "c": {
                    playbackManager.playAudio();
                    break;
                }
                case "p": {
                    playbackManager.pauseAudio();
                    break;
                }
                case "m": {
                    dbMenu(inputScanner);
                    break;
                }
                case "s": {
                    seekAudio(inputScanner);
                    break;
                }
                case "q": {
                    cleanup();
                    return;
                }
                case "d": { // Database uses absolute file paths, otherwise it would fail to load audio
                    database.addFileToDatabase((new File(filename)).getAbsolutePath());
                    return;
                }
                /*case "}": {
                    debug(inputScanner);
                    return;
                }//*///
                default: {
                    unknownCommandError();
                }
            }
        }

        /*private static void debug(Scanner scanner) {
            AnsiConsole.out().println(Ansi.ansi().fgBrightGreen() + "Debugging now!");
            AnsiConsole.out().println("AudioDataStructure toString Data:");
            AudioDataStructure ads = new AudioDataStructure(filename);
            String str = ads.toString();
            AnsiConsole.out().println(str);
            AudioDataStructure adsFrom = AudioDataStructure.fromString(str);
            AnsiConsole.out().println("AudioDataStructure fromString Decode Data:");
            AnsiConsole.out().println(adsFrom.toString());
            AnsiConsole.out().print(Ansi.ansi().fgDefault());
        }//*/// Debugging

        private static void addDatabaseFile(Scanner scanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please enter the filename:");
            String filenameIn = scanner.nextLine();
            // Fix whitespace errors
            String filename = filenameIn.trim();
            // Check if file exists
            File f = new File(filename);
            if (f.isFile()) { // Database uses absolute file paths, otherwise it would fail to load audio
                database.addFileToDatabase(f.getAbsolutePath());
            } else {
                AnsiConsole.out().println("File doesn't exist, is a directory, or is inaccessible.");
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    // Why?
                }
            }
        }

        private static void addDatabaseDir(Scanner scanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please enter the directory name:");
            String filenameIn = scanner.nextLine();
            // Fix whitespace errors
            String filename = filenameIn.trim();
            // Check if file exists
            File f = new File(filename);
            if (f.isDirectory()) { // Database uses absolute file paths, otherwise it would fail to load audio
                database.addDirToDatabase(f.getAbsolutePath());
            } else {
                AnsiConsole.out().println("Directory doesn't exist, is a file, or is inaccessible.");
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    // Why?
                }
            }
        }

        // Effects: Prints the error for an unknown command
        private static void unknownCommandError() {
            AnsiConsole.out().println("Invalid option.");
            AnsiConsole.out().println("Either you mistyped something, or it hasn't been implemented yet.");
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                // This is main thread
            }
        }

        // Modifies: this, playbackManager
        // Effects:  Cleans up extra threads
        private static void cleanup() {
            visualizerThread.killThread();
            visualizerThread.interrupt();
            playbackManager.cleanBackend();
            visualizerThread = null;
            playbackManager = null;
            end = true;
        }

        // Modifies: playbackManager and its decoding thread
        // Effects: plays a file
        private static void playFile(Scanner inputScanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please enter the filename:");
            doPlaybackStatusWrite();
            // Fix whitespace errors
            filename = inputScanner.nextLine().trim();
            // Check if file exists
            File f = new File(filename);
            if (f.isFile()) {
                songQueue.clear();
                visualizerThread.killThread();
                visualizerThread.safeJoin();
                playbackManager.loadAudio(f.getAbsolutePath());
                playbackManager.startAudioDecoderThread();
                playbackManager.playAudio();
                id3 = playbackManager.getID3();
                visualizerThread = new PlaybackThread();
                visualizerThread.start();
            } else {
                AnsiConsole.out().println("File doesn't exist, is a directory, or is inaccessible.");
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    // Why?
                }
            }
        }

        // Effects: returns formatted time
        private static String formatTime(long seconds) {
            if (seconds == -1) {
                return "X:XX";
            }
            return String.format("%01d:%02d", seconds / 60, seconds % 60);
        }

        // Effects: runs writePlaybackState() will null catching
        //          also doubles as checking against IntelliJ console
        private static void doPlaybackStatusWrite() {
            if (AnsiConsole.getTerminalWidth() == 0) {
                String w = formatTime((long) floor(playbackManager.getCurrentTime())) + " of "
                        + formatTime((long) floor(playbackManager.getFileDuration()));
                if (playbackManager.paused()) {
                    AnsiConsole.out().print(Ansi.ansi().fgBrightRed().toString() + "PAUSED"
                            + Ansi.ansi().fgDefault().toString() + "; ");
                }
                AnsiConsole.out().println(w);
                return;
            }
            try {
                writePlaybackState();
            } catch (NullPointerException e) {
                // Lol no
            }
        }

        // Modifies: Console
        // Effects:  updates the audio status bar
        //           writes the entire status bar in one single print statement,
        //           which fixes a race condition with the KEYBOARD
        private static void writePlaybackState() {
            double time = playbackManager.getPercentPlayed();
            int w = AnsiConsole.getTerminalWidth() - 2;
            String burstWrite = formatTime((long) floor(playbackManager.getCurrentTime())) + " [";
            if (playbackManager.paused()) {
                burstWrite = Ansi.ansi().fgBrightRed().toString() + "PAUSED"
                        + Ansi.ansi().fgBrightMagenta().toString() + " " + burstWrite;
                w += Ansi.ansi().fgBrightRed().toString().length() + Ansi.ansi().fgBrightMagenta().toString().length();
            }
            String fileDuration = formatTime((long) floor(playbackManager.getFileDuration()));
            w -= burstWrite.length() + fileDuration.length();
            burstWrite += getPlaybackBar(time, w);
            AnsiConsole.out().print(Ansi.ansi().saveCursorPosition().toString()
                    + Ansi.ansi().fgBrightMagenta().toString() + Ansi.ansi().cursor(1, 1).toString()
                    + burstWrite + "] " + fileDuration + Ansi.ansi().restoreCursorPosition().toString()
                    + Ansi.ansi().fgDefault().toString());
        }

        // Effects: gets the playback bar for writePlaybackState()
        private static String getPlaybackBar(double time, int w) {
            StringBuilder burstWrite = new StringBuilder();
            for (int i = 0; i < w; i++) {
                if (i < floor(time * w)) {
                    burstWrite.append("#");
                } else if (i < Math.round(time * w)) {
                    burstWrite.append("=");
                } else {
                    burstWrite.append("-");
                }
            }
            return burstWrite.toString();
        }

        // Modifies: loaded file container through playbackManager
        // Effects:  seeks to location in song
        private static void seekAudio(Scanner inputScanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please input the time (in seconds) you'd like to go to.");
            while (true) {
                try {
                    playbackManager.seekTo(inputScanner.nextDouble());
                    inputScanner.nextLine(); // Bugfix (fixes duplicate menu)
                    return;
                } catch (InputMismatchException e) {
                    if (inputScanner.nextLine().equalsIgnoreCase("q")) {
                        return;
                    } else {
                        AnsiConsole.out().print(Ansi.ansi().cursorUpLine());
                        AnsiConsole.out().println("That's not a time; enter \"Q\" to leave this menu.");
                    }
                }
            }
        }

        // Effects: prints main menu
        private static void printMenu() {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("What would you like to do?");
            AnsiConsole.out().println("1. Play a file");
            AnsiConsole.out().println("2. Add file to database");
            AnsiConsole.out().println("3. Scan directory into database");
            AnsiConsole.out().println("4. Browse database");
            AnsiConsole.out().println("5. Save database");
            AnsiConsole.out().println("Q. Exit");
            AnsiConsole.out().println("M. Enter database backup manager");
            if (playbackManager.audioIsLoaded()) {
                horizonalBar();
                AnsiConsole.out().println(Ansi.ansi().fgBrightCyan().toString() + "Now Playing: "
                        + playbackManager.getPlaybackString() + Ansi.ansi().fgDefault().toString());
                AnsiConsole.out().println("P. Pause");
                AnsiConsole.out().println("C. Play");
                AnsiConsole.out().println("S. Seek");
                AnsiConsole.out().println("D. Add playing file to database");
            }
        }

        private static int songID = 0;

        // Modifies: this
        // Effects:  browses menu (duh)
        private static void browseMenu(Scanner inputScanner) {
            state = MenuState.CLI_BROWSEMENU;
            String selected;
            do {
                if (!songQueue.isEmpty() && !playbackManager.audioIsLoaded()) {
                    playDbFile(songQueue.get(0));
                    songQueue.remove(0);
                }
                if (songID < 0) {
                    songID += database.listSize();
                }
                if (songID >= database.listSize()) {
                    songID -= database.listSize();
                }
                printBrowseMenu();
                selected = inputScanner.nextLine();
                int l = browseSwitch(selected, songID);
                if (l == 7000) {
                    return;
                }
                songID += l;
            } while (!selected.equalsIgnoreCase("q"));
        }

        @SuppressWarnings("methodlength") // Large switch/case
        // Modifies: this
        // Effects:  handles the switch case for browseMenu()
        private static int browseSwitch(String in, int idx) {
            switch (in.toLowerCase()) {
                case "1": {
                    return -1;
                }
                case "2": {
                    return 1;
                }
                case "p": {
                    playDbFile(database.get(idx));
                    break;
                }
                case "c": {
                    playDbFile(database.get(idx));
                    return 7000;
                }
                case "r": {
                    togglePlayback();
                    break;
                }
                case "l": {
                    songQueue.add(database.get(idx));
                    break;
                }
            }
            return 0;
        }

        // Modifies: playbackManager and its decoding thread
        // Effects:  plays a file from database
        private static void playDbFile(AudioDataStructure audioDataStructure) {
            File f = new File(audioDataStructure.getFilename());
            if (f.isFile()) {
                visualizerThread.killThread();
                visualizerThread.safeJoin();
                playbackManager.loadAudio(f.getAbsolutePath());
                playbackManager.startAudioDecoderThread();
                playbackManager.playAudio();
                id3 = playbackManager.getID3();
                audioDataStructure.updateID3(id3); // Update on file load
                visualizerThread = new PlaybackThread();
                visualizerThread.start();
            } else {
                AnsiConsole.out().println("File no longer exists, or is currently inaccessible.");
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    // Why?
                }
            }
        }

        // Effects: prints browse menu
        private static void printBrowseMenu() {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            String playMessage = "R. Pause song";
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            if (playbackManager.audioIsLoaded()) {
                AnsiConsole.out().println(Ansi.ansi().fgBrightCyan().toString() + "Now Playing: "
                        + playbackManager.getPlaybackString() + Ansi.ansi().fgDefault().toString());
                horizonalBar();
                if (playbackManager.paused()) {
                    playMessage = "R. Resume song";
                }
            }
            AnsiConsole.out().println(Ansi.ansi().fgBrightGreen().toString()
                    + database.get(songID).getPlaybackString() + Ansi.ansi().fgDefault().toString());
            horizonalBar();
            AnsiConsole.out().println("What would you like to do?");
            AnsiConsole.out().println("1. Browse to previous song in database");
            AnsiConsole.out().println("2. Browse to next song in database");
            printIfAudioLoaded(playMessage + "\n");
            AnsiConsole.out().println("P. Play song");
            printIfAudioLoaded("L. Queue song\n");
            AnsiConsole.out().println("C. Play song and return to main menu");
            AnsiConsole.out().println("Q. Return to main menu");
        }

        // Modifies: console
        // Effects:  prints string if audio is loaded
        //           function name explains itself really
        private static void printIfAudioLoaded(String str) {
            if (playbackManager.audioIsLoaded()) {
                AnsiConsole.out().print(str);
            }
        }

        // Effects: writes bar across the screen
        private static void horizonalBar() {
            for (int i = 1; i < AnsiConsole.getTerminalWidth(); i++) { // Skip first char
                AnsiConsole.out().print("-");
            }
            AnsiConsole.out().println("-"); // Final in line
        }

        // Modifies: database (maybe)
        // Effects:  at minimum, prints the menu to the screen
        //           at maximum, resets the database
        private static void dbMenu(Scanner inputScanner) {
            state = MenuState.CLI_OTHER;
            printDbMenu();
            String selected = inputScanner.nextLine();
            switch (selected.toLowerCase()) {
                case "1": {
                    database.revertDb();
                    break;
                }
                case "2": {
                    database.cleanDb(inputScanner.nextLine().trim());
                    break;
                }
                case "3": {
                    database.cleanOldDb();
                    break;
                }
                case "r": {
                    database.cleanDbFldr();
                    break;
                }
            }
            AnsiConsole.out().print(Ansi.ansi().fgDefault());
        }

        // Effects: prints database management menu
        private static void printDbMenu() {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().print(Ansi.ansi().fgBrightRed());
            AnsiConsole.out().println("Here be dragons...");
            AnsiConsole.out().println("1. Revert to previous database version");
            AnsiConsole.out().println("2. Clean database files (enter *.audiodex.basedb file on next line)");
            AnsiConsole.out().println("3. Clean all old database files");
            AnsiConsole.out().println("R. Refresh database folder");
            AnsiConsole.out().println("To return to main menu, enter any other character");
        }
    }
}