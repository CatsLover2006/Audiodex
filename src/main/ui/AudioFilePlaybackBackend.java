package ui;

import audio.AudioDecoder;
import audio.AudioFileLoader;
import audio.AudioSample;
import audio.ID3Container;
import model.ExceptionIgnore;

import javax.sound.sampled.*;
import java.awt.image.BufferedImage;

// Backend for allowing interactions between the UI and filesystem
// Specific to decoding audio
// This class is incapable of automated testing: multithreading over several
public class AudioFilePlaybackBackend {

    // No other class needs to know this
    // This is the audio decoding thread
    private class DecodingThread extends Thread {

        // No other class needs to know this
        // This quite literally just offloads the task of telling
        // the main thread that this thread is done so I can kill this thread
        private class FinishedSongThread extends Thread {
            public void run() {
                while (decoderThread != null) {
                    // Wait for decoder thread to finish, in case we've got a music queue
                    ExceptionIgnore.ignoreExc(() -> sleep(0, 1));
                }
                App.finishedSong();
            }
        }

        private volatile boolean run = true;

        // Modifies: this
        // Effects:  ends thread
        public void killThread() {
            run = false;
        }

        // Effects: join() but no try-catch
        public void safeJoin() {
            ExceptionIgnore.ignoreExc(() -> join());
        }

        // Effects: plays audio in file loadedFile
        public void run() {
            AudioSample sample;
            while (run && loadedFile.moreSamples()) {
                sample = loadedFile.getNextSample();
                line.write(sample.getData(), 0, sample.getLength());
            }
            line.stop();
            loadedFile.closeAudioFile();
            loadedFile = null;
            // This might be cursed, but whatever
            (new FinishedSongThread()).start();
            decoderThread = null;
        }
    }

    public boolean audioIsSkipping() {
        if (loadedFile == null) {
            return true;
        }
        return loadedFile.skipInProgress();
    }

    private AudioDecoder loadedFile;
    private AudioFormat audioFormat;
    private SourceDataLine line = null;
    private DecodingThread decoderThread = null;
    private boolean replayGain = false;
    private boolean paused = false;
    private float replayGainVal;
    private boolean failedReplayGainSet = false;

    // Modifies: this
    // Effects:  sets replaygain status
    public void setReplayGain(boolean to) {
        replayGain = to;
        if (line == null || !line.isOpen()) {
            return;
        }
        failedReplayGainSet = false;
        try {
            if (replayGain) {
                setReplayGain();
            } else {
                resetReplayGain();
            }
        } catch (Exception e) {
            failedReplayGainSet = true;
        }
        for (Control control : line.getControls()) {
            System.out.println(control.toString());
        }
    }

