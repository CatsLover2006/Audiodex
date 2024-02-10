package ui;

import audio.AudioDataStructure;
import audio.AudioFilePlaybackBackend;

import java.io.File;
import java.util.InputMismatchException;
import java.util.Scanner;

import audio.ID3Container;
import model.AudioFileList;
import org.fusesource.jansi.*;
import org.fusesource.jansi.io.*;

import static java.lang.Math.floor;
import static java.lang.Thread.*;

public class Main {
    private static ID3Container id3;
    private static AudioFilePlaybackBackend playbackManager;
    private static boolean USE_CLI = true;
    private static boolean end = false;
    private static String filename = "";
    private static AudioFileList database;

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
    // Checks when song is finished and redraws screen if necessary.
    public static void finishedSong() {
        if (USE_CLI) {
            if (Cli.cliMainMenu) {
                Cli.printMenu();
            }
            Cli.doPlaybackStatusWrite();
        }
    }

    public static void main(String[] args) {
        database = new AudioFileList();
        database.loadDatabase();
        playbackManager = new AudioFilePlaybackBackend();
        if (USE_CLI) {
            Cli.main(args);
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
                    return;
                }
            }

            // Effects: join(long millis) but no try-catch
            public void safeJoin(long millis) {
                try {
                    join(millis);
                } catch (InterruptedException e) {
                    return;
                }
            }

            // Effects: join(long millis, int nanos) but no try-catch
            public void safeJoin(long millis, int nanos) {
                try {
                    join(millis, nanos);
                } catch (InterruptedException e) {
                    return;
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

        private static Boolean cliMainMenu = true;

        // main function for the CLI
        private static void main(String[] args) {
            AnsiConsole.systemInstall();
            if (AnsiConsole.getTerminalWidth() == 0) {
                AnsiConsole.out().println("This application works better in a regular java console.");
            }
            PlaybackThread visualizerThread = new PlaybackThread();
            visualizerThread.start();
            Scanner inputScanner = new Scanner(System.in);
            while (true) {
                cliMainMenu = true;
                printMenu();
                doPlaybackStatusWrite();
                cliMain(inputScanner, visualizerThread, inputScanner.nextLine());
                if (end) {
                    AnsiConsole.out().println("Goodbye!");
                    return;
                }
            }
        }

        @SuppressWarnings("methodlength") // Large switch/case
        // CLI switch manager
        // calls corresponding functions for different actions
        private static void cliMain(Scanner inputScanner, PlaybackThread visualizerThread, String selected) {
            cliMainMenu = false;
            switch (selected.toLowerCase()) {
                case "1": {
                    playFile(inputScanner, visualizerThread);
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
                    cleanup(visualizerThread);
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

        private static void debug(Scanner scanner) {
            AnsiConsole.out().println(Ansi.ansi().fgBrightGreen() + "Debugging now!");
            AnsiConsole.out().println("AudioDataStructure toString Data:");
            AudioDataStructure ads = new AudioDataStructure(filename);
            String str = ads.toString();
            AnsiConsole.out().println(str);
            AudioDataStructure adsFrom = AudioDataStructure.fromString(str);
            AnsiConsole.out().println("AudioDataStructure fromString Decode Data:");
            AnsiConsole.out().println(adsFrom.toString());
            AnsiConsole.out().print(Ansi.ansi().fgDefault());
        }

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
                return; // Why?
            }
        }

        // Modifies: this, playbackManager
        // Effects:  Cleans up extra threads
        private static void cleanup(PlaybackThread visualizerThread) {
            visualizerThread.killThread();
            visualizerThread.interrupt();
            playbackManager.cleanBackend();
            visualizerThread = null;
            playbackManager = null;
            end = true;
        }

        // Modifies: playbackManager and its decoding thread
        // Effects: plays a file
        private static void playFile(Scanner inputScanner, PlaybackThread visualizerThread) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please enter the filename:");
            String filenameIn = inputScanner.nextLine();
            // Fix whitespace errors
            filename = filenameIn.trim();
            // Check if file exists
            File f = new File(filename);
            if (f.isFile()) {
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
                        + Ansi.ansi().fgDefault().toString() + " " + burstWrite;
                w += Ansi.ansi().fgBrightRed().toString().length() + Ansi.ansi().fgDefault().toString().length();
            }
            String fileDuration = formatTime((long) floor(playbackManager.getFileDuration()));
            w -= burstWrite.length() + fileDuration.length();
            for (int i = 0; i < w; i++) {
                if (i < floor(time * w)) {
                    burstWrite += "#";
                } else if (i < Math.round(time * w)) {
                    burstWrite += "=";
                } else {
                    burstWrite += "-";
                }
            }
            AnsiConsole.out().flush();
            AnsiConsole.out().print(Ansi.ansi().saveCursorPosition().toString()
                    + Ansi.ansi().cursor(1, 1).toString() + burstWrite + "] " + fileDuration
                    + Ansi.ansi().restoreCursorPosition().toString());
        }

        // Modifies: loaded file container through playbackManager
        // Effects:  seeks to location in song
        private static void seekAudio(Scanner inputScanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            AnsiConsole.out.print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please input the time (in seconds) you'd like to go to.");
            while (true) {
                try {
                    playbackManager.seekTo(inputScanner.nextDouble());
                    inputScanner.nextLine(); // Bugfix (fixes duplicate menu)
                    return;
                } catch (InputMismatchException e) {
                    if (inputScanner.nextLine() == "q" || inputScanner.nextLine() == "Q") {
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
                AnsiConsole.out().println("Now Playing: " + playbackManager.getPlaybackString());
                AnsiConsole.out().println("P. Pause");
                AnsiConsole.out().println("C. Play");
                AnsiConsole.out().println("S. Seek");
                AnsiConsole.out().println("D. Add playing file to database");
            }
        }

        // Modifies: database (maybe)
        // Effects:  at minimum, prints the menu to the screen
        //           at maximum, resets the database
        private static void dbMenu(Scanner inputScanner) {
            cliMainMenu = false;
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
            AnsiConsole.out().print(Ansi.ansi().fgBrightRed());
            doPlaybackStatusWrite();
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Here be dragons...");
            AnsiConsole.out().println("1. Revert to previous database version");
            AnsiConsole.out().println("2. Clean database files (enter *.audiodex.basedb file on next line)");
            AnsiConsole.out().println("3. Clean all old database files");
            AnsiConsole.out().println("R. Refresh database folder");
            AnsiConsole.out().println("To return to main menu, enter any other character");
        }
    }
}