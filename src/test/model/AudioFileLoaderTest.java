package model;

import ui.audio.AudioFileType;
import org.junit.jupiter.api.Test;
import ui.audio.filetypes.decoders.*;

import static model.AudioFileLoader.*;
import static org.junit.jupiter.api.Assertions.*;

public class AudioFileLoaderTest {
    @Test
    public void lmao() {
        assertEquals(AudioFileLoader.class, (new AudioFileLoader()).getClass());
    }

    @Test
    public void getAudioFiletypeTests() {
        assertEquals(AudioFileType.AIFF, getAudioFiletype("data/scarlet.aif"));
        assertEquals(AudioFileType.PCM_WAV, getAudioFiletype("data/scarlet.wav"));
        assertEquals(AudioFileType.MP3, getAudioFiletype("data/scarlet.mp3"));
        assertEquals(AudioFileType.AAC_MP4, getAudioFiletype("data/scarlet.aac.m4a"));
        assertEquals(AudioFileType.ALAC_MP4, getAudioFiletype("data/scarlet.alac.m4a"));
        assertEquals(AudioFileType.EMPTY, getAudioFiletype("data/scarlet.lmao.m4a"));
        assertEquals(AudioFileType.EMPTY, getAudioFiletype("data/scarlet.m4a"));
        assertEquals(AudioFileType.EMPTY, getAudioFiletype("data/scarlet.ogg"));
        assertEquals(AudioFileType.EMPTY, getAudioFiletype("data/scarlet.m44"));
        assertEquals(AudioFileType.EMPTY, getAudioFiletype("data/scarlet.lmao.ogg"));
        assertEquals(AudioFileType.VORBIS, getAudioFiletype("data/scarlet.vorbis.ogg"));
    }

    @Test
    public void loadFileTests() {
        try {
            assertEquals(Aiff.class, loadFile("data/scarlet.aif").getClass());
            assertEquals(WAV.class, loadFile("data/scarlet.wav").getClass());
            assertEquals(MP3.class, loadFile("data/scarlet.mp3").getClass());
            assertEquals(MP4AAC.class, loadFile("data/scarlet.aac.m4a").getClass());
            assertEquals(MP4alac.class, loadFile("data/scarlet.alac.m4a").getClass());
            assertEquals(Vorbis.class, loadFile("data/scarlet.vorbis.ogg").getClass());
            assertNull(loadFile("data/scarlet.lmao.ogg"));
            assertNull(loadFile("data/scarlet.lmao.m4a"));
            assertNull(loadFile("data/scarlet.m4a"));
            assertNull(loadFile("data/scarlet.ogg"));
        } catch (NullPointerException e) {
            fail("Null pointer exception; something went horribly wrong");
        }
    }
}
