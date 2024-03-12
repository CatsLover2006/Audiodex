package audio.filetypes.decoders;

import audio.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.*;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import audio.filetypes.TagConversion;
import javazoom.spi.mpeg.sampled.convert.DecodedMpegAudioInputStream;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import model.ExceptionIgnore;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.images.Artwork;

import static audio.filetypes.TagConversion.*;
import static java.io.File.separatorChar;

// MPEG-type audio file decoder class
public class MpegType implements AudioDecoder {
    private final String filename;
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
    @Override
    public boolean isReady() {
        return ready;
    }

    public MpegType(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    @Override
    public void prepareToPlayAudio() {
        try {
            File file = new File(filename);
            file.getCanonicalFile(); // Create IOException on invalid paths
            InputStream input = new FileInputStream(file);
            MpegAudioFileReader fileReader = new MpegAudioFileReader();
            duration = (Long) fileReader.getAudioFileFormat(input, file.length()).properties().get("duration");
            in = fileReader.getAudioInputStream(file);
            AudioFormat baseFormat = in.getFormat();
            format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(), 16, baseFormat.getChannels(),
                    baseFormat.getChannels() * 2, baseFormat.getSampleRate(), false);
            decoded = new DecodedMpegAudioInputStream(format, in);
            audioFrameRate = baseFormat.getFrameRate();
            System.out.println("MP3 decoder ready!");
            ready = true;
        } catch (FileNotFoundException e) {
            System.out.println("Error in encoding");
            return; // We don't set ready flag
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
        ExceptionIgnore.ignoreExc(() ->  {
            decoded.close();
            in.close();
        });
    }

    // Effects: returns true if goToTime() is running
    //          only exists due to having multiple threads
    @Override
    public boolean skipInProgress() {
        return skipping;
    }

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    @Override
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
            } catch (ArrayIndexOutOfBoundsException e) {
                decoded.skip(1); // Skip the bad byte
            }
        }
        return new AudioSample();
    }

    // Requires: prepareToPlayAudio() called
    //           0 <= time <= audio length
    // Modifies: this
    // Effects:  moves audio to a different point of the file
    @Override
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
    @Override
    public double getCurrentTime() {
        if (decoded == null) {
            return -1;
        } // format.getChannels() is a workaround for a library bug
        return (Long)decoded.properties().get("mp3.position.microseconds") * 0.000002 / format.getChannels();
    }

    // Effects: returns the duration of the audio in seconds
    @Override
    public double getFileDuration() { // format.getChannels() is a workaround for a library bug
        return duration * 0.000002 / format.getChannels(); // javax uses microseconds
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
        if (!ready) {
            return null;
        } // We readyn't
        ID3Container base = new ID3Container();
        base.setID3Data("VBR", "UNKNOWN");
        base.setID3Data("Title", getFileName());
        MP3File f = (MP3File)getAudioFile(filename);
        if (f == null) {
            return base;
        }
        base.setID3Data("VBR", f.getAudioHeader().isVariableBitRate() ? "YES" : "NO");
        base.setID3Data("bitRate", f.getAudioHeader().getBitRateAsNumber());
        base.setID3Data("sampleRate", f.getAudioHeader().getSampleRateAsNumber());
        getID3v1(f, base);
        getID3v2(f, base);
        return base;
    }

    // Effects: gets relevant ID3v1 tags
    private static void getID3v1(MP3File f, ID3Container base) {
        Tag tag = f.getTag();
        if (tag == null) {
            return;
        }
        for (Map.Entry<FieldKey, String> entry: keyConv.entrySet()) {
            try {
                Date d = Date.from(Instant.parse(tag.getFirst(entry.getKey())));
                base.setID3Long(entry.getValue(), String.valueOf(1900 + d.getYear()));
            } catch (Exception e) {
                base.setID3Long(entry.getValue(), tag.getFirst(entry.getKey()));
            }
        }
    }

    // Effects: gets relevant ID3v2 tags
    private static void getID3v2(MP3File f, ID3Container base) {
        if (!f.hasID3v2Tag()) {
            return;
        }
        ID3v24Tag v24tag = f.getID3v2TagAsv24();
        for (Map.Entry<String, String> entry: id3v2keyConv.entrySet()) {
            try {
                Date d = Date.from(Instant.parse(v24tag.getFirst(entry.getKey())));
                base.setID3Long(entry.getValue(), String.valueOf(1900 + d.getYear()));
            } catch (Exception e) {
                ExceptionIgnore.ignoreExc(() -> base.setID3Long(entry.getValue(), v24tag.getFirst(entry.getKey())));
            }
        }
    }

    // Effects: returns AudioFile class
    private static AudioFile getAudioFile(String filename) {
        try {
            return AudioFileIO.readAs(new File(filename), "mp3");
        } catch (Exception e) {
            return null;
        }
    }

    // Modifies: file on filesystem
    // Effects:  updates ID3 data
    @Override
    public void setID3(ID3Container container) {
        MP3File f = (MP3File)getAudioFile(filename);
        if (f == null) {
            return;
        }
        setID3v1(f, container);
        //setID3v2(f, container); // Unimplemented in library
        try {
            f.commit();
        } catch (CannotWriteException e) {
            System.out.println("Failed to write to file.");
        }
    }

    // Effects: sets relevant ID3v1 tags
    private static void setID3v1(MP3File f, ID3Container container) {
        Tag tag = f.getTagOrCreateAndSetDefault();
        if (tag == null) {
            return;
        }
        for (Map.Entry<String, FieldKey> entry : valConv.entrySet()) {
            Object data = container.getID3Data(entry.getKey());
            if (data != null) {
                try {
                    tag.setField(entry.getValue(), data.toString());
                } catch (FieldDataInvalidException e) {
                    System.out.println("Failed to set " + entry.getKey() + " to " + data);
                }
            }
        }
    }

    /* Effects: sets relevant ID3v2 tags
    private static void setID3v2(MP3File f, ID3Container container) {
        if (!f.hasID3v2Tag()) {
            f.setID3v2Tag(new ID3v24Tag());
        }
        ID3v24Tag v24tag = f.getID3v2TagAsv24();
        if (v24tag == null) {
            return;
        }
        for (Map.Entry<String, String> entry : id3v2valConv.entrySet()) {
            Object data = container.getID3Data(entry.getKey());
            if (data != null) {
                ID3v24Frame frame = v24tag.createFrame(entry.getValue());
                frame.setContent(data.toString());
                v24tag.mergeDuplicateFrames(frame);
            }
        }
    }//*///

    // Effects: returns filename without directories
    @Override
    public String getFileName() {
        String[] dirList = filename.split(String.valueOf(separatorChar));
        return dirList[dirList.length - 1];
    }

    // Returns filetype of decoder
    @Override
    public AudioFileType getFileType() {
        return AudioFileLoader.getAudioFiletype(filename);
    }

    // Effects: returns album artwork if possible
    @Override
    public Artwork getArtwork() {
        AudioFile f;
        try {
            f = AudioFileIO.readAs(new File(filename), "mp3");
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
        ExceptionIgnore.ignoreExc(() ->  {
            AudioFile f = AudioFileIO.readAs(new File(filename), "mp3");
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
        AudioFile f;
        try {
            f = AudioFileIO.read(new File(filename));
            return TagConversion.getReplayGain(f.getTag());
        } catch (Exception e) {
            // Why?
        }
        return -6;
    }
}
