package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import audio.filetypes.TagConversion;
import model.ExceptionIgnore;
import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import net.sourceforge.jaad.mp4.api.Frame;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.api.Track;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static audio.filetypes.TagConversion.keyConv;
import static java.io.File.separatorChar;

// Audio decoder for the MP4 file type with an AAC codec
public class MP4AAC implements AudioDecoder {
    Frame frame;
    private AudioTrack tracks;
    private AudioFormat audioFormat;
    private boolean ready = false;
    private final String filename;
    private Decoder decoder;
    private final SampleBuffer buffer = new SampleBuffer();
    private RandomAccessFile file;
    private double duration;
    private boolean skipping = false;

    @Override
    public boolean isReady() {
        return ready;
    }

    // Effects: it's a constructor
    public MP4AAC(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects: loads audio and makes all other functions valid
    @Override
    public void prepareToPlayAudio() {
        try {
            file = new RandomAccessFile(filename, "r");
            MP4Container container = new MP4Container(file);
            Movie movie = container.getMovie();
            duration = movie.getDuration();
            List<Track> tracks = movie.getTracks(AudioTrack.AudioCodec.AAC);
            if (tracks.isEmpty()) {
                file.close();
                file = null;
                return;
            }
            this.tracks = (AudioTrack) tracks.get(0);
            audioFormat = new AudioFormat(this.tracks.getSampleRate(), this.tracks.getSampleSize(),
                    this.tracks.getChannelCount(), true, true);
            decoder = new Decoder(this.tracks.getDecoderSpecificInfo());
            ready = true;
            System.out.println("AAC decoder ready!");
        } catch (IOException e) {
            ready = false;
            throw new RuntimeException(e);
        }
    }


    // Requires: prepareToPlayAudio() called
    // Modifies: this
    // Effects:  unloads audio file, to save memory
    //           getAudioOutputFormat() and atEndOfFile() remain valid
    @Override
    public void closeAudioFile() {
        try {
            file.close();
            file = null;
            ready = false;
        } catch (IOException e) {
            ready = false;
        } catch (NullPointerException e) {
            file = null;
            ready = false;
        }
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
                frame = tracks.readNextFrame();
                decoder.decodeFrame(frame.getData(), buffer);
                return new AudioSample(buffer.getData());
            } catch (AACException e) {
                e.printStackTrace();
            } catch (IOException e) {
                return new AudioSample();
            }
            // If we encountered an error, just move along to the next sample
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
        tracks.seek(time);
        skipping = false;
    }

    // Requires: prepareToPlayAudio() called once
    // Effects:  returns the audio format of the file
    @Override
    public AudioFormat getAudioOutputFormat() {
        return audioFormat;
    }

    // Effects: returns the current time in the audio in seconds
    @Override
    public double getCurrentTime() {
        if (frame == null) {
            return -1;
        }
        return frame.getTime();
    }

    @Override
    public double getFileDuration() {
        return duration;
    }

    // Effects:  returns true if there are more samples to be played
    //           will return false is no file is loaded
    @Override
    public boolean moreSamples() {
        if (!ready) {
            return false;
        }
        return tracks.hasMoreFrames();
    }

    // Effects: returns decoded ID3 data
    @Override
    public ID3Container getID3() {
        ID3Container base = new ID3Container();
        base.setID3Data("VBR", "UNKNOWN");
        base.setID3Data("Title", getFileName());
        base.setID3Data("bitRate", tracks.getSampleSize());
        base.setID3Data("sampleRate", tracks.getSampleRate());
        File file = new File(filename);
        AudioFile f;
        try {
            f = AudioFileIO.read(file);
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
        return AudioFileType.AAC_MP4;
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
