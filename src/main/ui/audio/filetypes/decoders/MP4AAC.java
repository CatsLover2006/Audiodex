package ui.audio.filetypes.decoders;

import ui.audio.filetypes.AudioDecoder;
import ui.audio.AudioFileType;
import ui.audio.AudioSample;
import model.ID3Container;
import ui.audio.filetypes.TagConversion;
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
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import ui.Main;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static ui.audio.filetypes.TagConversion.keyConv;
import static java.io.File.separatorChar;

// Audio decoder for the MP4 file type
// Only supports AAC MP4 files but to my knowledge there are no other
// audio formats that use MP4 as a primary
public class MP4AAC implements AudioDecoder {
    Frame frame;
    private AudioTrack tracks;
    private AudioFormat audioFormat;
    private boolean ready = false;
    private String filename;
    private Decoder decoder;
    private final SampleBuffer buffer = new SampleBuffer();
    private RandomAccessFile file;
    private double duration;
    private boolean skipping = false;

    public boolean isReady() {
        return ready;
    }

    // Effects: it's a constructor
    public MP4AAC(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects: loads audio and makes all other functions valid
    public void prepareToPlayAudio() {
        try {
            //cachedID3 = makeID3();
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
        }
    }


    // Requires: prepareToPlayAudio() called
    // Modifies: this
    // Effects:  unloads audio file, to save memory
    //           getAudioOutputFormat() and atEndOfFile() remain valid
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
    public boolean skipInProgress() {
        return skipping;
    }

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
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
    public void goToTime(double time) {
        skipping = true;
        tracks.seek(time);
        skipping = false;
    }

    // Requires: prepareToPlayAudio() called once
    // Effects:  returns the audio format of the file
    public AudioFormat getAudioOutputFormat() {
        return audioFormat;
    }

    // Effects: returns the current time in the audio in seconds
    public double getCurrentTime() {
        if (frame == null) {
            return -1;
        }
        return frame.getTime();
    }

    public double getFileDuration() {
        return duration;
    }

    // Effects:  returns true if there are more samples to be played
    //           will return false is no file is loaded
    public boolean moreSamples() {
        if (!ready) {
            return false;
        }
        return tracks.hasMoreFrames();
    }

    // Effects: returns decoded ID3 data
    public ID3Container getID3() {
        ID3Container base = new ID3Container();
        base.setID3Data("VBR", "UNKNOWN");
        base.setID3Data("bitRate", tracks.getSampleSize());
        base.setID3Data("sampleRate", tracks.getSampleRate());
        File file = new File(filename);
        AudioFile f = null;
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
        return AudioFileType.AAC_MP4;
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
