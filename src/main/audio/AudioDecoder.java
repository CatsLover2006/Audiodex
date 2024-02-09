package audio;

import javax.sound.sampled.AudioFormat;

// Audio decoder interface
// Most functions explain themselves
public interface AudioDecoder {

    // Effects: returns true if audio can be decoded currently
    public boolean isReady();

    // Modifies: this
    // Effects:  loads audio and makes all other functions valid
    public void prepareToPlayAudio();

    // Requires: prepareToPlayAudio() called
    // Modifies: this
    // Effects:  unloads audio file, to save memory
    //           getAudioOutputFormat() and atEndOfFile() remain valid
    public void closeAudioFile();

    // Requires: prepareToPlayAudio() called
    // Effects:  decodes and returns the next audio sample
    public AudioSample getNextSample();

    // Requires: prepareToPlayAudio() called
    //           0 <= time <= audio length
    // Modifies: this
    // Effects:  moves audio to a different point of the file
    public void goToTime(double time);

    // Effects: returns the current time in the audio in seconds
    public double getCurrentTime();

    // Effects: returns the duration of the audio in seconds
    public double getFileDuration();

    // Requires: prepareToPlayAudio() or setAudioOutputFormat() called once
    // Effects:  returns the audio format of the file
    public AudioFormat getAudioOutputFormat();

    // Requires: prepareToPlayAudio() has never been called
    //           won't crash but is pointless
    // Modifies: this
    // Effects:  sets the audio format of the file
    public void setAudioOutputFormat(AudioFormat format);

    // Effects:  returns true if there are more samples to be played
    //           will return false is no file is loaded
    public boolean moreSamples();

}