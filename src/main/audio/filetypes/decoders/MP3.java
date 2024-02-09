package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioSample;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javazoom.spi.mpeg.sampled.convert.DecodedMpegAudioInputStream;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileFormat;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;

import static java.lang.Math.random;

public class MP3 implements AudioDecoder {
    private MpegAudioFileReader fileReader;
    private File file;
    private String filename;
    private AudioFormat format;
    private DecodedMpegAudioInputStream decoded;
    private AudioInputStream in;
    private boolean ready = false;
    private byte[] data = new byte[4096]; // 4kb sample buffer, seems standard
    private int numberBytesRead = 0;
    private long duration;
    private double audioFrameRate;

    // Effects: returns true if audio can be decoded currently
    public boolean isReady() {
        return ready;
    }

    public MP3(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    public void prepareToPlayAudio() {
        try {
            file = new File(filename);
            fileReader = new MpegAudioFileReader();
            duration = (Long)(fileReader.getAudioFileFormat(file).properties().get("duration"));
            in = fileReader.getAudioInputStream(file);
            AudioFormat baseFormat = in.getFormat();
            format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            decoded = new DecodedMpegAudioInputStream(format, in);
            audioFrameRate = baseFormat.getFrameRate();
            ready = true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Requires: prepareToPlayAudio() called
    // Modifies: this
    // Effects:  unloads audio file, to save memory
    //           getAudioOutputFormat() and atEndOfFile() remain valid
    public void closeAudioFile() {
        ready = false;
        try {
            decoded.close();
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    public AudioSample getNextSample() {
        while (moreSamples()) {
            try {
                numberBytesRead = decoded.read(data, 0, data.length);
                if (numberBytesRead == -1) {
                    break;
                }
                return new AudioSample(data, numberBytesRead);
            } catch (IOException e) {
                // Move along
            }
        }
        return new AudioSample();
    }

    // Requires: prepareToPlayAudio() called
    //           0 <= time <= audio length
    // Modifies: this
    // Effects:  moves audio to a different point of the file
    public void goToTime(double time) {
        long toSkip = (long)(time * audioFrameRate) // Target frame
                - (Long)decoded.properties().get("mp3.frame"); // Minus current frame
        if (toSkip < 0) { // We can't skip backwards for some reason
            try {
                decoded.reset();
            } catch (IOException e) {
                prepareToPlayAudio(); // Backup
            }
            toSkip = (long)(time * audioFrameRate);
        }
        decoded.skipFrames(toSkip);
    }

    // Effects: returns the current time in the audio in seconds
    public double getCurrentTime() {
        return (Long)decoded.properties().get("mp3.position.microseconds") * 0.000001;
    }

    // Effects: returns the duration of the audio in seconds
    public double getFileDuration() {
        return duration * 0.000001; // javax uses microseconds
    }

    // Requires: prepareToPlayAudio() or setAudioOutputFormat() called once
    // Effects:  returns the audio format of the file
    public AudioFormat getAudioOutputFormat() {
        return format;
    }

    // Requires: prepareToPlayAudio() has never been called
    //           won't crash but is pointless
    // Modifies: this
    // Effects:  sets the audio format of the file
    public void setAudioOutputFormat(AudioFormat format) {
        this.format = format;
    }

    // Effects:  returns true if there are more samples to be played
    //           will return false is no file is loaded
    public boolean moreSamples() {
        return numberBytesRead != -1;
    }
}
