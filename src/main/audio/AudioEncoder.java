package audio;

import javax.sound.sampled.AudioFormat;
import java.util.HashMap;
import java.util.List;

// audio encoder interface
public interface AudioEncoder {
    // Effects: Tells the audio encoder where we're encoding from
    void setSource(AudioDecoder from);

    // Effects: Gets encoder specific selectors
    //          e.g: compression ratio
    HashMap<String, List<String>> getEncoderSpecificSelectors();

    // Modifies: this
    // Effects:  sets the target audio format for encoder
    void setAudioFormat(AudioFormat format, HashMap<String, String> encoderSpecificValues);

    // Modifies: filesystem
    // Effects:  encodes audio to specific file
    boolean encodeAudio(String to);

    // Effects: gets an approximate percent for how far along the encoding is
    double encodedPercent();
}
