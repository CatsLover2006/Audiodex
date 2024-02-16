package audio.filetypes.encoders;

import audio.AudioDecoder;
import audio.AudioEncoder;
import audio.AudioSample;
import org.tritonus.sampled.file.WaveAudioOutputStream;
import org.tritonus.share.sampled.file.AudioOutputStream;
import org.tritonus.share.sampled.file.TDataOutputStream;
import org.tritonus.share.sampled.file.TNonSeekableDataOutputStream;
import ui.Main;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;

public class WAV implements AudioEncoder {
    AudioDecoder decoder;

    // Effects: Tells the audio encoder where we're encoding from
    public void setSource(AudioDecoder from) {
        decoder = from;
    }

    // Effects: Gets encoder specific selectors
    //          e.g: compression ratio
    public HashMap<String, List<String>> getEncoderSpecificSelectors() {
        return null;
    }

    // Modifies: this
    // Effects:  sets the target audio format for encoder
    public void setAudioFormat(AudioFormat format, HashMap<String, String> encoderSpecificValues) {
        Main.CliInterface.println("WAV does not have any configurable settings.");
    }

    // Modifies: filesystem
    // Effects:  encodes audio to specific file
    public boolean encodeAudio(String to) {
        try {
            AudioFormat format = decoder.getAudioOutputFormat();
            long size = (long) (((format.getSampleSizeInBits() * format.getChannels()
                    * format.getSampleRate()) * decoder.getFileDuration()) / 8);
            TDataOutputStream file = new TNonSeekableDataOutputStream(new FileOutputStream(to));
            WaveAudioOutputStream out = new WaveAudioOutputStream(decoder.getAudioOutputFormat(), size, file);
            while (decoder.moreSamples()) {
                AudioSample sample = decoder.getNextSample();
                out.write(sample.getData(), 0, sample.getLength());
            }
            out.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Effects: gets an approximate percent for how far along the encoding is
    //          output ranges from 0.0 to 1.0
    public double encodedPercent() {
        if (decoder == null || !decoder.isReady()) {
            return 0;
        }
        return decoder.getCurrentTime() / decoder.getFileDuration();
    }
}
