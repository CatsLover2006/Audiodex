package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioSample;
import audio.ID3Container;
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
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import ui.Main;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.io.File.separatorChar;

// Audio decoder for the MP4 file type
// Only supports AAC MP4 files but to my knowledge there are no other
// audio formats that use MP4 as a primary
public class MP4 implements AudioDecoder {
    Frame frame;
    private MP4Container container;
    private AudioTrack tracks;
    private AudioFormat audioFormat;
    private boolean ready = false;
    private String filename;
    private Decoder decoder;
    private SampleBuffer buffer = new SampleBuffer();
    private RandomAccessFile file;
    private double duration;
    private ID3Container cachedID3;

    public boolean isReady() {
        return ready;
    }

    // Effects: it's a constructor
    public MP4(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects: loads audio and makes all other functions valid
    public void prepareToPlayAudio() {
        try {
            //cachedID3 = makeID3();
            file = new RandomAccessFile(filename, "r");
            container = new MP4Container(file);
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
        tracks.seek(time);
    }

    // Requires: prepareToPlayAudio() or setAudioOutputFormat() called once
    // Effects:  returns the audio format of the file
    public AudioFormat getAudioOutputFormat() {
        return audioFormat;
    }

    // Requires: prepareToPlayAudio() has never been called
    //           won't crash but is pointless
    // Modifies: this
    // Effects:  sets the audio format of the file
    public void setAudioOutputFormat(AudioFormat format) {
        audioFormat = format;
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

    private static final HashMap<FieldKey, String> keyConv;

    static {
        keyConv = new HashMap<FieldKey, String>();
        keyConv.put(FieldKey.ARTIST, "Artist");
        keyConv.put(FieldKey.ALBUM, "Album");
        keyConv.put(FieldKey.ALBUM_ARTIST, "AlbumArtist");
        keyConv.put(FieldKey.TITLE, "Title");
        keyConv.put(FieldKey.TRACK, "Track");
        keyConv.put(FieldKey.TRACK_TOTAL, "Tracks");
        keyConv.put(FieldKey.DISC_NO, "Disc");
        keyConv.put(FieldKey.DISC_TOTAL, "Discs");
        keyConv.put(FieldKey.YEAR, "Year");
        keyConv.put(FieldKey.GENRE, "GenreString");
        keyConv.put(FieldKey.COMMENT, "Comment");
        keyConv.put(FieldKey.LYRICS, "Lyrics");
        keyConv.put(FieldKey.COMPOSER, "Composer");
        keyConv.put(FieldKey.RECORD_LABEL, "Publisher");
        keyConv.put(FieldKey.COPYRIGHT, "Copyright");
        keyConv.put(FieldKey.ENCODER, "Encoder");
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
        Tag tag = f.getTag();
        for (Map.Entry<FieldKey, String> entry: keyConv.entrySet()) {
            base.setID3Long(entry.getValue(), tag.getFirst(entry.getKey()));
        }
        return base;
    }

    // Effects: returns filename without directories
    public String getFileName() {
        String[] dirList = filename.split(String.valueOf(separatorChar));
        return dirList[dirList.length - 1];
    }
}