    // Modifies: this
    // Effects:  sets replaygain value
    private void setReplayGain() {
        FloatControl gainControl =
                (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
        gainControl.setValue(replayGainVal);
    }

    // Modifies: this
    // Effects:  resets replaygain value
    private void resetReplayGain() {
        FloatControl gainControl =
                (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
        gainControl.setValue(-6.0f);
    }

    // Requires: filename points to file (in order for anything to happen)
    // Modifies: this
    // Effects:  loads audio and gets decoder thread to
    public void loadAudio(String filename) {
        unloadAudio();
        loadedFile = AudioFileLoader.loadFile(filename);
        if (loadedFile == null) {
            return;
        }
        loadedFile.prepareToPlayAudio();
        if (!loadedFile.isReady()) {
            return;
        }
        try {
            audioFormat = loadedFile.getAudioOutputFormat();
            line = AudioSystem.getSourceDataLine(audioFormat);
            line.open(audioFormat);
            replayGainVal = loadedFile.getReplayGain();
            System.out.println("Replaygain is: " + replayGainVal);
            setReplayGain(replayGain);
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    // Effects: returns image of playing audio
    public BufferedImage getArtwork() {
        try {
            return (BufferedImage) loadedFile.getArtwork().getImage();
        } catch (Exception e) {
            return null;
        }
    }

    // loadAudio helper
    private void unloadAudio() {
        if (decoderThread != null && decoderThread.isAlive()) {
            pauseAudio();
        }
        if (line != null && line.isActive()) {
            line.stop();
            line.close();
        }
        if (loadedFile != null && loadedFile.isReady()) {
            loadedFile.closeAudioFile();
            loadedFile = null;
        }
        paused = false;
    }

    public boolean paused() {
        return decoderThread != null && paused;
    }

    // Effects: returns a number between 0.0 and 1.0 relating to how much of the audio is played
    //          will return 0.0 if no audio is loaded
    public double getPercentPlayed() {
        if (loadedFile == null) {
            return 0;
        }
        if (decoderThread != null && decoderThread.isAlive()) {
            return loadedFile.getCurrentTime() / loadedFile.getFileDuration();
        }
        return 0;
    }

    // Effects: returns the audio length in seconds
    //          returns -1 if none is loaded
    public double getFileDuration() {
        if (loadedFile != null) {
            return loadedFile.getFileDuration();
        }
        return -1;
    }

    // Effects: returns the audio played in seconds
    //          returns -1 if none is loaded
    public double getCurrentTime() {
        if (loadedFile != null) {
            return loadedFile.getCurrentTime();
        }
        return -1;
    }

    // Modifies: this
    // Effects:  starts decoder thread, if it isn't already present
    public void startAudioDecoderThread() {
        if (decoderThread == null) {
            decoderThread = new DecodingThread();
        }
    }

    // Requires: startAudioDecoderThread() ran
    // Modifies: this
    // Effects:  resumes paused audio
    public void playAudio() {
        if (loadedFile == null) {
            return;
        }
        paused = false;
        line.start();
        if (decoderThread.isAlive()) {
            decoderThread.resume();
        } else {
            decoderThread.start();
        }
        App.CliInterface.updatePlaybackStatus();
    }

    // Modifies: this
    // Effects:  pauses audio (if it isn't already paused)
    public void pauseAudio() {
        paused = true;
        if (decoderThread.isAlive()) {
            decoderThread.suspend();
            line.stop();
        }
        App.CliInterface.updatePlaybackStatus();
    }

    // Requires: 0 <= time <= audio length
    // Modifies: this
    // Effects:  sets playback pointer to the specified time
    public void seekTo(double time) {
        loadedFile.goToTime(time);
        App.CliInterface.updatePlaybackStatus();
    }

    // Effects: waits for audio playback to finish
    public void waitForAudioFinish() {
        decoderThread.safeJoin();
    }

    // Modifies: this
    // Effects:  ends audio decoding thread and closes currently playing file
    public void cleanBackend() {
        if (decoderThread != null) {
            decoderThread.killThread();
            playAudio(); // Safely resume thread
            decoderThread = null;
        }
        if (line != null) {
            line.close();
        }
        if (loadedFile != null) {
            loadedFile.closeAudioFile();
        }
    }

    // Effects: returns true if audio is loaded
    public boolean audioIsLoaded() {
        return loadedFile != null;
    }

    // Effects: passthrough for ID3Container of file
    public ID3Container getID3() {
        if (loadedFile == null) {
            return new ID3Container();
        }
        return loadedFile.getID3();
    }

    // Effects: returns playback string for display
    public String getPlaybackString() {
        return getPlaybackString(true);
    }

    // Effects: returns playback string for display
    public String getPlaybackString(boolean cli) {
        if (loadedFile == null) {
            return "Not Playing";
        }
        ID3Container id3 = loadedFile.getID3();
        if (id3 == null) {
            return loadedFile.getFileName();
        }
        String workingData = (String) id3.getID3Data("Title");
        if (workingData == null || workingData.equals("null") || workingData.isEmpty()) {
            return loadedFile.getFileName();
        }
        String base = workingData;
        workingData = (String) id3.getID3Data("Artist");
        if (cli && !(workingData == null || workingData.equals("null") || workingData.isEmpty())) {
            base += " by " + workingData;
        }
        return base;
    }
}
