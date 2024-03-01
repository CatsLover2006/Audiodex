package audio.filetypes.encoders;

import audio.AudioDecoder;
import audio.AudioEncoder;
import audio.AudioSample;
import audio.ID3Container;
import org.tritonus.sampled.file.AiffAudioOutputStream;
import org.tritonus.share.sampled.file.TDataOutputStream;
import org.tritonus.share.sampled.file.TSeekableDataOutputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.util.HashMap;
import java.util.List;

// AIFF file encoder class
public class Aiff implements AudioEncoder {
    AudioDecoder decoder;
    private boolean done = false;

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
        System.out.println("AIFF does not have any configurable settings.");
    }

    // Modifies: filesystem
    // Effects:  encodes audio to specific file
    public boolean encodeAudio(String to) {
        try {
            decoder.prepareToPlayAudio();
            AudioFormat format = decoder.getAudioOutputFormat();
            long size = (long) (((format.getSampleSizeInBits() * format.getChannels()
                    * format.getSampleRate()) * decoder.getFileDuration()) / 8);
            TDataOutputStream file = new TSeekableDataOutputStream(new File(to));
            AiffAudioOutputStream out = new AiffAudioOutputStream(decoder.getAudioOutputFormat(),
                    AudioFileFormat.Type.AIFF, size, file);
            while (decoder.moreSamples()) {
                AudioSample sample = decoder.getNextSample();
                out.write(sample.getData(), 0, sample.getLength());
            }
            out.close();
            return updateID3(to);
        } catch (Exception e) {
            return false;
        }
    }

    // Modifies: filesystem
    // Effects:  updates ID3 data in MP3 file (returns true on success)
    private boolean updateID3(String filename) {
        decoder.prepareToPlayAudio();
        ID3Container container = decoder.getID3();
        audio.filetypes.decoders.Aiff id3Updater = new audio.filetypes.decoders.Aiff(filename);
        id3Updater.prepareToPlayAudio();
        container.setID3Data("Encoder", "Audiodex");
        id3Updater.setID3(container);
        id3Updater.setArtwork(decoder.getArtwork());
        id3Updater.closeAudioFile();
        decoder.closeAudioFile();
        done = true;
        return true;
    }

    // Effects: gets an approximate percent for how far along the encoding is
    //          output ranges from 0.0 to 1.0
    public double encodedPercent() {
        if (done) {
            return 1;
        }
        if (decoder == null || !decoder.isReady()) {
            return 0;
        }
        return decoder.getCurrentTime() / decoder.getFileDuration();
    }
}
