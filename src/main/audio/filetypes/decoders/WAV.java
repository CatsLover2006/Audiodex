package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import model.Event;
import model.EventLog;
import model.ExceptionIgnore;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.images.Artwork;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;

import static java.io.File.separatorChar;

// WAV file decoder class
public class WAV implements AudioDecoder {
    private File file;
    private final String filename;
    private AudioFormat format;
    private AudioInputStream in;
    private boolean ready = false;
    private final byte[] data = new byte[4096]; // 4kb sample buffers
    private int numberBytesRead = 0;
    private double bytesPerSecond;
    private long bytesPlayed = 0;
    private double duration;
    private boolean skipping = false;

    // Effects: returns true if audio can be decoded currently
    @Override
    public boolean isReady() {
        return ready;
    }

    public WAV(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    @Override
    public void prepareToPlayAudio() {
        try {
            file = new File(filename);
            in = AudioSystem.getAudioInputStream(file);
            format = in.getFormat();
            double audioFrameRate = format.getFrameRate();
            long audioFileLength = file.length();
            int frameSize = format.getFrameSize();
            bytesPerSecond = frameSize * audioFrameRate;
            duration = audioFileLength / (frameSize * audioFrameRate);
            EventLog.getInstance().logEvent(new Event("WAV/PCM decoder ready!"));
            ready = true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Requires: prepareToPlayAudio() called
    // Modifies: this
    // Effects:  unloads audio file, to save memory
    //           getAudioOutputFormat() and atEndOfFile() remain valid
    @Override
    public void closeAudioFile() {
        ready = false;
        ExceptionIgnore.ignoreExc(() -> in.close());
    }

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    @Override
    public AudioSample getNextSample() {
        numberBytesRead = -2;
        while (moreSamples()) {
            ExceptionIgnore.ignoreExc(() -> numberBytesRead = in.read(data, 0, data.length));
            if (numberBytesRead < 0) {
                continue;
            } // Yes I have to do this to track time
            bytesPlayed += numberBytesRead;
            return new AudioSample(data, numberBytesRead);
        }
        return new AudioSample();
    }

    // Effects: returns true if goToTime() is running
    //          only exists due to having multiple threads
    @Override
    public boolean skipInProgress() {
        return skipping;
    }

    // Effects: returns bytes per sample
    private int bytesPerSample() {
        return (format.getSampleSizeInBits() * format.getChannels()) / 8;
    }

    // Requires: prepareToPlayAudio() called
    //           0 <= time <= audio length
    // Modifies: this
    // Effects:  moves audio to a different point of the file
    @Override
    public void goToTime(double time) {
        skipping = true;
        ExceptionIgnore.ignoreExc(() -> {
            prepareToPlayAudio(); // Reset doesn't work
            bytesPlayed = (long) Math.min(time * bytesPerSecond, duration * bytesPerSecond);
            bytesPlayed = (long) (Math.floor(bytesPlayed / bytesPerSample()) * bytesPerSample());
            long toSkip = bytesPlayed;
            long skipped;
            while (toSkip != 0) {
                skipped = in.skip(toSkip);
                toSkip -= skipped;
                if (skipped == 0) {
                    numberBytesRead = -1;
                    skipping = false;
                    return;
                }
            }
        });
        skipping = false;
    }

    // Effects: returns the current time in the audio in seconds
    @Override
    public double getCurrentTime() {
        return bytesPlayed / bytesPerSecond;
    }

    // Effects: returns the duration of the audio in seconds
    @Override
    public double getFileDuration() {
        return duration;
    }

    // Requires: prepareToPlayAudio() called once
    // Effects:  returns the audio format of the file
    @Override
    public AudioFormat getAudioOutputFormat() {
        return format;
    }

    // Effects:  returns true if there are more samples to be played
    //           will return false is no file is loaded
    @Override
    public boolean moreSamples() {
        return numberBytesRead != -1;
    }

    // Effects: returns decoded ID3 data
    @Override
    public ID3Container getID3() {
        ID3Container base = new ID3Container();
        base.setID3Data("VBR", "NO");
        base.setID3Data("Title", getFileName());
        return base; // Virtual stub
    }

    // Modifies: file on filesystem
    // Effects:  updates ID3 data
    @Override
    public void setID3(ID3Container container) {
        // Can't do shit here
    }

    // Effects: returns filename without directories
    @Override
    public String getFileName() {
        int location = filename.lastIndexOf(separatorChar);
        if (location == -1) {
            location = 0;
        }
        return filename.substring(location + 1);
    }

    // Effects: returns album artwork if possible
    @Override
    public Artwork getArtwork() {
        return null;
        // LMAO what artwork
    }

    // Effects: sets the album artwork if possible
    @Override
    public void setArtwork(Artwork image) {
        // lol wat
    }
    
    // Effects: removes the album artwork if possible
    @Override
    public void removeArtwork() {
        // lol wat
    }

    // Returns filetype of decoder
    @Override
    public AudioFileType getFileType() {
        return AudioFileType.PCM_WAV;
    }

    // Effects: returns replaygain value
    //          defaults to -6
    @Override
    public float getReplayGain() {
        return -6; // Not stored
    }

}
