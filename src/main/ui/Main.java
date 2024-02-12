package ui;

import audio.AudioDataStructure;
import audio.AudioFileLoader;
import audio.AudioFilePlaybackBackend;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

import audio.ID3Container;
import model.AudioConversion;
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
    private static List<AudioConversion> audioConverterList;
    private static LinkedList<AudioDataStructure> played;
    private static AudioDataStructure nowPlaying;
    private static boolean loop = false;

    // Effects: shuffles the database
    private static void shuffleDatabase() {
        LinkedList<AudioDataStructure> databaseClone = new LinkedList<>();
        for (int i = 0; i < database.listSize(); i++) {
            databaseClone.addLast(database.get(i));
        }
        Random rng = new Random();
        while (!databaseClone.isEmpty()) {
            int randNumb = (int)Math.min(Math.floor(rng.nextDouble() * databaseClone.size()),
                    databaseClone.size() - 1);
            songQueue.addLast(databaseClone.get(randNumb));
            databaseClone.remove(randNumb);
        }
        Cli.playDbFile(songQueue.getFirst());
        songQueue.removeFirst();
    }

    // Effects: Counts currently converting audio files
    private static int activeAudioConversions() {
        int i = 0;
        for (AudioConversion converter : audioConverterList) {
            if (converter.isFinished()) {
                continue;
            }
            if (converter.errorOccurred()) {
                continue;
            }
            i++;
        }
        return i;
    }

    // Effects: Counts failed audio file conversions
    private static int deadAudioConversions() {
        int i = 0;
        for (AudioConversion converter : audioConverterList) {
            if (converter.isFinished()) {
                continue;
            }
            if (converter.errorOccurred()) {
                i++;
            }
        }
        return i;
    }


    // This is probably the lowest-level I still consider UI-level;
    // we're still not interacting with actual data on the filesystem
    // and this program basically is a file management program
    private static LinkedList<AudioDataStructure> songQueue;

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
    // Effects:  Is run when song is finished and redraws screen if necessary
    //           also adds previously playing song to previously played list
    public static void finishedSong() {
        if (end) {
            return;
        }
        played.addFirst(nowPlaying);
        nowPlaying = null;
        maxBoundPlayedList();
        if (USE_CLI) {
            Cli.finishedSong();
        } else {
            if (!songQueue.isEmpty()) {
                // Run the playDbFile() function for GUI
            }
        }
    }

    // Modifies: this
    // Effects:  Keeps previously played list to a reasonable size
    public static void maxBoundPlayedList() {
        while (played.size() > 100) {
            played.remove(played.size() - 1);
        }
    }

    // Modifies: this
    // Effects:  Is run when encode is finished and redraws screen if necessary.
    public static void finishedEncode() {
        if (end) {
            return;
        }
        if (USE_CLI) {
            Cli.finishedEncode();
        } else {
            // Do GUI stuff
        }
    }

    static { // Disable jaudiotagger (library) logging
        java.util.logging.LogManager manager = java.util.logging.LogManager.getLogManager();
        try {
            manager.readConfiguration(new ByteArrayInputStream(("handlers=java.util.logging.ConsoleHandler\n"
                    + "org.jaudiotagger.level=OFF").getBytes()));
        } catch (IOException e) {
            // Oops
        }
    }

    public static void main(String[] args) {
        if (strArrContains(args, "--gui")) {
            USE_CLI = false; // Prep
        }
        played = new LinkedList<>();
        songQueue = new LinkedList<>();
        database = new AudioFileList();
        database.loadDatabase();
        playbackManager = new AudioFilePlaybackBackend();
        audioConverterList = new ArrayList<>();
        if (USE_CLI) {
            while (!end) {
                Cli.cli(args);
            }
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
                    Cli.wait(20);
                    if (playbackManager == null) {
                        return;
                    }
                    Cli.doPlaybackStatusWrite();
                }
            }
        }

        // main function for the CLI
        private static void cli(String[] args) {
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
                    state = MenuState.CLI_OTHER;
                    waitForEncoders();
                    AnsiConsole.out().println("Goodbye!");
                    return;
                }
            }
        }

        // Effects: waits for all audio encoding threads to finish
        private static void waitForEncoders() {
            if (audioConverterList.isEmpty()) {
                return;
            }
            AnsiConsole.out().println("Waiting for audio converter threads to finish...");
            while (!audioConverterList.isEmpty()) {
                waitForFirstEncoder();
            }
        }

        // Effects: waits for first audio encoding thread to finish
        private static void waitForFirstEncoder() {
            if (audioConverterList.isEmpty()) {
                return;
            }
            audioConverterList.get(0).waitForEncoderFinish();
        }

        // Modifies: this
        // Effects:  finishedSong() extension for CLI
        private static void finishedSong() {
            if (loop) {
                playDbFile(played.getFirst());
                played.removeFirst();
            } else if (!songQueue.isEmpty()) {
                playDbFile(songQueue.getFirst());
                songQueue.removeFirst();
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

        // Modifies: this
        // Effects:  finishedEncode() extension for CLI
        private static void finishedEncode() {
            for (int i = audioConverterList.size() - 1; i >= 0; i--) {
                if (audioConverterList.get(i).isFinished() && !audioConverterList.get(i).errorOccurred()) {
                    audioConverterList.remove(i); // No need to update index, we're decrementing
                }
            }
            if (Objects.requireNonNull(Cli.state) == MenuState.CLI_MAINMENU) {
                Cli.printMenu();
            }
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
                case "6": {
                    startEncoder(inputScanner);
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
                case "<": {
                    playPrevious();
                    break;
                }
                case ">": {
                    playNext();
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
                case "r": {
                    shuffleDatabase();
                    break;
                }
                case "l": {
                    loop = !loop;
                    break;
                }
                /*case "}": {
                    debug(inputScanner);
                    return;
                } //*///
                default: {
                    unknownCommandError();
                }
            }
        }

        // Modifies: this
        // Effects:  plays most recently played song, if possible
        private static void playPrevious() {
            if (played.isEmpty()) {
                AnsiConsole.out().println("No previous song to play!");
                return;
            }
            songQueue.addFirst(nowPlaying);
            playDbFile(played.getFirst());
            played.removeFirst();
        }

        // Modifies: this
        // Effects:  plays next song, if possible
        private static void playNext() {
            if (songQueue.isEmpty()) {
                AnsiConsole.out().println("No song in queue to play!");
                return;
            }
            played.addFirst(nowPlaying);
            playDbFile(songQueue.getFirst());
            songQueue.removeFirst();
        }

        // Modifies: a lot:
        //           - filesystem
        //           - console
        //           - audio converter list
        // Effects:  Starts an audio converter thread
        private static void startEncoder(Scanner scanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please enter the source filename:");
            doPlaybackStatusWrite();
            // Fix whitespace errors
            filename = scanner.nextLine().trim();
            // Check if file exists
            File f = new File(filename);
            if (f.isFile()) {
                AudioConversion converter = makeAudioConverter(new AudioDataStructure(f.getAbsolutePath()), scanner);
                audioConverterList.add(converter);
                converter.start();
            } else {
                AnsiConsole.out().println("File doesn't exist, is a directory, or is inaccessible.");
            }
        }

        // Modifies: a lot:
        //           - filesystem
        //           - console
        //           - audio converter list
        // Effects:  Starts an audio converter thread using a database file
        private static void encodeDatabaseFile(AudioDataStructure audioDataStructure, Scanner scanner) {
            File f = new File(audioDataStructure.getFilename());
            if (f.isFile()) {
                AudioConversion converter = makeAudioConverter(audioDataStructure, scanner);
                audioConverterList.add(converter);
                converter.start();
            } else {
                AnsiConsole.out().println("File no longer exists, or is currently inaccessible.");
                wait(1000);
            }
        }

        // Effects: returns an audio converter object with user-selected settings
        private static AudioConversion makeAudioConverter(AudioDataStructure source, Scanner scanner) {
            AnsiConsole.out().println("Please enter the target filename:");
            AudioConversion base = new AudioConversion(source, scanner.nextLine().trim());
            HashMap<String, List<String>> options = base.getOptions();
            if (options == null) {
                AnsiConsole.out().println("Encoder does not have any selectable options.");
            }
            return base;
        }

        // Effects: wait for a set time
        private static void wait(int millis) {
            try {
                sleep(millis);
            } catch (InterruptedException e) {
                // Why?
            }
        }

        private static void debug(Scanner scanner) {
            AnsiConsole.out().println(Ansi.ansi().fgBrightGreen() + "Debugging now!");
            String file = scanner.nextLine().trim();
            AnsiConsole.out().println(AudioFileLoader.getAudioFiletype(file));
            wait(10000);
            AnsiConsole.out().print(Ansi.ansi().fgDefault());
        } //*/// Debugging

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
                wait(1000);
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
                database.sanitizeDatabase();
            } else {
                AnsiConsole.out().println("Directory doesn't exist, is a file, or is inaccessible.");
                wait(1000);
            }
        }

        // Effects: Prints the error for an unknown command
        private static void unknownCommandError() {
            AnsiConsole.out().println("Invalid option.");
            AnsiConsole.out().println("Either you mistyped something, or it hasn't been implemented yet.");
            wait(1000);
        }

        // Modifies: this, playbackManager
        // Effects:  Cleans up extra threads
        private static void cleanup() {
            end = true;
            visualizerThread.killThread();
            visualizerThread.interrupt();
            playbackManager.cleanBackend();
            visualizerThread = null;
            playbackManager = null;
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
                visualizerThread.killThread();
                visualizerThread.safeJoin();
                playbackManager.loadAudio(f.getAbsolutePath());
                playbackManager.startAudioDecoderThread();
                playbackManager.playAudio();
                id3 = playbackManager.getID3();
                visualizerThread = new PlaybackThread();
                visualizerThread.start();
                nowPlaying = new AudioDataStructure(f.getAbsolutePath());
            } else {
                AnsiConsole.out().println("File doesn't exist, is a directory, or is inaccessible.");
                wait(1000);
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
            w -= burstWrite.length() + fileDuration.length() + (loop ? " LOOP".length() : 0);
            burstWrite += getPlaybackBar(time, w);
            AnsiConsole.out().print(Ansi.ansi().saveCursorPosition().toString()
                    + Ansi.ansi().fgBrightMagenta().toString() + Ansi.ansi().cursor(1, 1).toString()
                    + burstWrite + "] " + fileDuration + (loop ? " LOOP" : "")
                    + Ansi.ansi().restoreCursorPosition().toString() + Ansi.ansi().fgDefault().toString());
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
            printMenuBlock();
            if (playbackManager.audioIsLoaded()) {
                printLoadedAudioStatus();
            }
            if (!audioConverterList.isEmpty()) {
                printReencodeStatus();
            }
            if (!played.isEmpty() || !songQueue.isEmpty()) {
                horizonalBar();
            }
            if (!played.isEmpty()) {
                AnsiConsole.out().println("<. Play previous song");
            }
            if (!songQueue.isEmpty()) {
                AnsiConsole.out().println(">. Skip to next song");
            }
        }

        // Effects: prints main menu static text
        private static void printMenuBlock() {
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("What would you like to do?");
            AnsiConsole.out().println("1. Play a file");
            AnsiConsole.out().println("2. Add file to database");
            AnsiConsole.out().println("3. Scan directory into database");
            AnsiConsole.out().println("4. Browse database");
            AnsiConsole.out().println("5. Save database");
            AnsiConsole.out().println("6. Re-encode file");
            AnsiConsole.out().println("R. Shuffle and play database");
            AnsiConsole.out().println("L. Toggle loop");
            AnsiConsole.out().println("Q. Exit");
            AnsiConsole.out().println("M. Enter database backup manager");
        }

        // Modifies: console
        // Effects:  prints the now playing text and menu
        private static void printLoadedAudioStatus() {
            horizonalBar();
            AnsiConsole.out().println(Ansi.ansi().fgBrightCyan().toString() + "Now Playing: "
                    + playbackManager.getPlaybackString() + Ansi.ansi().fgDefault().toString());
            AnsiConsole.out().println("P. Pause");
            AnsiConsole.out().println("C. Play");
            AnsiConsole.out().println("S. Seek");
            AnsiConsole.out().println("D. Add playing file to database");
        }

        // Modifies: console
        // Effects:  prints the status of encoders
        private static void printReencodeStatus() {
            horizonalBar();
            AnsiConsole.out().println(Ansi.ansi().fgBrightYellow().toString() + "Converting "
                    + activeAudioConversions() + " audio files..." + Ansi.ansi().fgDefault().toString());
            int dead = deadAudioConversions();
            if (dead != 0) {
                AnsiConsole.out().println(Ansi.ansi().fgBrightRed().toString() + dead
                        + " audio conversions failed." + Ansi.ansi().fgDefault().toString());
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
                    playDbFile(songQueue.getFirst());
                    songQueue.removeFirst();
                }
                if (songID < 0) {
                    songID += database.listSize();
                }
                if (songID >= database.listSize()) {
                    songID -= database.listSize();
                }
                printBrowseMenu();
                selected = inputScanner.nextLine();
                int l = browseSwitch(selected, songID, inputScanner);
                if (l == 7000) {
                    return;
                }
                songID += l;
            } while (!selected.equalsIgnoreCase("q"));
        }

        @SuppressWarnings("methodlength") // Large switch/case
        // Modifies: this
        // Effects:  handles the switch case for browseMenu()
        private static int browseSwitch(String in, int idx, Scanner scanner) {
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
                case "e": {
                    encodeDatabaseFile(database.get(idx), scanner);
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
                nowPlaying = audioDataStructure;
                id3 = playbackManager.getID3();
                audioDataStructure.updateID3(id3); // Update on file load
                visualizerThread = new PlaybackThread();
                visualizerThread.start();
            } else {
                AnsiConsole.out().println("File no longer exists, or is currently inaccessible.");
                wait(1000);
            }
        }

        // Effects: prints browse menu
        private static void printBrowseMenu() {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite(); // Checkstyle made me do this
            String playMessage = playbackManager.paused() ? "R. Resume song" : "R. Pause song";
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            if (playbackManager.audioIsLoaded()) {
                AnsiConsole.out().println(Ansi.ansi().fgBrightCyan().toString() + "Now Playing: "
                        + playbackManager.getPlaybackString() + Ansi.ansi().fgDefault().toString());
                horizonalBar();
            }
            AnsiConsole.out().println(Ansi.ansi().fgBrightGreen().toString()
                    + database.get(songID).getPlaybackString() + Ansi.ansi().fgDefault().toString());
            horizonalBar();
            AnsiConsole.out().println("What would you like to do?");
            AnsiConsole.out().println("1. Browse to previous song in database");
            AnsiConsole.out().println("2. Browse to next song in database");
            AnsiConsole.out().println("E. Re-encode song");
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
            AnsiConsole.out().print(Ansi.ansi().fgBrightRed());
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Here be dragons...");
            AnsiConsole.out().println("You have been warned.");
            AnsiConsole.out().println("1. Revert to previous database version");
            AnsiConsole.out().println("2. Clean database files (enter *.audiodex.basedb file on next line)");
            AnsiConsole.out().println("3. Clean all old database files");
            AnsiConsole.out().println("R. Refresh database folder");
            AnsiConsole.out().println("To return to main menu, enter any other character");
        }
    }
}