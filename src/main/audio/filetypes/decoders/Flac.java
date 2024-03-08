package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import audio.filetypes.TagConversion;
import model.ExceptionIgnore;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.kc7bfi.jflac.FLACDecoder;
import org.kc7bfi.jflac.metadata.StreamInfo;
import org.kc7bfi.jflac.sound.spi.FlacAudioFormat;
import org.kc7bfi.jflac.util.ByteData;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static audio.filetypes.TagConversion.keyConv;
import static java.io.File.separatorChar;

// FLAC decoder class
// TODO: implement skipping
public class Flac implements AudioDecoder {
    private StreamInfo info;
    private String filename;
    private FLACDecoder decoder;
    private long bytesPlayed = 0;
    private boolean skipping = false;
    private FileInputStream in;


    // Decode a single frame and converts the returned ByteData to an AudioSample (not hard lmao)
    private AudioSample decodeFrame() {
        try {
            ByteData byteData = decoder.decodeFrame(decoder.readNextFrame(),
                    new ByteData(info.getBitsPerSample() * 128));
            return new AudioSample(byteData.getData(), byteData.getLen());
        } catch (Exception e) {
            return new AudioSample();
        }
    }

    public Flac(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    @Override
    public void prepareToPlayAudio() {
        try {
            in = new FileInputStream(filename);
            decoder = new FLACDecoder(in);
            try {
                info = decoder.readStreamInfo();
            } catch (IOException e) {
                decoder.decode();
                info = decoder.getStreamInfo();
            }
            System.out.println("FLAC decoder ready!");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Returns filetype of decoder
    @Override
    public AudioFileType getFileType() {
        return AudioFileType.FLAC;
    }

    // Getter on other decoders, this one works very differently
    // We're not ready if the decoder isn't present
    @Override
    public boolean isReady() {
        return decoder != null;
    }

    // Effects: returns filename without directories
    @Override
    public String getFileName() {
        String[] dirList = filename.split(String.valueOf(separatorChar));
        return dirList[dirList.length - 1];
    }

    // Effects:  returns true if there are more samples to be played
    //           will return false is no file is loaded
    @Override
    public boolean moreSamples() {
        if (decoder == null) {
            return false;
        }
        return !decoder.isEOF();
    }

    // Requires: prepareToPlayAudio() called
    // Modifies: this
    // Effects:  unloads audio file, to save memory
    //           getAudioOutputFormat() remains valid
    @Override
    public void closeAudioFile() {
        decoder = null;
    }

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    @Override
    public AudioSample getNextSample() {
        while (skipping) {
            ExceptionIgnore.ignoreExc(() -> wait(0, 1));
        }
        if (moreSamples()) {
            AudioSample sample = decodeFrame();
            bytesPlayed += sample.getLength();
            return sample;
        }
        return new AudioSample();
    }

    // Effects: returns the current time in the audio in seconds
    @Override
    public double getCurrentTime() {
        return bytesPlayed / (info.getSampleRate() * info.getBitsPerSample() * info.getChannels() / 8.0);
    }

    // Requires: prepareToPlayAudio() called
    //           0 <= time <= audio length
    // Modifies: this
    // Effects:  moves audio to a different point of the file
    @Override
    public void goToTime(double time) {
        skipping = true;
        if (getCurrentTime() > time) {
            try {
                decoder.seek(0);
            } catch (IOException e) {
                prepareToPlayAudio();
            }
            bytesPlayed = 0;
        }
        while (time > getCurrentTime()) {
            bytesPlayed += decodeFrame().getLength();
            if (time >= getFileDuration()) {
                break;
            }
        }
        skipping = false;
    }

    // Effects: returns the duration of the audio in seconds
    @Override
    public double getFileDuration() {
        return (double) info.getTotalSamples() / info.getSampleRate();
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
        AudioFile f;
        try {
            f = AudioFileIO.read(new File(filename));
            return TagConversion.getReplayGain(f.getTag());
        } catch (Exception e) {
            // Why?
        }
        return -6;
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
        ExceptionIgnore.ignoreExc(() ->  {
            AudioFile f = AudioFileIO.read(new File(filename));
            f.getTag().setField(image);
            f.commit();
        });
    }

    // Effects: returns decoded ID3 data
    @Override
    public ID3Container getID3() {
        ID3Container base = new ID3Container();
        base.setID3Data("VBR", "UNKNOWN");
        AudioFile f;
        try {
            base.setID3Data("bitRate", info.getBitsPerSample());
            base.setID3Data("sampleRate", info.getSampleRate());
            File file = new File(filename);
            f = AudioFileIO.readAs(file, "flac");
        } catch (Exception e) {
            return base;
        }
        base.setID3Data("VBR", f.getAudioHeader().isVariableBitRate() ? "YES" : "NO");
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
                try {
                    tag.setField(entry.getValue(), data.toString());
                } catch (Exception e) {
                    System.out.println("Failed to set " + entry.getKey() + " to " + data);
                }
            }
        }
        try {
            f.commit();
        } catch (CannotWriteException e) {
            System.out.println("Failed to write to file.");
        }
    }

    // Requires: prepareToPlayAudio() called once
    // Effects:  returns the audio format of the file
    @Override
    public AudioFormat getAudioOutputFormat() {
        AudioFormat flacFormat = new FlacAudioFormat(info);
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                info.getSampleRate(),
                info.getBitsPerSample(),
                info.getChannels(),
                info.getChannels() * info.getBitsPerSample() / 8,
                info.getSampleRate(),
                false);
    }
}
