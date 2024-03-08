package audio.filetypes.encoders;

import audio.*;
import audio.filetypes.decoders.MpegType;
import net.sourceforge.lame.lowlevel.LameEncoder;
import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

// MP3 file encoder class
public class MP3 implements AudioEncoder {
    private AudioDecoder decoder;
    private boolean done = false;
    private int bitrate = 320;
    private boolean useVBR = false;
    private boolean stereo = false;

    // Effects: Tells the audio encoder where we're encoding from
    @Override
    public void setSource(AudioDecoder from) {
        decoder = from;
    }

    // Effects: Gets encoder specific selectors
    //          e.g: compression ratio
    @Override
    public HashMap<String, List<String>> getEncoderSpecificSelectors() {
        HashMap<String, List<String>> options = new HashMap<>();
        List<String> valid = new ArrayList<>();
        valid.add("Yes");
        valid.add("No");
        options.put("variable bit rate", valid);
        decoder.prepareToPlayAudio();
        if (decoder.getAudioOutputFormat().getChannels() != 1) {
            options.put("stereo", valid);
        }
        decoder.closeAudioFile();
        valid = new ArrayList<>();
        int[] bitrates = {
                8, 12, 16, 20, 24, 28, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160
        };
        for (int bitrate : bitrates) {
            valid.add(bitrate + " kbps");
        }
        options.put("mono bitrate", valid);
        return options;
    }

    // Modifies: this
    // Effects:  sets the target audio format for encoder
    @Override
    public void setAudioFormat(AudioFormat format, HashMap<String, String> encoderSpecificValues) {
        if (Objects.equals(encoderSpecificValues.get("stereo"), "Yes")) {
            stereo = true;
        }
        if (Objects.equals(encoderSpecificValues.get("variable bit rate"), "Yes")) {
            useVBR = true;
        }
        String[] bitrate = encoderSpecificValues.get("mono bitrate").split(" ");
        this.bitrate = Integer.parseInt(bitrate[0]);
    }

    // Modifies: filesystem
    // Effects:  encodes audio to specific file
    @Override
    public boolean encodeAudio(String to) {
        try {
            decoder.prepareToPlayAudio();
            AudioFormat format = decoder.getAudioOutputFormat();
            LameEncoder encoder = new LameEncoder(format, bitrate * (stereo ? 2 : 1),
                    stereo ? MPEGMode.STEREO : MPEGMode.MONO, Lame.QUALITY_HIGHEST, useVBR);
            AudioSample sample;
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            int written;
            while (decoder.moreSamples()) {
                sample = decoder.getNextSample();
                byte[] byteBuffer = new byte[sample.getLength()];
                written = encoder.encodeBuffer(sample.getData(), 0, sample.getLength(), byteBuffer);
                stream.write(byteBuffer, 0, written);
            }
            encoder.close();
            OutputStream file = new FileOutputStream(to);
            file.write(stream.toByteArray());
            file.close();
            decoder.closeAudioFile();
            return updateID3(to);
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

    // Modifies: filesystem
    // Effects:  updates ID3 data in MP3 file (returns true on success)
    private boolean updateID3(String filename) {
        decoder.prepareToPlayAudio();
        ID3Container container = decoder.getID3();
        MpegType id3Updater = new MpegType(filename);
        id3Updater.prepareToPlayAudio();
        container.setID3Data("Encoder", "Audiodex");
        id3Updater.setID3(container);
        id3Updater.setArtwork(decoder.getArtwork());
        id3Updater.closeAudioFile();
        decoder.closeAudioFile();
        done = true;
        return true;
    }
}
