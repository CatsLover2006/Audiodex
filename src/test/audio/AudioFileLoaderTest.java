package audio;

import audio.filetypes.decoders.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static audio.AudioFileLoader.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class AudioFileLoaderTest {
    @Test
    public void getAudioFiletypeTests() {
        assertEquals(AudioFileType.AIFF, getAudioFiletype("data/scarlet.aif"));
        assertEquals(AudioFileType.PCM_WAV, getAudioFiletype("data/scarlet.wav"));
        assertEquals(AudioFileType.PCM_WAV, getAudioFiletype("data/scarlet.aac.wav"));
        assertEquals(AudioFileType.PCM_WAV, getAudioFiletype("data/scarlet.mp3.wav"));
        assertEquals(AudioFileType.PCM_WAV, getAudioFiletype("data/scarlet.vorbis.wav"));
        assertEquals(AudioFileType.MP3, getAudioFiletype("data/scarlet.mp3"));
        assertEquals(AudioFileType.AAC_MP4, getAudioFiletype("data/scarlet.aac.m4a"));
        assertEquals(AudioFileType.ALAC_MP4, getAudioFiletype("data/scarlet.alac.m4a"));
        assertEquals(AudioFileType.ALAC_MP4, getAudioFiletype("data/scarlet.lmao.m4a"));
        assertEquals(AudioFileType.VORBIS, getAudioFiletype("data/scarlet.vorbis.ogg"));
    }

    @Test
    public void loadFileTests() {
        try {
            assertEquals(Aiff.class, loadFile("data/scarlet.aif").getClass());
            assertEquals(WAV.class, loadFile("data/scarlet.wav").getClass());
            assertEquals(WAV.class, loadFile("data/scarlet.aac.wav").getClass());
            assertEquals(WAV.class, loadFile("data/scarlet.vorbis.wav").getClass());
            assertEquals(WAV.class, loadFile("data/scarlet.mp3.wav").getClass());
            assertEquals(MP3.class, loadFile("data/scarlet.mp3").getClass());
            assertEquals(MP4AAC.class, loadFile("data/scarlet.aac.m4a").getClass());
            assertEquals(MP4alac.class, loadFile("data/scarlet.alac.m4a").getClass());
            assertEquals(MP4alac.class, loadFile("data/scarlet.lmao.m4a").getClass());
            assertEquals(Vorbis.class, loadFile("data/scarlet.vorbis.ogg").getClass());
        } catch (NullPointerException e) {
            fail("Null pointer exception; something went horribly wrong");
        }
    }
}
