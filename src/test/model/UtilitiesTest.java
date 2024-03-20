package model;

import audio.AudioSample;
import audio.filetypes.decoders.Aiff;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(0)
public class UtilitiesTest {

    @Test
    public void UtilitiesTest() {
        new ExceptionIgnore();
        new FileManager();
    }

    // Audio sample decrease test
    @Test
    public void AudioSampleQualityDropTest() {
        Aiff aiffDecoder = new Aiff("data/scarlet.aif");
        aiffDecoder.prepareToPlayAudio();
        AudioSample sample;
        List<Byte> decodedSample = new LinkedList<>();
        while (aiffDecoder.moreSamples()) {
            decodedSample.clear();
            sample = aiffDecoder.getNextSample();
            int sampleLength = sample.getLength();
            for (int i = 0; i < sample.getLength(); i += 2) {
                decodedSample.add(sample.getData()[i]);
            }
            sample.reduceBitdepth(2, aiffDecoder.getAudioOutputFormat().isBigEndian());
            assertEquals(sampleLength / 2, sample.getLength());
            for (int i = 0; i < sample.getLength(); i++) {
                assertEquals(decodedSample.get(i), sample.getData()[i]);
            }
        }
    }

    @Test
    public void RootCheckTest() {
        assertFalse(FileManager.isRoot(new File("./data/out/")));
        assertFalse(FileManager.isRoot(new File("./data/")));
        FileManager.updateRootStores();
        if (SystemUtils.IS_OS_WINDOWS) {
            assertTrue(FileManager.isRoot(new File("C:\\\\")));
        } else { // These have to be hardcoded for obvious reasons
            assertTrue(FileManager.isRoot(new File("/")));
        }
    }
}
