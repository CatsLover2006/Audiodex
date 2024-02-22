package ui;

import audio.AudioDataStructure;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import audio.ID3Container;
import model.AudioConversion;
import model.AudioFileList;
import org.fusesource.jansi.*;

import javax.imageio.ImageIO;
import javax.swing.*;

import static java.lang.Math.floor;
import static java.lang.Thread.*;

public class Main {
    private static boolean notMain = true;
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

    // Effects: returns formatted time
    private static String formatTime(long seconds) {
        if (seconds == -1) {
            return "X:XX";
        }
        return String.format("%01d:%02d", seconds / 60, seconds % 60);
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
        if (end || notMain) {
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
        if (end || notMain) {
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
        notMain = false;
        if (strArrContains(args, "--gui")) {
            USE_CLI = false; // Prep
            Gui.createLoadingThread();
        }
        played = new LinkedList<>();
        songQueue = new LinkedList<>();
        database = new AudioFileList();
        database.loadDatabase();
        database.sortList("Album_Title");
        playbackManager = new AudioFilePlaybackBackend();
        audioConverterList = new ArrayList<>();
        if (USE_CLI) {
            Cli.cli(args);
        } else {
            Gui.doGui(args);
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

    // GUI mode
    private static class Gui {
        private static LoadingFrameThread loadingFrame = null;
        private static JFrame mainWindow;

        // Modifies: this
        // Effects:  shows loading thread
        public static void createLoadingThread() {
            if (loadingFrame != null) {
                return;
            }
            loadingFrame = new LoadingFrameThread();
            loadingFrame.start();
        }

        // Modifies: this
        // Effects:  hides loading thread
        private static void closeLoadingThread() {
            if (loadingFrame == null) {
                return;
            }
            while (!loadingFrame.showing) {
                Cli.wait(1);
            }
            loadingFrame.doneLoading();
            loadingFrame.safeJoin(250);
            loadingFrame = null;
        }

        private static String[] columns = {
                "Title", "Artist", "Album", "Album Artist"
        };

        private static JScrollPane musicList;

        // Modifies: this
        // Effects:  displays main window
        public static void doGui(String[] args) {
            mainWindow = new JFrame("AudioDex");
            mainWindow.setSize(900, 500);
            mainWindow.setResizable(true);
            try {
                mainWindow.setIconImage(ImageIO.read(new File("./data/spec/AppIcon.png")));
            } catch (IOException e) {
                // Whoops
            }
            mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            updateGuiList();
            createPlaybackBar();
            visualizerThread = new PlaybackThread();
            visualizerThread.start();
            mainWindow.add(playbackStatusView);
            mainWindow.add(musicList);
            setupMainWindowLayout();
            mainWindow.setVisible(true);
            closeLoadingThread();
        }

        // Modifies: this
        // Effects:  sets up group layout for main window
        private static void setupMainWindowLayout() {
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.weightx = 1.0;
            constraints.anchor = GridBagConstraints.FIRST_LINE_START;
            constraints.gridx = 0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            layout.setConstraints(playbackStatusView, constraints);
            constraints.fill = GridBagConstraints.BOTH;
            constraints.weighty = 1.0;
            layout.setConstraints(musicList, constraints);
            mainWindow.setLayout(layout);
        }

        // Effects: converts ID3 tag to its corresponding menu bar item
        private static String convertKey(String key) {
            switch (key) {
                case "AlbumArtist":
                    return "Album Artist";
                default:
                    return key;
            }
        }

        // Effects: converts menu bar item to its corresponding ID3 tag
        private static String convertValue(String value) {
            switch (value) {
                case "Album Artist":
                    return "AlbumArtist";
                default:
                    return value;
            }
        }

        // List management stuff
        private static int lastClicked = -1;

        // Modifies: this
        // Effects:  updates the list
        public static void updateGuiList() {
            Object[][] rowData = new Object[database.listSize()][columns.length];
            String key;
            for (int j = 0; j < columns.length; j++) {
                key = convertValue(columns[j]); // Save time on lookup
                for (int i = 0; i < database.listSize(); i++) {
                    rowData[i][j] = database.get(i).getId3Data().getID3Data(key);
                }
            }
            JTable musicTable = new JTable(rowData, columns) {
                public boolean editCellAt(int row, int column, java.util.EventObject e) {
                    if (lastClicked == row) {
                        playDbFile(database.get(row));
                    } else {
                        lastClicked = row;
                    }
                    return false;
                }
            };
            musicList = new JScrollPane(musicTable);
            lastClicked = -1;
            musicTable.setFillsViewportHeight(true);
        }

        private static JSlider playbackSlider = new JSlider(0, 0);
        private static JLabel leftPlaybackLabel = new JLabel("X:XX");;
        private static JLabel rightPlaybackLabel = new JLabel("X:XX");;
        private static JPanel playbackStatusView = new JPanel(true);

        // Effects: sets up the playback bar
        public static void createPlaybackBar() {
            playbackSlider.addChangeListener(e -> {
                if (playbackSlider.getValueIsAdjusting()) {
                    playbackManager.seekTo(playbackSlider.getValue() / 1000.0);
                }
            });
            playbackStatusView.add(leftPlaybackLabel);
            playbackStatusView.add(playbackSlider);
            playbackStatusView.add(rightPlaybackLabel);
            setupPlaybackBarLayout();
        }

        // Modifies: this
        // Effects:  sets up the layout of the playback bar
        private static void setupPlaybackBarLayout() {
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridy = 0;
            layout.setConstraints(leftPlaybackLabel, constraints);
            layout.setConstraints(rightPlaybackLabel, constraints);
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1.0;
            layout.setConstraints(playbackSlider, constraints);
            playbackStatusView.setLayout(layout);
        }

        // Modifies: this
        // Effects:  sets playback bar length
        public static void setPlaybackBarLength(double fileTime) {
            if (fileTime == -1) {
                playbackSlider.setMaximum(0);
            } else { // Dies at a month long but why bother
                playbackSlider.setMaximum((int) (fileTime * 1000));
            }
            rightPlaybackLabel.setText(formatTime((long) fileTime));
            playbackStatusView.updateUI();
        }

        // Modifies: this
        // Effects:  sets the playback bar value and times
        public static void updatePlaybackBarStatus(double time) {
            if (time == -1) {
                playbackSlider.setValue(0);
            } else if (!playbackSlider.getValueIsAdjusting()) {
                playbackSlider.setValue((int) (time * 1000));
            } // Dies at a month long but why bother
            leftPlaybackLabel.setText(formatTime((long) time));
            playbackStatusView.updateUI();
        }

        // Modifies: playbackManager and its decoding thread
        // Effects:  plays a file from database
        private static void playDbFile(AudioDataStructure audioDataStructure) {
            File f = new File(audioDataStructure.getFilename());
            if (f.isFile()) {
                playbackManager.loadAudio(f.getAbsolutePath());
                setPlaybackBarLength(playbackManager.getFileDuration());
                playbackManager.startAudioDecoderThread();
                playbackManager.playAudio();
                nowPlaying = audioDataStructure;
                id3 = playbackManager.getID3();
                audioDataStructure.updateID3(id3); // Update on file load
            } else {
                System.out.println("File no longer exists, or is currently inaccessible.");
            }
        }

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
                while (run) {
                    Cli.wait(20);
                    if (playbackManager == null) {
                        continue;
                    }
                    updatePlaybackBarStatus(playbackManager.getCurrentTime());
                }
            }
        }

        // No other class needs to know this
        // class for the thread which displays the loading menu
        private static class LoadingFrameThread extends Thread {
            private volatile boolean showing = true;

            public boolean isShowing() {
                return showing;
            }

            // Modifies: this
            // Effects:  closes loading window by setting showing to false
            public void doneLoading() {
                showing = false;
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

            // Effects: shows loading indicator window
            public void run() {
                JFrame loadingFrame = new JFrame("Loading...");
                loadingFrame.setSize(200, 150);
                loadingFrame.setResizable(false);
                loadingFrame.setUndecorated(true);
                loadingFrame.getRootPane().setWindowDecorationStyle(JRootPane.NONE);
                loadingFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                loadingFrame.add(new JLabel("Loading..."));
                loadingFrame.setVisible(true);
                while (showing) {
                    Cli.wait(100);
                }
                loadingFrame.setVisible(false);
                loadingFrame.dispose();
                showing = false;
            }
        }
    }

    // public interface to the private CLI class
    public static class CliInterface {
        public static void updatePlaybackStatus() {
            if (notMain) {
                return;
            }
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
            Scanner inputScanner = new Scanner(System.in);
            updateAllFiles(inputScanner);
            database.removeEmptyFiles();
            visualizerThread = new PlaybackThread();
            visualizerThread.start();
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

        // Modifies: this
        // Effects:  requests filename updates for all unlocatable files
        private static void updateAllFiles(Scanner inputScanner) {
            List<Integer> indexes = database.getRemovedFiles();
            if (indexes.size() == 0) {
                return;
            }
            for (Integer index : indexes) {
                String in;
                do {
                    AnsiConsole.out().println("Couldn't find the file " + database.get(index).getFilename()
                            + ", update index? (Y/n)");
                    in = inputScanner.nextLine().toLowerCase().trim();
                    if (in == "n") {
                        break;
                    }
                    AnsiConsole.out().println("Please enter the new filename.");
                    database.updateFile(index, inputScanner.nextLine().trim());
                } while (database.get(index).isEmpty() || !(new File(database.get(index).getFilename())).exists());
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
                case "z": {
                    updateMetadata();
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
            if (nowPlaying != null) {
                songQueue.addFirst(nowPlaying);
            }
            playDbFile(played.getFirst());
            played.removeFirst();
        }

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

        // Modifies: this
        // Effects:  plays next song, if possible
        private static void playNext() {
            if (songQueue.isEmpty()) {
                AnsiConsole.out().println("No song in queue to play!");
                return;
            }
            // If song queue isn't empty we're playing SOMETHING
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
                if (converter.errorOccurred()) {
                    AnsiConsole.out().println("An error has occurred.");
                    return;
                }
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
                if (converter.errorOccurred()) {
                    AnsiConsole.out().println("An error has occurred.");
                    return;
                }
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
            } else {
                HashMap<String, String> selectedOptions = new HashMap<>();
                for (Map.Entry<String, List<String>> option : options.entrySet()) {
                    AnsiConsole.out().println("Options for " + option.getKey() + ":");
                    for (int i = 0; i < option.getValue().size(); i++) {
                        AnsiConsole.out().println(i + ". " + option.getValue().get(i));
                    }
                    AnsiConsole.out().println("Please select an option.");
                    selectedOptions.put(option.getKey(),
                            option.getValue().get(getUserIntToValue(option.getValue().size(), scanner)));
                }
                base.setAudioSettings(selectedOptions);
            }
            return base;
        }

        // Effects: returns a valid number between 0 and a specified value
        private static int getUserIntToValue(int max, Scanner scanner) {
            int out;
            while (true) {
                try {
                    String read = scanner.nextLine();
                    out = Integer.parseInt(read);
                    if (out > max) {
                        throw new NumberFormatException();
                    }
                    return out;
                } catch (NumberFormatException e) {
                    AnsiConsole.out().println("Invalid number, please pick again.");
                }
            }
        }

        // Effects: wait for a set time
        private static void wait(int millis) {
            try {
                sleep(millis);
            } catch (InterruptedException e) {
                // Why?
            }
        }

        /*private static void debug(Scanner scanner) {
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
                database.sanitizeDatabase();
                database.sortList("Album_Title");
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
                database.sortList("Album_Title");
            } else {
                AnsiConsole.out().println("Directory doesn't exist, is a file, or is inaccessible.");
                wait(1000);
            }
        }

        // Modifies: database
        // Effects:  updates all database metadata caches
        private static void updateMetadata() {
            for (int i = 0; i < database.listSize(); i++) {
                database.updateFile(i, new AudioDataStructure(database.get(i).getFilename()));
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
            String color = playbackManager.audioIsSkipping() ? Ansi.ansi().fgBrightBlack().toString()
                    : Ansi.ansi().fgBrightMagenta().toString();
            String burstWrite = formatTime((long) floor(playbackManager.getCurrentTime())) + " [";
            if (playbackManager.paused()) {
                burstWrite = Ansi.ansi().fgBrightRed().toString() + "PAUSED" + color + " " + burstWrite;
                w += Ansi.ansi().fgBrightRed().toString().length() + Ansi.ansi().fgBrightMagenta().toString().length();
            }
            String fileDuration = formatTime((long) floor(playbackManager.getFileDuration()));
            w -= burstWrite.length() + fileDuration.length() + (loop ? " LOOP".length() : 0);
            burstWrite += getPlaybackBar(time, w);
            AnsiConsole.out().print(Ansi.ansi().saveCursorPosition().toString() + color
                    + Ansi.ansi().cursor(1, 1).toString() + burstWrite + "] " + fileDuration
                    + (loop ? " LOOP" : "") + Ansi.ansi().restoreCursorPosition().toString()
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
            AnsiConsole.out().println("Z. Reload database metadata");
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
            if (database.listSize() == 0) {
                return;
            }
            state = MenuState.CLI_BROWSEMENU;
            while (true) {
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
                int l = browseSwitch(inputScanner.nextLine(), songID, inputScanner);
                if (l == 7000) {
                    return;
                }
                songID += l;
            }
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
                case "c":
                    playDbFile(database.get(idx));
                case "q":
                    return 7000;
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