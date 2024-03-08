package audio.filetypes.encoders;

import audio.AudioDecoder;
import audio.AudioEncoder;
import audio.AudioSample;
import org.tritonus.sampled.file.WaveAudioOutputStream;
import org.tritonus.share.sampled.file.TDataOutputStream;
import org.tritonus.share.sampled.file.TNonSeekableDataOutputStream;

import javax.sound.sampled.AudioFormat;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;

// WAV file encoder class
public class WAV implements AudioEncoder {
    AudioDecoder decoder;
    private boolean done = false;

    // Effects: Tells the audio encoder where we're encoding from
    @Override
    public void setSource(AudioDecoder from) {
        decoder = from;
    }

    // Effects: Gets encoder specific selectors
    //          e.g: compression ratio
    @Override
    public HashMap<String, List<String>> getEncoderSpecificSelectors() {
        return null;
    }

    // Modifies: this
    // Effects:  sets the target audio format for encoder
    @Override
    public void setAudioFormat(AudioFormat format, HashMap<String, String> encoderSpecificValues) {
        System.out.println("WAV does not have any configurable settings.");
    }

    // Modifies: filesystem
    // Effects:  encodes audio to specific file
    @Override
    public boolean encodeAudio(String to) {
        try {
            AudioFormat format = decoder.getAudioOutputFormat();
            long size = (long) ((format.getSampleSizeInBits() * format.getChannels()
                    * format.getSampleRate()) * decoder.getFileDuration() / 8);
            TDataOutputStream file = new TNonSeekableDataOutputStream(new FileOutputStream(to));
            WaveAudioOutputStream out = new WaveAudioOutputStream(decoder.getAudioOutputFormat(), size, file);
            while (decoder.moreSamples()) {
                AudioSample sample = decoder.getNextSample();
                out.write(sample.getData(), 0, sample.getLength());
            }
            out.close();
            done = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Effects: gets an approximate percent for how far along the encoding is
    //          output ranges from 0.0 to 1.0
    @Override
    public double encodedPercent() {
        if (done) {
            return 1;
        }
        if (decoder == null || !decoder.isReady()) {
            return 0;
        }
        if (!decoder.moreSamples()) {
            return 1;
        }
        return decoder.getCurrentTime() / decoder.getFileDuration();
    }
}
