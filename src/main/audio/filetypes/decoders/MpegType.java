package audio.filetypes.decoders;

import audio.*;
import audio.filetypes.TagConversion;
import model.Event;
import model.EventLog;
import model.ExceptionIgnore;
import net.sourceforge.lame.lowlevel.LameDecoder;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.images.Artwork;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static audio.filetypes.TagConversion.id3v2keyConv;
import static audio.filetypes.TagConversion.keyConv;
import static java.io.File.separatorChar;

public class MpegType implements AudioDecoder {
    private String filename;
    private boolean ready = false;
    private LameDecoder decoder;
    private ByteBuffer buffer;
    private boolean hasSamples;
    private double length = -2;
    private long samplesPlayed;
    private boolean skipping = false;
    private int decodedSize;

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
            makeDecoder();
            if (length == -2) {
                MP3File f = (MP3File) getAudioFile(filename);
                if (f == null) {
                    length = -1;
                } else {
                    length = f.getAudioHeader().getPreciseTrackLength();
                }
            }
            samplesPlayed = 0;
            hasSamples = true;
            ready = true;
            EventLog.getInstance().logEvent(new Event("Modern MPEG-type decoder ready!"));
        } catch (Exception e) {
            ExceptionIgnore.logException(e);
            ready = false;
        }
    }

    // Effects: shrinks byte buffer to fit (and loads audio)
    //          some files play at half speed at the default buffer size due excess bytes at the end
    private void makeDecoder() {
        decoder = new LameDecoder(filename);
        buffer = ByteBuffer.allocate(decoder.getBufferSize());
        for (int i = 0; i < decoder.getBufferSize(); i++) {
            buffer.put(i, (byte) 0xFF);
        }
        decoder.decode(buffer);
        for (decodedSize = decoder.getBufferSize(); buffer.get(decodedSize - 1) == (byte) 0xFF; decodedSize--) {
            // It's all in the for statement
        }
        for (int i = 0; i < decoder.getBufferSize(); i++) {
            buffer.put(i, (byte) 0x00);
        }
        decoder.decode(buffer);
        for (; buffer.get(decodedSize - 1) != (byte) 0x00; decodedSize++) {
            // It's all in the for statement
        }
        decoder = new LameDecoder(filename);
        EventLog.getInstance().logEvent(new Event("Got decoder output size: " + decodedSize));
    }

    // Requires: prepareToPlayAudio() called
    // Modifies: this
    // Effects:  unloads audio file, to save memory
    //           getAudioOutputFormat() and atEndOfFile() remain valid
    @Override
    public void closeAudioFile() {
        ready = false;
    }

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    @Override
    public AudioSample getNextSample() {
        if (skipping) {
            return new AudioSample();
        }
        hasSamples = decoder.decode(buffer);
        byte[] samples = buffer.array();
        AudioSample sample = new AudioSample(samples, decodedSize);
        samplesPlayed += decodedSize / 2;
        return sample;
    }

    // Requires: prepareToPlayAudio() called
    //           0 <= time <= audio length
    // Modifies: this
    // Effects:  moves audio to a different point of the file
    @Override
    public void goToTime(double time) {
        skipping = true;
        if (getCurrentTime() > time) {
            prepareToPlayAudio();
        }
        while (hasSamples && getCurrentTime() < time) {
            hasSamples = decoder.decode(buffer);
            samplesPlayed += decodedSize / 2;
        }
        skipping = false;
    }

    // Effects: returns the current time in the audio in seconds
    @Override
    public double getCurrentTime() {
        return (double)samplesPlayed / (decoder.getSampleRate() * decoder.getChannels());
    }

    // Effects: returns the duration of the audio in seconds
    @Override
    public double getFileDuration() {
        return length;
    }

    // Requires: prepareToPlayAudio() or setAudioOutputFormat() called once
    // Effects:  returns the audio format of the file
    @Override
    public AudioFormat getAudioOutputFormat() {
        return new AudioFormat(decoder.getSampleRate(), 16, decoder.getChannels(), true, false);
    }

    // Effects: returns true if there are more samples to be played
    //          will return false is no file is loaded
    @Override
    public boolean moreSamples() {
        return hasSamples;
    }

    // Effects: returns true if goToTime() is running
    //          only exists due to having multiple threads
    @Override
    public boolean skipInProgress() {
        return skipping;
    }

    // Returns filetype of decoder
    @Override
    public AudioFileType getFileType() {
        return AudioFileLoader.getAudioFiletype(filename);
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
        ExceptionIgnore.ignoreExc(() -> f.commit());
    }

    // Effects: sets relevant ID3v1 tags
    private static void setID3v1(MP3File f, ID3Container container) {
        Tag tag = f.getTagOrCreateAndSetDefault();
        if (tag == null) {
            return;
        }
        for (Map.Entry<String, FieldKey> entry : TagConversion.valConv.entrySet()) {
            Object data = container.getID3Data(entry.getKey());
            if (data != null) {
                ExceptionIgnore.ignoreExc(() -> tag.setField(entry.getValue(), data.toString()));
            } else if (tag.hasField(entry.getValue())) {
                ExceptionIgnore.ignoreExc(() -> tag.deleteField(entry.getValue()));
            }
        }
        f.setTag(tag);
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
        float[] ret = new float[] {-6};
        ExceptionIgnore.ignoreExc(() ->  {
            AudioFile f = AudioFileIO.readAs(new File(filename), "mp3");
            ret[0] = TagConversion.getReplayGain(f.getTag());
        });
        return ret[0];
    }
}
