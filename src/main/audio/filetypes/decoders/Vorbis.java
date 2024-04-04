package audio.filetypes.decoders;

import audio.*;
import audio.filetypes.TagConversion;
import model.Event;
import model.EventLog;
import model.ExceptionIgnore;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import de.jarnbjo.ogg.*;
import de.jarnbjo.vorbis.*;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static audio.filetypes.TagConversion.keyConv;
import static java.io.File.separatorChar;

public class Vorbis implements AudioDecoder {
    private PhysicalOggStream stream;
    private VorbisStream decoded;
    private RandomAccessFile file;
    private String filename;
    private boolean ready = false;
    private int numberBytesRead = 0;
    private LogicalOggStream oggStream;
    private boolean skipping = false;
    private IdentificationHeader header;

    public Vorbis(String filename) {
        this.filename = filename;
    }

    // Returns filetype of decoder
    @Override
    public AudioFileType getFileType() {
        return AudioFileType.VORBIS;
    }

    // Effects: returns true if audio can be decoded currently
    @Override
    public boolean isReady() {
        return ready;
    }

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    @Override
    public void prepareToPlayAudio() {
        try {
            file = new RandomAccessFile(new File(filename), "r");
            stream = new FileStream(file);
            for (Object stream : stream.getLogicalStreams()) {
                if (stream instanceof LogicalOggStream) {
                    oggStream = (LogicalOggStream) stream;
                    if (oggStream.getFormat().equals(LogicalOggStream.FORMAT_VORBIS)) {
                        decoded = new VorbisStream(oggStream);
                        break;
                    }
                }
            }
            if (decoded == null) {
                throw new Exception("Incorrect file type");
            }
            header = decoded.getIdentificationHeader();
            ready = true;
            EventLog.getInstance().logEvent(new Event("Modern Vorbis decoder ready!"));
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
        ExceptionIgnore.ignoreExc(() -> {
            decoded.close();
            stream.close();
            oggStream.close();
            file.close();
        });
        ready = false;
    }

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    @Override
    public AudioSample getNextSample() {
        if (skipping) {
            return new AudioSample();
        }
        byte[] data = new byte[4096];
        while (moreSamples()) {
            try {
                numberBytesRead = decoded.readPcm(data, 0, data.length);
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
    @Override
    public void goToTime(double time) {
        skipping = true;
        try {
            oggStream.setTime((long) (time * header.getSampleRate()));
        } catch (Exception e) {
            ExceptionIgnore.ignoreExc(() -> oggStream.setTime(oggStream.getMaximumGranulePosition() - 1));
        }
        skipping = false;
    }

    // Effects: returns the current time in the audio in seconds
    @Override
    public double getCurrentTime() {
        if (header == null) {
            return -1;
        }
        return (double) oggStream.getTime() / header.getSampleRate();
    }

    // Effects: returns the duration of the audio in seconds
    @Override
    public double getFileDuration() {
        if (header == null) {
            return -1;
        }
        return (double) oggStream.getMaximumGranulePosition() / header.getSampleRate();
    }

    // Requires: prepareToPlayAudio() or setAudioOutputFormat() called once
    // Effects:  returns the audio format of the file
    @Override
    public AudioFormat getAudioOutputFormat() {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, header.getSampleRate(), 16,
                header.getChannels(), header.getChannels() * 2, header.getSampleRate(), true);
    }

    // Effects: returns true if there are more samples to be played
    //          will return false is no file is loaded
    @Override
    public boolean moreSamples() {
        if (decoded == null || !decoded.isOpen()) {
            return false;
        }
        return oggStream.getMaximumGranulePosition() > oggStream.getTime();
    }

    // Effects: returns decoded ID3 data
    @Override
    public ID3Container getID3() {
        ID3Container base = new ID3Container();
        base.setID3Data("VBR", "UNKNOWN");
        base.setID3Data("Title", getFileName());
        AudioFile f;
        try {
            f = AudioFileIO.read(new File(filename));
        } catch (Exception e) {
            return base;
        }
        base.setID3Data("VBR", f.getAudioHeader().isVariableBitRate() ? "YES" : "NO");
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
            }
        }
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
            f.getTag().setField(image);
            f.commit();
        });
    }

    // Effects: returns true if goToTime() is running
    //          only exists due to having multiple threads
    @Override
    public boolean skipInProgress() {
        return skipping;
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
