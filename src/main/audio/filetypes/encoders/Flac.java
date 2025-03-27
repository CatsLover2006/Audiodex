package audio.filetypes.encoders;

import audio.AudioDecoder;
import audio.AudioEncoder;
import audio.AudioSample;
import audio.ID3Container;
import javaFlacEncoder.*;
import org.tritonus.sampled.convert.PCM2PCMConversionProvider;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

// FLAC file encoder class
public class Flac implements AudioEncoder {
    AudioDecoder decoder;
    private boolean done = false;
    private boolean encoded = false;
    private int unencodedSamples = 0;
    private int sampleRate = 1;
    
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
        System.out.println("FLAC does not have any configurable settings.");
    }
    
    // Modifies: filesystem
    // Effects:  encodes audio to specific file
    @Override
    public boolean encodeAudio(String to) {
        try {
            File outFile = new File(to);
            AudioFormat format = decoder.getAudioOutputFormat();
            int sampleSize = format.getSampleSizeInBits();
            if (sampleSize % 8 != 0) return false; // Unsupported
            int bytesPerSample = sampleSize / 8;
            sampleRate = (int)format.getSampleRate();
            int channels = format.getChannels();
            boolean bigEndian = format.isBigEndian();
            boolean isSigned = format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED;
            StreamConfiguration streamConfiguration = new StreamConfiguration();
            streamConfiguration.setSampleRate(sampleRate);
            streamConfiguration.setBitsPerSample(sampleSize);
            streamConfiguration.setChannelCount(channels);
            EncodingConfiguration encodingConfiguration = new EncodingConfiguration();
            FLACEncoder flac = new FLACEncoder();
            if (!flac.setStreamConfiguration(streamConfiguration) ||
                    !flac.setEncodingConfiguration(encodingConfiguration)) {
                System.err.println("failed to set codec config");
                return false; // Error
            }
            FLACFileOutputStream outputStream = new FLACFileOutputStream(outFile);
            flac.setOutputStream(outputStream);
            AudioSample sample;
            int[] integerSampleRepresentation;
            int framesRead, recodedSample; // Memory optimization
            byte[] sampleData;
            flac.openFLACStream();
            while (decoder.moreSamples()) {
                sample = decoder.getNextSample();
                framesRead = sample.getLength() / bytesPerSample;
                sampleData = sample.getData();
                integerSampleRepresentation = new int[framesRead];
                for (int i = 0, j = 0; i < sample.getLength(); i += bytesPerSample, j++) {
                    recodedSample = 0;
                    if (bigEndian) {
                        for (int a = 0; a < bytesPerSample; a++) recodedSample |= (sampleData[i + a] & 0xff) << (bytesPerSample - a - 1) * 8;
                    } else {
                        for (int a = 0; a < bytesPerSample; a++) recodedSample |= (sampleData[i + a] & 0xff) << a * 8;
                    }
                    if (!isSigned) {
                        recodedSample -= 1 << bytesPerSample * 8 - 1;
                    }
                    integerSampleRepresentation[j] = recodedSample;
                }
                flac.addSamples(integerSampleRepresentation, framesRead / channels);
                unencodedSamples += framesRead / channels;
                unencodedSamples -= flac.encodeSamples(unencodedSamples, false);
            }
            flac.encodeSamples(unencodedSamples, true);
            return updateID3(to);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // Modifies: filesystem
    // Effects:  updates ID3 data in MP3 file (returns true on success)
    private boolean updateID3(String filename) {
        decoder.prepareToPlayAudio();
        ID3Container container = decoder.getID3();
        audio.filetypes.decoders.Flac id3Updater = new audio.filetypes.decoders.Flac(filename);
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
        return (decoder.getCurrentTime() - unencodedSamples / (double)sampleRate) / decoder.getFileDuration();
    }
}
