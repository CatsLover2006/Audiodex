package ui;

import audio.AudioFilePlaybackBackend;

import java.io.File;
import java.util.InputMismatchException;
import java.util.Scanner;
import org.fusesource.jansi.*;
import org.fusesource.jansi.io.*;

import static java.lang.Thread.*;

public class Main {
    private static AudioFilePlaybackBackend playbackManager;
    private static boolean USE_CLI = true;
    private static boolean end = false;

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
            Cli.writePlaybackState();
        }
    }

    public static void main(String[] args) {
        playbackManager = new AudioFilePlaybackBackend();
        if (USE_CLI) {
            Cli.main(args);
        }
    }

    // public interface to the private CLI class
    public static class CliInterface {
        public static void updatePlaybackStatus() {
            Cli.writePlaybackState();
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
                while (run) {
                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        return;
                    }
                    Cli.writePlaybackState();
                }
            }
        }

        private static Boolean cliMainMenu = true;

        // main function for the CLI
        private static void main(String[] args) {
            AnsiConsole.systemInstall();
            if (AnsiConsole.getTerminalWidth() == 0) {
                AnsiConsole.out().println("This application does not work within your terminal.");
                AnsiConsole.out().print("Please switch to a different terminal, ");
                AnsiConsole.out().println("or use an actual terminal if you are in an IDE.");
                return;
            }
            PlaybackThread visualizerThread = new PlaybackThread();
            visualizerThread.start();
            Scanner inputScanner = new Scanner(System.in);
            while (true) {
                cliMainMenu = true;
                printMenu();
                writePlaybackState();
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
                case "c": {
                    playbackManager.playAudio();
                    break;
                }
                case "p": {
                    playbackManager.pauseAudio();
                    break;
                }
                case "s": {
                    seekAudio(inputScanner);
                    break;
                }
                case "4": {
                    cleanup(visualizerThread);
                    return;
                }
                default: {
                    unknownCommandError();
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
            end = true;
        }

        // Modifies: playbackManager and its decoding thread
        // Effects: plays a file
        private static void playFile(Scanner inputScanner, PlaybackThread visualizerThread) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            writePlaybackState();
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please enter the filename:");
            String filenameIn = inputScanner.nextLine();
            // Fix whitespace errors
            String filename = filenameIn.trim();
            // Check if file exists
            File f = new File(filename);
            if (f.isFile()) {
                visualizerThread.killThread();
                visualizerThread.safeJoin();
                playbackManager.loadAudio(filename);
                playbackManager.startAudioDecoderThread();
                playbackManager.playAudio();
                visualizerThread = new PlaybackThread();
                visualizerThread.start();
            } else {
                AnsiConsole.out().println("File doesn't exist, is a directory, or is inaccessible.");
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    return; // Why?
                }
            }
        }

        // Modifies: Console
        // Effects:  updates the audio status bar
        //           writes the entire status bar in one single print statement,
        //           which fixes a race condition with the KEYBOARD
        private static void writePlaybackState() {
            double time = playbackManager.getPercentPlayed();
            int w = AnsiConsole.getTerminalWidth() - 2;
            String burstWrite = "[";
            if (playbackManager.paused()) {
                burstWrite = Ansi.ansi().fgBrightRed().toString() + "PAUSED"
                        + Ansi.ansi().fgDefault().toString() + " [";
                w -= 7;
            }
            for (int i = 0; i < w; i++) {
                if (i < Math.floor(time * w)) {
                    burstWrite += "#";
                } else if (i < Math.round(time * w)) {
                    burstWrite += "=";
                } else {
                    burstWrite += "-";
                }
            }
            AnsiConsole.out().flush();
            AnsiConsole.out().print(Ansi.ansi().saveCursorPosition().toString()
                    + Ansi.ansi().cursor(1, 1).toString() + burstWrite + "]"
                    + Ansi.ansi().restoreCursorPosition().toString());
        }

        // Modifies: loaded file container through playbackManager
        // Effects:  seeks to location in song
        private static void seekAudio(Scanner inputScanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            writePlaybackState();
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
            writePlaybackState();
            AnsiConsole.out.print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("What would you like to do?");
            AnsiConsole.out().println("1. Play a file");
            AnsiConsole.out().println("2. Add file to database");
            AnsiConsole.out().println("3. Play a file from database");
            AnsiConsole.out().println("4. Exit");
            if (playbackManager.audioIsLoaded()) {
                AnsiConsole.out().println("P. Pause");
                AnsiConsole.out().println("C. Play");
                AnsiConsole.out().println("S. Seek");
            }
        }
    }
}