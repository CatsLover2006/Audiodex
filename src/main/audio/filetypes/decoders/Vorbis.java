package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioFileType;
import audio.AudioSample;
import audio.ID3Container;
import audio.filetypes.TagConversion;
import com.github.trilarion.sound.vorbis.sampled.DecodedVorbisAudioInputStream;
import com.github.trilarion.sound.vorbis.sampled.spi.VorbisAudioFileReader;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import ui.Main;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static audio.filetypes.TagConversion.keyConv;
import static java.io.File.separatorChar;
import static java.lang.Thread.sleep;

// Vorbis-type audio file decoder class
public class Vorbis implements AudioDecoder {
    private String filename;
    private AudioFormat format;
    private DecodedVorbisAudioInputStream decoded;
    private AudioInputStream in;
    private boolean ready = false;
    private final byte[] data = new byte[4096]; // 4kb sample buffer, seems standard
    private int numberBytesRead = 0;
    private long bytesPlayed = 0;
    private boolean allowSampleReads = true;
    private double bytesPerSecond;
    private double duration;

    // Effects: returns true if audio can be decoded currently
    public boolean isReady() {
        return ready;
    }

    public Vorbis(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    public void prepareToPlayAudio() {
        try {
            File file = new File(filename);
            VorbisAudioFileReader fileReader = new VorbisAudioFileReader();
            in = fileReader.getAudioInputStream(file);
            AudioFormat baseFormat = in.getFormat();
            format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            decoded = new DecodedVorbisAudioInputStream(format, in);
            AudioFile f = AudioFileIO.read(file);
            duration = f.getAudioHeader().getPreciseTrackLength();
            bytesPerSecond = format.getSampleSizeInBits() * format.getChannels() * format.getSampleRate() / 8;
            System.out.println("Vorbis decoder ready!");
            ready = true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Requires: prepareToPlayAudio() called
    // Modifies: this
    // Effects:  unloads audio file, to save memory
    //           getAudioOutputFormat() and atEndOfFile() remain valid
    public void closeAudioFile() {
        ready = false;
        try {
            decoded.close();
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Effects: returns true if goToTime() is running
    //          only exists due to having multiple threads
    public boolean skipInProgress() {
        return !allowSampleReads;
    }

    // Effects: wait for a set time
    private static void wait(int nanos) {
        try {
            sleep(0, nanos);
        } catch (InterruptedException e) {
            // Why?
        }
    }

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    public AudioSample getNextSample() {
        while (!allowSampleReads) {
            wait(1);
        }
        while (moreSamples()) {
            try {
                numberBytesRead = decoded.read(data, 0, data.length);
                if (numberBytesRead == -1) {
                    break;
                } // Yes I have to do this to track time
                bytesPlayed += numberBytesRead;
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
    public void goToTime(double time) {
        try {
            long toSkip = (long) (time * bytesPerSecond) - bytesPlayed;
            if (toSkip < 0) {
                allowSampleReads = false;
                toSkip += bytesPlayed;
                bytesPlayed = 0;
                prepareToPlayAudio();
            }
            allowSampleReads = false;
            long skipped;
            while (toSkip != 0) {
                skipped = decoded.skip(toSkip);
                bytesPlayed += skipped;
                toSkip -= skipped;
                if (skipped == 0) {
                    allowSampleReads = true;
                    return;
                }
            }
            allowSampleReads = true;
        } catch (IOException e) {
            // RIP
        }
        allowSampleReads = true;
    }

    // Effects: returns the current time in the audio in seconds
    public double getCurrentTime() {
        return bytesPlayed / bytesPerSecond;
    }

    // Effects: returns the duration of the audio in seconds
    public double getFileDuration() {
        return duration;
    }

    // Requires: prepareToPlayAudio() or setAudioOutputFormat() called once
    // Effects:  returns the audio format of the file
    public AudioFormat getAudioOutputFormat() {
        return format;
    }

    // Requires: prepareToPlayAudio() has never been called
    //           won't crash but is pointless
    // Modifies: this
    // Effects:  sets the audio format of the file
    public void setAudioOutputFormat(AudioFormat format) {
        this.format = format;
    }

    // Effects:  returns true if there are more samples to be played
    //           will return false is no file is loaded
    public boolean moreSamples() {
        return numberBytesRead != -1;
    }

    // Effects: returns decoded ID3 data
    public ID3Container getID3() {
        ID3Container base = new ID3Container();
        base.setID3Data("VBR", "UNKNOWN");
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
    public void setID3(ID3Container container) {
        AudioFile f;
        try {
            f = AudioFileIO.read(new File(filename));
        } catch (Exception e) {
            return;
        }
        Tag tag = f.getTag();
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
        return AudioFileType.VORBIS;
    }

    // Effects: returns an audio input stream for encoding data
    public AudioInputStream getAudioInputStream() {
        return decoded;
    }
}