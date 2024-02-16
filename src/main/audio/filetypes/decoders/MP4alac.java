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
    private AudioInputStream in;
    private AudioInputStream decoded;
    private boolean ready = false;
    private final byte[] data = new byte[8192]; // 8kb sample buffer, lossless decoding might be expensive
    private int numberBytesRead = 0;
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
            InputStream input = new FileInputStream(file);
            AlacAudioFileReader fileReader = new AlacAudioFileReader();
            Alac alac = new Alac(new FileInputStream(file));
            totalSamples = alac.getNumSamples();
            in = fileReader.getAudioInputStream(file);
            AudioFormat baseFormat = in.getFormat();
            format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            decoded = AudioSystem.getAudioInputStream(format, in);
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
        try {
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Effects: wait for a set time
    private static void wait(int nanos) {
        try {
            sleep(0, nanos);
        } catch (InterruptedException e) {
            // Why?
        }
    }

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    public AudioSample getNextSample() {
        while (!allowSampleReads) {
            wait(1);
        }
        while (moreSamples()) {
            try {
                numberBytesRead = decoded.read(data, 0, data.length);
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
        long toSkip = (long)(time * bytesPerSecond) - bytesPlayed;
        allowSampleReads = false;
        if (toSkip < 0) {
            // Reset doesn't work
            prepareToPlayAudio();
            toSkip = (long)(time * bytesPerSecond);
            bytesPlayed = 0;
        }
        bytesPlayed += toSkip;
        try {
            while (toSkip != 0) { // It won't skip all the way
                toSkip -= decoded.skip(toSkip);
            }
        } catch (IOException e) {
            bytesPlayed -= toSkip;
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
}
