package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import audio.filetypes.TagConversion;
import model.Event;
import model.EventLog;
import model.ExceptionIgnore;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;

import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.tritonus.sampled.file.AiffAudioFileReader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static audio.filetypes.TagConversion.keyConv;
import static java.io.File.separatorChar;

// AIFF file decoder class
// It physically hurts to camel case this but checkstyle gets upset if I use all caps
public class Aiff implements AudioDecoder {
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

    public Aiff(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    @Override
    public void prepareToPlayAudio() {
        try {
            file = new File(filename);
            AudioFile f = AudioFileIO.read(file);
            AiffAudioFileReader reader = new AiffAudioFileReader();
            in = reader.getAudioInputStream(file);
            format = in.getFormat();
            double audioFrameRate = format.getFrameRate();
            int frameSize = format.getFrameSize();
            bytesPerSecond = frameSize * audioFrameRate;
            duration = f.getAudioHeader().getPreciseTrackLength();
            EventLog.getInstance().logEvent(new Event("AIFF decoder ready!"));
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
        if (bytesPlayed == Math.round(duration * bytesPerSecond)) {
            numberBytesRead = -1;
        }
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
        AudioFile f;
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

    // Modifies: file on filesystem
    // Effects:  updates ID3 data
    @Override
    public void setID3(ID3Container container) {
        AudioFile f;
        try {
            f = AudioFileIO.read(new File(filename));
        } catch (Exception e) {
            return;
        }
        Tag tag = f.getTagOrCreateAndSetDefault();
        for (Map.Entry<String, FieldKey> entry : TagConversion.valConv.entrySet()) {
            Object data = container.getID3Data(entry.getKey());
            if (data != null) {
                ExceptionIgnore.ignoreExc(() -> tag.setField(entry.getValue(), data.toString()));
            } else if (tag.hasField(entry.getValue())) {
                ExceptionIgnore.ignoreExc(() -> tag.deleteField(entry.getValue()));
            }
        }
        f.setTag(tag);
        ExceptionIgnore.ignoreExc(() -> f.commit());
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

    // Returns filetype of decoder
    @Override
    public AudioFileType getFileType() {
        return AudioFileType.AIFF;
    }

    // Effects: returns album artwork if possible
    @Override
    public Artwork getArtwork() {
        AudioFile f;
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
    @Override
    public void setArtwork(Artwork image) {
        ExceptionIgnore.ignoreExc(() -> {
            AudioFile f = AudioFileIO.read(new File(filename));
            while (!f.getTag().getArtworkList().isEmpty()) {
                f.getTag().deleteArtworkField();
            } // It duplicates artwork if I don't do this and idk why
            f.getTag().setField(image);
            f.commit();
        });
    }

    // Effects: returns replaygain value
    //          defaults to -6
    @Override
    public float getReplayGain() {
        float[] ret = new float[] {-6};
        ExceptionIgnore.ignoreExc(() ->  {
            AudioFile f = AudioFileIO.read(new File(filename));
            ret[0] = TagConversion.getReplayGain(f.getTag());
        });
        return ret[0];
    }
}