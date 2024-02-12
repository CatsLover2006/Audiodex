package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioFileType;
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
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static audio.filetypes.TagConversion.keyConv;
import static java.io.File.separatorChar;

// Audio decoder for the MP4 file type
// Only supports AAC MP4 files but to my knowledge there are no other
// audio formats that use MP4 as a primary
public class MP4 implements AudioDecoder {
    Frame frame;
    private AudioTrack tracks;
    private AudioFormat audioFormat;
    private boolean ready = false;
    private String filename;
    private Decoder decoder;
    private final SampleBuffer buffer = new SampleBuffer();
    private RandomAccessFile file;
    private double duration;
    private ID3Container cachedID3;

    // AudioInputStream container for audio decoder
    // done for easy handling of certain encoders
    private class VirtualMP4AudioInputStream extends AudioInputStream {
        private AudioSample sampleStor;
        private int sampleInStor = 0;
        private double timeToSkipTo = 0;
        private int timeToResetMark = 0;
        private int sampleStorReset = 0;

        public VirtualMP4AudioInputStream() {
            super(null, audioFormat, 0);
        }

        @Override
        public int available() throws IOException {
            return (int) ((audioFormat.getSampleSizeInBits() * audioFormat.getSampleRate()
                            * (duration - getCurrentTime())) / 8);
        }

        @Override
        public void close() {
            return; // Does nothing, its pulling data from this object
        }

        @Override
        public AudioFormat getFormat() {
            return audioFormat;
        }

        @Override
        public long getFrameLength() {
            return (long) (audioFormat.getFrameSize() * duration);
        }

        @Override
        public void mark(int readlimit) {
            timeToResetMark = readlimit;
            timeToSkipTo = getCurrentTime();
            sampleStorReset = sampleInStor;
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public int read() {
            if (moreSamples()) {
                if (timeToResetMark == 0) {
                    timeToSkipTo = 0;
                } else {
                    timeToResetMark--;
                }
                if (sampleInStor <= sampleStor.getLength()) {
                    sampleInStor = 0;
                    sampleStor = getNextSample();
                }
                int t = sampleStor.getData()[sampleInStor];
                sampleInStor++;
                return t;
            }
            return -1;
        }

        @Override
        public int read(byte[] b) {
            if (!moreSamples()) {
                return -1;
            }
            for (int i = 0; i < Math.min(b.length, audioFormat.getFrameSize()); i++) {
                b[i] = (byte)read();
                if (b[i] == -1) {
                    return i + 1;
                }
            }
            return b.length;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            tracks.seek(0);
            while (off != 0) {
                off -= (int) skip(off);
            }
            if (!moreSamples()) {
                return -1;
            }
            for (int i = 0; i < Math.min(b.length, Math.min(len, audioFormat.getFrameSize())); i++) {
                b[i] = (byte)read();
                if (b[i] == -1) {
                    return i + 1;
                }
            }
            return b.length;
        }

        @Override
        public void reset() {
            tracks.seek(timeToSkipTo);
            sampleInStor = sampleStorReset;
            sampleStorReset = 0;
            timeToSkipTo = 0;
        }

        @Override
        public long skip(long n) {
            for (long i = 0; i < n; i++) {
                if (read() == -1) {
                    return i + 1;
                }
            }
            return n;
        }
    }

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
            try {
                Date d = Date.from(Instant.parse(tag.getFirst(entry.getKey())));
                base.setID3Long(entry.getValue(), String.valueOf(1900 + d.getYear()));
            } catch (Exception e) {
                base.setID3Long(entry.getValue(), tag.getFirst(entry.getKey()));
            }
        }
        return base;
    }

    // Effects: returns filename without directories
    public String getFileName() {
        String[] dirList = filename.split(String.valueOf(separatorChar));
        return dirList[dirList.length - 1];
    }

    public AudioFileType getFileType() {
        return AudioFileType.AAC_MP4;
    }

    // Effects: returns an audio input stream for encoding data
    public AudioInputStream getAudioInputStream() {
        return new VirtualMP4AudioInputStream();
    }
}
