package model;

import javax.sound.sampled.AudioFormat;

import java.io.File;
import java.io.IOException;

import static java.io.File.separatorChar;

import org.json.simple.*;
import ui.audio.AudioFileType;
import ui.audio.filetypes.AudioDecoder;

// Audio data structure class
// Will be used for the database
public class AudioDataStructure {
    private String filename;
    private long bitrate;
    private long sampleSize;
    private AudioFileType audioFileType;
    private ID3Container id3Data;
    private long fileSize;

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
            fileSize = -1;
            audioFileType = AudioFileType.EMPTY;
            id3Data = null;
            return;
        }
        fileSize = (new File(filename)).length(); // Will exist
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
        return audioFileType == AudioFileType.EMPTY || audioFileType == AudioFileType.UNKNOWN;
    }

    // Modifies: this
    // Effects:  creates a data structure for the audio from known data (loading from database)
    //           ID3 string variant
    public AudioDataStructure(String filename, long fileSize, long bitrate,
                              long sampleSize, AudioFileType fileType,
                              JSONObject id3Data) {
        this.filename = filename;
        this.fileSize = fileSize;
        this.bitrate = bitrate;
        this.sampleSize = sampleSize;
        audioFileType = fileType;
        this.id3Data = new ID3Container(id3Data);
    }

    // Effects: gets ID3Container
    public ID3Container getId3Data() {
        return id3Data;
    }

    // Effects: gets sample size in bits
    public long getSampleSize() {
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

    // Effects: gets filesize
    public long getFileSize() {
        return fileSize;
    }

    // Effects: encodes data
    public JSONObject encode() {
        JSONObject out = new JSONObject();
        out.put("filename", filename);
        out.put("filetype", audioFileType.toString());
        out.put("ID3data", id3Data.encode());
        out.put("bitrate", bitrate);
        out.put("samplesize", sampleSize);
        out.put("filesize", fileSize);
        return out;
    }

    // Requires: inputting valid input (from toString)
    // Effects:  returns a *VALID* Audio Data Structure object
    //           with all audio data and ID3 data decoded from string
    public static AudioDataStructure decode(JSONObject data) {
        String filename = (String) data.get("filename");
        AudioFileType filetype = AudioFileType.valueOf((String) data.get("filetype"));
        long bitrate = (Long) data.get("bitrate");
        long filesize = (Long) data.get("filesize");
        long samplesize = (Long) data.get("samplesize");
        JSONObject id3 = (JSONObject) data.get("ID3data");
        return new AudioDataStructure(filename, filesize, bitrate, samplesize, filetype, id3);
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
