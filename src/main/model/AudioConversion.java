package model;

import audio.*;
import audio.filetypes.encoders.*;
import ui.App;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static audio.AudioFileLoader.getAudioFiletype;

// Defines an audio conversion manager
public class AudioConversion {
    // Nobody else needs to know about this
    // Defines this audio converter's thread
    private class AudioConverterThread extends Thread {

        // No other class needs to know this
        // This quite literally just offloads the task of telling
        // the main thread that this thread is done
        private class FinishedEncodeThread extends Thread {
            @Override
            public void run() {
                do {
                    // Wait for decoder thread to finish, in case we've got a music queue
                    ExceptionIgnore.ignoreExc(() -> sleep(0, 1));
                } while (converterThread != null);
                App.finishedEncode();
            }
        }

        // Effects: join() but no try-catch
        public void safeJoin() {
            ExceptionIgnore.ignoreExc(() -> join());
        }

        // Effects: plays audio in file loadedFile
        @Override
        public void run() {
            if (isFinished()) {
                return;
            }
            // This is lower priority than the main thread
            Thread.currentThread().setPriority(MIN_PRIORITY);
            error = !helper.encodeAudio(targetFile);
            done = true;
            source.closeAudioFile();
            source = null;
            new FinishedEncodeThread().start();
            converterThread = null;
        }
    }

    private volatile boolean done = false;
    private volatile boolean error = false;
    private AudioConverterThread converterThread;
    private AudioDecoder source;
    private AudioEncoder helper;
    private final String targetFile;

    // Effects: everything is ready
    public AudioConversion(AudioDataStructure sourceFile, String targetFile) {
        source = AudioFileLoader.loadFile(new File(sourceFile.getFilename()).getAbsolutePath());
        if (source == null) {
            error = true;
            done = true;
            this.targetFile = "";
            return;
        }
        this.targetFile = new File(targetFile).getAbsolutePath();
        setHelper(this.targetFile);
        if (helper == null) {
            error = true;
            done = true;
            return;
        }
        helper.setSource(source);
    }

    // Effects: gets target string
    public String getTarget() {
        return targetFile;
    }

    // Effects: gets completion percent [0.0, 1.0]
    public double getComplete() {
        if (helper == null) {
            return 0;
        }
        return helper.encodedPercent();
    }

    // Modifies: this
    // Effects:  sets audio encoder settings, if relevant
    public void setAudioSettings(HashMap<String, String> settings) {
        source.prepareToPlayAudio();
        helper.setAudioFormat(source.getAudioOutputFormat(), settings);
        source.closeAudioFile();
    }

    // Modifies: this
    // Effects:  sets helper to relevant audio encoder
    //           otherwise it sets the error flags and leaves
    private void setHelper(String filename) {
        switch (getAudioFiletype(filename)) {
            case PCM_WAV:
                helper = new WAV();
                break;
            case AIFF:
                helper = new Aiff();
                break;
            case MP3:
                helper = new MP3();
                break;
            default:  // We done here
                error = true;
                done = true;
                break;
        }
    }

    // Modifies: this
    // Effects:  starts the audio encoding thread
    public void start() {
        converterThread = new AudioConverterThread();
        source.prepareToPlayAudio();
        converterThread.start();
    }

    public boolean isFinished() {
        return done;
    }

    // returns true if there was an error and the conversion is done
    public boolean errorOccurred() {
        return error && done;
    }

    public void waitForEncoderFinish() {
        ExceptionIgnore.ignoreExc(() -> converterThread.safeJoin());
    }

    // getter but null-safe
    public HashMap<String, List<String>> getOptions() {
        if (helper == null) {
            return null;
        }
        return helper.getEncoderSpecificSelectors();
    }
}
