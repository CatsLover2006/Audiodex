package audio;

import org.jaudiotagger.tag.images.Artwork;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

// Audio decoder interface
// Most functions explain themselves
public interface AudioDecoder {

    AudioFileType getFileType();

    // Effects: returns filename without directories
    String getFileName();

    // Effects: returns true if audio can be decoded currently
    boolean isReady();

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    void prepareToPlayAudio();

    // Requires: prepareToPlayAudio() called
    // Modifies: this
    // Effects:  unloads audio file, to save memory
    //           getAudioOutputFormat() and atEndOfFile() remain valid
    void closeAudioFile();

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    AudioSample getNextSample();

    // Requires: prepareToPlayAudio() called
    //           0 <= time <= audio length
    // Modifies: this
    // Effects:  moves audio to a different point of the file
    void goToTime(double time);

    // Effects: returns the current time in the audio in seconds
    double getCurrentTime();

    // Effects: returns the duration of the audio in seconds
    double getFileDuration();

    // Requires: prepareToPlayAudio() or setAudioOutputFormat() called once
    // Effects:  returns the audio format of the file
    AudioFormat getAudioOutputFormat();

    // Effects: returns true if there are more samples to be played
    //          will return false is no file is loaded
    boolean moreSamples();

    // Effects: returns decoded ID3 data
    ID3Container getID3();

    // Modifies: file on filesystem
    // Effects:  returns decoded ID3 data
    void setID3(ID3Container container);

    // Effects: returns album artwork if possible
    Artwork getArtwork();

    // Effects: sets album artwork if possible
    void setArtwork(Artwork image);

    // Effects: returns true if goToTime() is running
    //          only exists due to having multiple threads
    boolean skipInProgress();

    // Effects: returns replaygain value
    //          defaults to -6
    float getReplayGain();
}