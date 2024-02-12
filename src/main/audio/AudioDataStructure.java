package audio;

import javax.sound.sampled.AudioFormat;

import java.io.File;
import java.io.IOException;

import static java.io.File.separatorChar;

// Audio data structure class
// Will be used for the database
public class AudioDataStructure {
    public static final String[] RESERVED_CHARACTERS = {
            "🜁", // Key separator
            "🜃" // Value separator
    };

    private String filename;
    private long bitrate;
    private int sampleSize;
    private AudioFileType audioFileType;
    private ID3Container id3Data;

    // Requires: filename points to a file (obviously)
    // Modifies: this
    // Effects:  creates a data structure for the audio from a file
    public AudioDataStructure(String filename) {
        try {
            this.filename = (new File(filename)).getCanonicalPath();
        } catch (IOException e) {
            this.filename = filename;
        }
        AudioDecoder audioDecoder = AudioFileLoader.loadFile(filename);
        if (audioDecoder == null) {
            bitrate = -1;
            sampleSize = -1;
            audioFileType = AudioFileType.EMPTY;
            id3Data = null;
            return;
        }
        audioDecoder.prepareToPlayAudio();
        audioFileType = audioDecoder.getFileType();
        AudioFormat format = audioDecoder.getAudioOutputFormat();
        bitrate = (long) (format.getSampleSizeInBits() / format.getSampleRate());
        sampleSize = format.getSampleSizeInBits();
        id3Data = audioDecoder.getID3();
        audioDecoder.closeAudioFile();
    }

    // Effects: returns true if audio data file type is empty
    public boolean isEmpty() {
        return audioFileType == AudioFileType.EMPTY;
    }

    // Modifies: this
    // Effects:  creates a data structure for the audio from known data (loading from database)
    //           ID3 string variant
    public AudioDataStructure(String filename, long bitrate,
                              int sampleSize, AudioFileType fileType,
                              String id3Data) {
        this.filename = filename;
        this.bitrate = bitrate;
        this.sampleSize = sampleSize;
        audioFileType = fileType;
        this.id3Data = ID3Container.fromString(id3Data);
    }

    // Effects: gets ID3Container
    public ID3Container getId3Data() {
        return id3Data;
    }

    // Effects: gets sample size in bits
    public int getSampleSize() {
        return sampleSize;
    }

    // Effects: gets bitrate
    public long getBitrate() {
        return bitrate;
    }

    // Effects: gets filename
    public String getFilename() {
        return filename;
    }

    // Effects: gets filetype
    public AudioFileType getAudioFileType() {
        return audioFileType;
    }

    @Override
    // Effects: encodes data into string
    //          yes I'm making one of these myself
    public String toString() {
        String out = "";
        out += "fn" + RESERVED_CHARACTERS[1] + filename + RESERVED_CHARACTERS[0];
        out += "t" + RESERVED_CHARACTERS[1] + audioFileType.toString() + RESERVED_CHARACTERS[0];
        out += "id3" + RESERVED_CHARACTERS[1] + id3Data + RESERVED_CHARACTERS[0];
        out += "br" + RESERVED_CHARACTERS[1] + bitrate + RESERVED_CHARACTERS[0];
        out += "ss" + RESERVED_CHARACTERS[1] + sampleSize + RESERVED_CHARACTERS[0];
        return out.substring(0, out.length() - RESERVED_CHARACTERS[0].length()); // Delete the last key separator
    }

    // Using method length warning suppression due to switch/case for
    // datatype decompression
    @SuppressWarnings("methodlength")
    // Requires: inputting valid input (from toString)
    // Effects:  returns a *VALID* Audio Data Structure object
    //           with all audio data and ID3 data decoded from string
    public static AudioDataStructure fromString(String data) {
        String[] keys = data.split(RESERVED_CHARACTERS[0]); // Key separator
        String filename = "";
        String id3 = "";
        long bitrate = 0;
        int sampleSize = 0;
        AudioFileType fileType = null;
        for (String key : keys) {
            String[] dat = key.split(RESERVED_CHARACTERS[1]); // Value separator
            try {
                switch (dat[0]) {
                    case "fn": {
                        filename = dat[1];
                        break;
                    }
                    case "id3": {
                        id3 = dat[1];
                        break;
                    }
                    case "br": {
                        bitrate = Long.parseLong(dat[1]);
                        break;
                    }
                    case "ss": {
                        sampleSize = Integer.parseInt(dat[1]);
                        break;
                    }
                    case "t": {
                        fileType = AudioFileType.valueOf(dat[1]);
                        break;
                    }
                }
            } catch (Exception e) {
                // Lol bye
            }
        }
        return new AudioDataStructure(filename, bitrate, sampleSize, fileType, id3);
    }

    // Effects: returns playback string for display
    public String getPlaybackString() {
        String workingData = (String) id3Data.getID3Data("Title");
        if (workingData == null || workingData.equals("null") || workingData.isEmpty()) {
            String[] dirList = filename.split(String.valueOf(separatorChar));
            return dirList[dirList.length - 1];
        }
        String base = workingData;
        workingData = (String) id3Data.getID3Data("Artist");
        if (!(workingData == null || workingData.equals("null") || workingData.isEmpty())) {
            base += " by " + workingData;
        }
        return base;
    }

    // Modifies: this
    // Effects:  replaces ID3Container
    public void updateID3(ID3Container nu) {
        id3Data = nu;
    }
}
