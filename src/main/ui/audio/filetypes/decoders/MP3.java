package ui.audio.filetypes.decoders;

import ui.audio.filetypes.AudioDecoder;
import ui.audio.AudioFileType;
import ui.audio.AudioSample;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import model.ID3Container;
import ui.audio.filetypes.TagConversion;
import javazoom.spi.mpeg.sampled.convert.DecodedMpegAudioInputStream;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import ui.Main;

import static java.io.File.separatorChar;

// MPEG-type audio file decoder class
public class MP3 implements AudioDecoder {
    private String filename;
    private AudioFormat format;
    private DecodedMpegAudioInputStream decoded;
    private AudioInputStream in;
    private boolean ready = false;
    private final byte[] data = new byte[4096]; // 4kb sample buffer, seems standard
    private int numberBytesRead = 0;
    private long duration;
    private double audioFrameRate;
    private boolean skipping = false;

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
            File file = new File(filename);
            InputStream input = new FileInputStream(file);
            MpegAudioFileReader fileReader = new MpegAudioFileReader();
            duration = (Long)(fileReader.getAudioFileFormat(input, file.length()).properties().get("duration"));
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
            System.out.println("MP3 decoder ready!");
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

    // Effects: returns true if goToTime() is running
    //          only exists due to having multiple threads
    public boolean skipInProgress() {
        return skipping;
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
        skipping = true;
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
        skipping = false;
    }

    // Effects: returns the current time in the audio in seconds
    public double getCurrentTime() {
        return (Long)decoded.properties().get("mp3.position.microseconds") * 0.000001;
    }

    // Effects: returns the duration of the audio in seconds
    public double getFileDuration() {
        return duration * 0.000001; // javax uses microseconds
    }

    // Requires: prepareToPlayAudio() called once
    // Effects:  returns the audio format of the file
    public AudioFormat getAudioOutputFormat() {
        return format;
    }

    // Effects:  returns true if there are more samples to be played
    //           will return false is no file is loaded
    public boolean moreSamples() {
        return numberBytesRead != -1;
    }

    // Effects: returns decoded ID3 data
    public ID3Container getID3() {
        ID3Container base = new ID3Container();
        base.setID3Data("VBR", "UNKNOWN");
        AudioFile f = getAudioFile(filename);
        if (f == null) {
            return null;
        }
        base.setID3Data("VBR", f.getAudioHeader().isVariableBitRate() ? "YES" : "NO");
        base.setID3Data("bitRate", f.getAudioHeader().getBitRateAsNumber());
        base.setID3Data("sampleRate", f.getAudioHeader().getSampleRateAsNumber());
        Tag tag = f.getTag();
        if (tag == null) {
            return null;
        }
        for (Map.Entry<FieldKey, String> entry: TagConversion.keyConv.entrySet()) {
            try {
                Date d = Date.from(Instant.parse(tag.getFirst(entry.getKey())));
                base.setID3Long(entry.getValue(), String.valueOf(1900 + d.getYear()));
            } catch (Exception e) {
                base.setID3Long(entry.getValue(), tag.getFirst(entry.getKey()));
            }
        }
        return base;
    }

    // Effects: returns AudioFile class
    private AudioFile getAudioFile(String filename) {
        try {
            return AudioFileIO.read(new File(filename));
        } catch (Exception e) {
            return null;
        }
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
            String data = container.getID3Data(entry.getKey()).toString();
            if (data != null) {
                try {
                    tag.setField(entry.getValue(), data);
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
        return AudioFileType.MP3;
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
}
