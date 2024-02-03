package audio.filetypes.decoders;

import audio.AudioDecoder;
import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import net.sourceforge.jaad.mp4.api.Frame;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.api.Track;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

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

    public boolean isReady() {
        return ready;
    }

    // Effects: it's a constructor
    public MP4(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects: loads audio and makes all other functions valid
    public boolean prepareToPlayAudio() {
        try {
            file = new RandomAccessFile(filename, "r");
            container = new MP4Container(file);
            Movie movie = container.getMovie();
            duration = movie.getDuration();
            List<Track> tracks = movie.getTracks(AudioTrack.AudioCodec.AAC);
            if (tracks.isEmpty()) {
                file.close();
                file = null;
                return false;
            }
            this.tracks = (AudioTrack) tracks.get(0);
            audioFormat = new AudioFormat(this.tracks.getSampleRate(), this.tracks.getSampleSize(),
                    this.tracks.getChannelCount(), true, true);
            decoder = new Decoder(this.tracks.getDecoderSpecificInfo());
            ready = true;
            System.out.println("AAC decoder ready!");
            return true;
        } catch (IOException e) {
            ready = false;
            return false;
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
        }
    }

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    public byte[] getNextSample() {
        while (moreSamples()) {
            try {
                frame = tracks.readNextFrame();
                decoder.decodeFrame(frame.getData(), buffer);
                return buffer.getData();
            } catch (AACException e) {
                e.printStackTrace();
            } catch (IOException e) {
                return new byte[] {0};
            }
            // If we encountered an error, just move along to the next sample
        }
        return new byte[] {0};
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
            return 0;
        }
        return frame.getTime();
    }

    public double getFileDuration() {
        return duration;
    }

    // Effects:  returns false if there are more samples to be played
    //           will return true is no file is loaded
    public boolean moreSamples() {
        if (!ready) {
            return false;
        }
        return tracks.hasMoreFrames();
    }
}
