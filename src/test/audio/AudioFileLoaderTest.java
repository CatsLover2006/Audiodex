package audio;

import audio.filetypes.decoders.*;
import org.junit.jupiter.api.Test;

import static audio.AudioFileLoader.*;
import static org.junit.jupiter.api.Assertions.*;

public class AudioFileLoaderTest {
    @Test
    public void getAudioFiletypeTests() {
        assertEquals(AudioFileType.AIFF, getAudioFiletype("data/scarlet.aif"));
        assertEquals(AudioFileType.PCM_WAV, getAudioFiletype("data/scarlet.wav"));
        assertEquals(AudioFileType.PCM_WAV, getAudioFiletype("data/scarlet.aac.wav"));
        assertEquals(AudioFileType.PCM_WAV, getAudioFiletype("data/scarlet.mp3.wav"));
        assertEquals(AudioFileType.PCM_WAV, getAudioFiletype("data/scarlet.vorbis.wav"));
        assertEquals(AudioFileType.MP3, getAudioFiletype("data/scarlet.mp3"));
        assertEquals(AudioFileType.MPEG, getAudioFiletype("data/scarlet.mp2"));
        assertEquals(AudioFileType.FLAC, getAudioFiletype("data/scarlet.flac"));
        assertEquals(AudioFileType.EMPTY_OGG, getAudioFiletype("data/scarlet.flac.oga")); // :`(
        assertEquals(AudioFileType.AAC_MP4, getAudioFiletype("data/scarlet.aac.m4a"));
        assertEquals(AudioFileType.ALAC_MP4, getAudioFiletype("data/scarlet.alac.m4a"));
        assertEquals(AudioFileType.ALAC_MP4, getAudioFiletype("data/scarlet.lmao.m4a"));
        assertEquals(AudioFileType.EMPTY_MP4, getAudioFiletype("data/video.mp4"));
        assertEquals(AudioFileType.VORBIS, getAudioFiletype("data/scarlet.vorbis.ogg"));
    }

    @Test
    public void edgeCaseTests() {
        new AudioFileLoader(); // No need
        assertEquals(AudioFileType.EMPTY_MP4, getAudioFiletype("data/null.mp4"));
        assertEquals(AudioFileType.EMPTY_OGG, getAudioFiletype("data/null.ogg"));
        assertEquals(AudioFileType.EMPTY, getAudioFiletype("data/null"));
        assertEquals(AudioFileType.MP3, getAudioFiletype("data/null.mp3"));
    }

    @Test
    public void loadFileTests() {
        try {
            assertEquals(Aiff.class, loadFile("data/scarlet.aif").getClass());
            assertEquals(WAV.class, loadFile("data/scarlet.wav").getClass());
            assertEquals(WAV.class, loadFile("data/scarlet.aac.wav").getClass());
            assertEquals(WAV.class, loadFile("data/scarlet.vorbis.wav").getClass());
            assertEquals(WAV.class, loadFile("data/scarlet.mp3.wav").getClass());
            assertEquals(MpegType.class, loadFile("data/scarlet.mp3").getClass());
            assertEquals(MpegType.class, loadFile("data/scarlet.mp2").getClass());
            assertEquals(Flac.class, loadFile("data/scarlet.flac").getClass());
            assertNull(loadFile("data/scarlet.flac.oga")); // :`(
            assertEquals(MP4AAC.class, loadFile("data/scarlet.aac.m4a").getClass());
            assertEquals(MP4alac.class, loadFile("data/scarlet.alac.m4a").getClass());
            assertEquals(MP4alac.class, loadFile("data/scarlet.lmao.m4a").getClass());
            assertEquals(Vorbis.class, loadFile("data/scarlet.vorbis.ogg").getClass());
        } catch (NullPointerException e) {
            fail("Null pointer exception; something went horribly wrong");
        }
    }
}
