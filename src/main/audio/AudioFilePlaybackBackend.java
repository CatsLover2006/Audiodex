package audio;

import ui.Main;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

// Backend for allowing interactions between the UI and filesystem
// Specific to decoding audio
public class AudioFilePlaybackBackend {

    // No other class needs to know this
    // This is the audio decoding thread
    private class DecodingThread extends Thread {
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
            byte[] sample;
            while (run && loadedFile.moreSamples()) {
                sample = loadedFile.getNextSample();
                line.write(sample, 0, sample.length);
            }
            line.stop();
            loadedFile.closeAudioFile();
            loadedFile = null;
            Main.finishedSong();
            // This might be cursed, but whatever
            decoderThread = null;
        }
    }

    private AudioDecoder loadedFile;
    private AudioFormat audioFormat;
    private SourceDataLine line = null;
    private DecodingThread decoderThread = null;
    protected boolean paused = false;

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
            line.open();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
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
    //          returns 0.0 if none is loaded
    public double getFileDuration() {
        if (loadedFile != null) {
            return loadedFile.getFileDuration();
        }
        return 0;
    }

    // Effects: returns the audio played in seconds
    //          returns 0.0 if none is loaded
    public double getCurrentTime() {
        if (loadedFile != null) {
            return loadedFile.getCurrentTime();
        }
        return 0;
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
        if (decoderThread.isAlive()) {
            line.start();
            decoderThread.resume();
        } else {
            line.start();
            decoderThread.start();
        }
        Main.CliInterface.updatePlaybackStatus();
    }

    // Modifies: this
    // Effects:  pauses audio (if it isn't already paused)
    public void pauseAudio() {
        paused = true;
        if (decoderThread.isAlive()) {
            decoderThread.suspend();
            line.stop();
        }
        Main.CliInterface.updatePlaybackStatus();
    }

    // Modifies: this
    // Effects:  sets playback pointer to the beginning of the loaded file
    public void restartAudio() {
        loadedFile.goToTime(0);
        Main.CliInterface.updatePlaybackStatus();
    }

    // Requires: 0 <= time <= audio length
    // Modifies: this
    // Effects:  sets playback pointer to the specified time
    public void seekTo(double time) {
        loadedFile.goToTime(time);
        Main.CliInterface.updatePlaybackStatus();
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
            decoderThread = null;
        }
        if (line != null) {
            line.close();
        }
        if (loadedFile != null) {
            loadedFile.closeAudioFile();
        }
    }

    // Effects: returns true if audio is playing
    public boolean audioIsPlaying() {
        return decoderThread != null && decoderThread.isAlive();
    }

    // Effects: returns true if audio is loaded
    public boolean audioIsLoaded() {
        return loadedFile != null;
    }
}
