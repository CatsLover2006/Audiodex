package audio.filetypes.decoders;

import audio.AudioDecoder;
import audio.AudioSample;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.io.IOException;

import audio.ID3Container;
import com.mpatric.mp3agic.*;
import javazoom.spi.mpeg.sampled.convert.DecodedMpegAudioInputStream;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;

import static java.io.File.separatorChar;

public class MP3 implements AudioDecoder {
    private MpegAudioFileReader fileReader;
    private File file;
    private String filename;
    private AudioFormat format;
    private DecodedMpegAudioInputStream decoded;
    private AudioInputStream in;
    private boolean ready = false;
    private byte[] data = new byte[4096]; // 4kb sample buffer, seems standard
    private int numberBytesRead = 0;
    private long duration;
    private double audioFrameRate;

    // Effects: returns true if audio can be decoded currently
    public boolean isReady() {
        return ready;
    }

    public MP3(String filename) {
        this.filename = filename;
    }

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    public void prepareToPlayAudio() {
        try {
            file = new File(filename);
            fileReader = new MpegAudioFileReader();
            duration = (Long)(fileReader.getAudioFileFormat(file).properties().get("duration"));
            in = fileReader.getAudioInputStream(file);
            AudioFormat baseFormat = in.getFormat();
            format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            decoded = new DecodedMpegAudioInputStream(format, in);
            audioFrameRate = baseFormat.getFrameRate();
            System.out.println("MP3 decoder ready!");
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

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
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
            }
        }
        return new AudioSample();
    }

    // Requires: prepareToPlayAudio() called
    //           0 <= time <= audio length
    // Modifies: this
    // Effects:  moves audio to a different point of the file
    public void goToTime(double time) {
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
    }

    // Effects: returns the current time in the audio in seconds
    public double getCurrentTime() {
        return (Long)decoded.properties().get("mp3.position.microseconds") * 0.000001;
    }

    // Effects: returns the duration of the audio in seconds
    public double getFileDuration() {
        return duration * 0.000001; // javax uses microseconds
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
        Mp3File id3Grabber;
        try {
            id3Grabber = new Mp3File(filename);
        } catch (Exception e) {
            return new ID3Container();
        }
        ID3Container id3 = new ID3Container();
        id3.setID3Data("VBR", id3Grabber.isVbr() ? "YES" : "NO");
        id3.setID3Data("bitRate", id3Grabber.getBitrate());
        id3.setID3Data("sampleRate", id3Grabber.getSampleRate());
        if (id3Grabber.hasId3v1Tag()) {
            getID3v1(id3, id3Grabber);
        }
        if (id3Grabber.hasId3v2Tag()) {
            getID3v2(id3, id3Grabber);
        }
        return id3;
    }

    // Modifies: id3
    // Effects:  fills in relevant ID3 fields
    private void getID3v1(ID3Container id3, Mp3File id3Grabber) {
        ID3v1 id3v1Tag = id3Grabber.getId3v1Tag();
        id3.setID3Long("Track", id3v1Tag.getTrack());
        id3.setID3Long("Year", id3v1Tag.getYear());
        id3.setID3Data("Artist", id3v1Tag.getArtist());
        id3.setID3Data("Title", id3v1Tag.getTitle());
        id3.setID3Data("Album", id3v1Tag.getAlbum());
        id3.setID3Data("GenreInt", id3v1Tag.getGenre());
        id3.setID3Data("GenreString", id3v1Tag.getGenreDescription());
        id3.setID3Data("Comment", id3v1Tag.getComment());
    }

    // Modifies: id3
    // Effects:  fills in relevant ID3 fields
    private void getID3v2(ID3Container id3, Mp3File id3Grabber) {
        ID3v2 id3v2Tag = id3Grabber.getId3v2Tag();
        id3.setID3Data("Artist", id3v2Tag.getArtist());
        id3.setID3Data("Title", id3v2Tag.getTitle());
        id3.setID3Data("Album", id3v2Tag.getAlbum());
        id3.setID3Long("Track", id3v2Tag.getTrack());
        id3.setID3Long("Year", id3v2Tag.getYear());
        id3.setID3Data("GenreInt", id3v2Tag.getGenre());
        id3.setID3Data("GenreString", id3v2Tag.getGenreDescription());
        id3.setID3Data("Comment", id3v2Tag.getComment());
        id3.setID3Data("Lyrics", id3v2Tag.getLyrics());
        id3.setID3Data("Composer", id3v2Tag.getComposer());
        id3.setID3Data("Publisher", id3v2Tag.getPublisher());
        id3.setID3Data("OriginalArtist", id3v2Tag.getOriginalArtist());
        id3.setID3Data("AlbumArtist", id3v2Tag.getAlbumArtist());
        id3.setID3Data("Copyright", id3v2Tag.getCopyright());
        id3.setID3Data("URL", id3v2Tag.getUrl());
        id3.setID3Data("Encoder", id3v2Tag.getEncoder());
    }

    // Effects: returns filename without directories
    public String getFileName() {
        String[] dirList = filename.split(String.valueOf(separatorChar));
        return dirList[dirList.length - 1];
    }
}
