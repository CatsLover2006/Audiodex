package audio;

import javax.sound.sampled.AudioFormat;

public class AudioDataStructure {
    private final String filename;
    private final long bitrate;
    private final int sampleSize;
    private final AudioFileTypes audioFileType;

    // Requires: filename points to a file (obviously)
    // Modifies: this
    // Effects:  creates a data structure for the audio from a file
    public AudioDataStructure(String filename) {
        this.filename = filename;
        AudioDecoder audioDecoder = AudioFileLoader.loadFile(filename);
        audioDecoder.prepareToPlayAudio();
        audioFileType = AudioFileTypes.AAC_MP4;
        AudioFormat format = audioDecoder.getAudioOutputFormat();
        bitrate = (long) (format.getSampleSizeInBits() / format.getSampleRate());
        sampleSize = format.getSampleSizeInBits();
    }

    // Modifies: this
    // Effects:  creates a data structure for the audio from known data (loading from database)
    public AudioDataStructure(String filename, long bitrate,
                              int sampleSize, AudioFileTypes fileType) {
        this.filename = filename;
        this.bitrate = bitrate;
        this.sampleSize = sampleSize;
        audioFileType = fileType;
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
    public AudioFileTypes getAudioFileType() {
        return audioFileType;
    }
}
