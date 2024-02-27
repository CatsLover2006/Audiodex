package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import audio.filetypes.TagConversion;
import com.beatofthedrum.alacdecoder.Alac;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import ui.Main;
import vavi.sound.sampled.alac.AlacAudioFileReader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static audio.filetypes.TagConversion.keyConv;
import static java.io.File.separatorChar;
import static java.lang.Thread.sleep;

public class MP4alac implements AudioDecoder {
    private String filename;
    private AudioFormat format;
    private double bytesPerSecond;
    private Alac alac;
    private boolean ready = false;
    private byte[] data;
    private int numberBytesRead = 1; // Fix
    private long totalSamples = 0;
    private boolean allowSampleReads = true;
    private long bytesPlayed = 0;

    // Effects: returns true if audio can be decoded currently
    public boolean isReady() {
        return ready;
    }

    public MP4alac(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    public void prepareToPlayAudio() {
        try {
            File file = new File(filename);
            alac = new Alac(new FileInputStream(file));
            totalSamples = alac.getNumSamples();
            format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    alac.getSampleRate(),
                    alac.getBitsPerSample(),
                    alac.getNumChannels(),
                    alac.getNumChannels() * alac.getBytesPerSample(),
                    alac.getSampleRate(),
                    false);
            data = new byte[0xFFFF]; // Make sure we have space
            bytesPerSecond = format.getSampleSizeInBits() * format.getChannels() * format.getSampleRate() / 8;
            System.out.println("ALAC decoder ready!");
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
    }

    // Effects: wait for a set time
    private static void wait(int nanos) {
        try {
            sleep(0, nanos);
        } catch (InterruptedException e) {
            // Why?
        }
    }

    // This is what the library does so idk
    private int[] decodeBuffer = new int[1024 * 24 * 3];

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    public AudioSample getNextSample() {
        while (!allowSampleReads) {
            wait(1);
        }
        while (moreSamples()) {
            numberBytesRead = alac.decode(decodeBuffer, data);
            if (numberBytesRead <= 0) {
                break;
            } // Yes I have to do this to track time
            bytesPlayed += numberBytesRead;
            return new AudioSample(data, numberBytesRead);
        }
        return new AudioSample();
    }

    // Effects: returns true if goToTime() is running
    //          only exists due to having multiple threads
    public boolean skipInProgress() {
        return !allowSampleReads;
    }

    // Requires: prepareToPlayAudio() called
    //           0 <= time <= audio length
    // Modifies: this
    // Effects:  moves audio to a different point of the file
    public void goToTime(double time) {
        allowSampleReads = false;
        if (getCurrentTime() > time) {
            prepareToPlayAudio(); // Reset
            decodeBuffer = new int[1024 * 24 * 3]; // Reset decoding buffer
            bytesPlayed = 0;
        }
        while (getCurrentTime() < time) {
            numberBytesRead = alac.decode(decodeBuffer, data);
            if (numberBytesRead <= 0) {
                break;
            } // Yes I have to do this to track time
            bytesPlayed += numberBytesRead;
        }
        allowSampleReads = true;
    }

    // Effects: returns the current time in the audio in seconds
    public double getCurrentTime() {
        return bytesPlayed / bytesPerSecond;
    }

    // Effects: returns the duration of the audio in seconds
    public double getFileDuration() {
        return totalSamples / format.getSampleRate();
    }

    // Requires: prepareToPlayAudio() called once
    // Effects:  returns the audio format of the file
    public AudioFormat getAudioOutputFormat() {
        return format;
    }

    // Effects:  returns true if there are more samples to be played
    //           will return false is no file is loaded
    public boolean moreSamples() {
        return numberBytesRead > 0;
    }

    // Effects: returns decoded ID3 data
    public ID3Container getID3() {
        ID3Container base = new ID3Container();
        base.setID3Data("VBR", "NO");
        AudioFile f = null;
        try {
            f = AudioFileIO.read(new File(filename));
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

    // Modifies: file on filesystem
    // Effects:  updates ID3 data
    public void setID3(ID3Container container) {
        AudioFile f = null;
        try {
            f = AudioFileIO.read(new File(filename));
        } catch (Exception e) {
            return;
        }
        Tag tag = f.getTagOrCreateAndSetDefault();
        for (Map.Entry<String, FieldKey> entry : TagConversion.valConv.entrySet()) {
            Object data = container.getID3Data(entry.getKey());
            if (data != null) {
                try {
                    tag.setField(entry.getValue(), data.toString());
                } catch (FieldDataInvalidException e) {
                    Main.CliInterface.println("Failed to set " + entry.getKey() + " to " + data);
                }
            }
        }
        try {
            f.commit();
        } catch (CannotWriteException e) {
            Main.CliInterface.println("Failed to write to file.");
        }
    }

    // Effects: returns filename without directories
    public String getFileName() {
        String[] dirList = filename.split(String.valueOf(separatorChar));
        return dirList[dirList.length - 1];
    }

    public AudioFileType getFileType() {
        return AudioFileType.ALAC_MP4;
    }

    // Effects: returns album artwork if possible
    public Artwork getArtwork() {
        AudioFile f = null;
        try {
            f = AudioFileIO.read(new File(filename));
            Tag tag = f.getTag();
            for (Artwork art : tag.getArtworkList()) {
                if (art.getPictureType() == 0) {
                    return art;
                }
            }
            return tag.getFirstArtwork();
        } catch (Exception e) {
            return null;
        }
    }

    // Effects: sets the album artwork if possible
    public void setArtwork(Artwork image) {
        AudioFile f = null;
        try {
            f = AudioFileIO.read(new File(filename));
            f.getTag().setField(image);
            f.commit();
        } catch (Exception e) {
            // Why?
        }
    }

    // Modifies: this
    // Effects:  force disables decoding
    //           for use in tests only
    public void forceDisableDecoding() {
        allowSampleReads = false;
    }

    // Modifies: this
    // Effects:  force enables decoding
    //           for use in tests only
    public void forceEnableDecoding() {
        allowSampleReads = true;
    }

    // Effects: returns replaygain value
    //          defaults to -6
    public float getReplayGain() {
        AudioFile f = null;
        try {
            f = AudioFileIO.read(new File(filename));
            return TagConversion.getReplayGain(f.getTag());
        } catch (Exception e) {
            // Why?
        }
        return -6;
    }
}
