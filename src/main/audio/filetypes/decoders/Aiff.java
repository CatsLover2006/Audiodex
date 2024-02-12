package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;

import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.tritonus.sampled.file.AiffAudioFileReader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static audio.filetypes.TagConversion.keyConv;
import static java.io.File.separatorChar;

// AIFF file decoder class
// It physically hurts to camel case this but checkstyle gets upset if I use all caps
public class Aiff implements AudioDecoder {
    private File file;
    private String filename;
    private AudioFormat format;
    private AudioInputStream in;
    private boolean ready = false;
    private final byte[] data = new byte[4096]; // 4kb sample buffers
    private int numberBytesRead = 0;
    private double bytesPerSecond;
    private long bytesPlayed = 0;
    private double duration;

    // Effects: returns true if audio can be decoded currently
    public boolean isReady() {
        return ready;
    }

    public Aiff(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    public void prepareToPlayAudio() {
        try {
            file = new File(filename);
            AudioFile f = AudioFileIO.read(file);
            AiffAudioFileReader reader = new AiffAudioFileReader();
            in = reader.getAudioInputStream(file);
            format = in.getFormat();
            double audioFrameRate = format.getFrameRate();
            int frameSize = format.getFrameSize();
            bytesPerSecond = (frameSize * audioFrameRate);
            duration = f.getAudioHeader().getPreciseTrackLength();
            System.out.println("AIFF decoder ready!");
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
            in.close();
        } catch (Exception e) {
            //lmao
        }
    }

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    public AudioSample getNextSample() {
        while (moreSamples()) {
            try {
                numberBytesRead = in.read(data, 0, data.length);
                if (numberBytesRead == -1) {
                    break;
                } // Yes I have to do this to track time
                bytesPlayed += numberBytesRead;
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
        try {
            prepareToPlayAudio(); // Reset doesn't work
            in.skip((long)(time * bytesPerSecond));
            bytesPlayed = (long)(time * bytesPerSecond);
        } catch (IOException e) {
            // RIP
        }
    }

    // Effects: returns the current time in the audio in seconds
    public double getCurrentTime() {
        return bytesPlayed / bytesPerSecond;
    }

    // Effects: returns the duration of the audio in seconds
    public double getFileDuration() {
        return duration;
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

    // Effects: returns decoded ID3 data
    public ID3Container getID3() {
        ID3Container base = new ID3Container();
        base.setID3Data("VBR", "NO");
        AudioFile f = null;
        try {
            f = AudioFileIO.read(file);
        } catch (Exception e) {
            return base;
        }
        base.setID3Data("bitRate", f.getAudioHeader().getBitRateAsNumber());
        base.setID3Data("sampleRate", f.getAudioHeader().getSampleRateAsNumber());
        Tag tag = f.getTag();
        for (Map.Entry<FieldKey, String> entry: keyConv.entrySet()) {
            try {
                Date d = Date.from(Instant.parse(tag.getFirst(entry.getKey())));
                base.setID3Long(entry.getValue(), String.valueOf(1900 + d.getYear()));
            } catch (Exception e) {
                base.setID3Long(entry.getValue(), tag.getFirst(entry.getKey()));
            }
        }
        return base;
    }

    // Effects: returns filename without directories
    public String getFileName() {
        String[] dirList = filename.split(String.valueOf(separatorChar));
        return dirList[dirList.length - 1];
    }

    public AudioFileType getFileType() {
        return AudioFileType.AIFF;
    }

    // Effects: returns an audio input stream for encoding data
    public AudioInputStream getAudioInputStream() {
        return in;
    }
}