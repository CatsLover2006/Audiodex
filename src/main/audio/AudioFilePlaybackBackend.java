package audio;

import ui.Main;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

// Backend for allowing interactions between the UI and filesystem
// Specific to decoding audio
// This class is beyond automated testing: multithreading
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
                    try { // Wait for decoder thread to finish, in case we've got a music queue
                        sleep(0, 1);
                    } catch (InterruptedException e) {
                        // LMAO just burn more time
                    }
                }
                Main.finishedSong();
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
            AudioSample sample;
            while (run && loadedFile.moreSamples()) {
                sample = loadedFile.getNextSample();
                line.write(sample.getData(), 0, sample.getLength());
                //System.out.println(sample.getLength()); // Debugging info
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
        if (!(workingData == null || workingData.equals("null") || workingData.isEmpty())) {
            base += " by " + workingData;
        }
        return base;
    }
}
