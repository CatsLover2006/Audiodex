package audio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Semi-automated testing
public class AudioFilePlaybackBackendTest {
    AudioFilePlaybackBackend handler;
    @BeforeEach
    public void prepare() {
        handler = new AudioFilePlaybackBackend();
        handler.loadAudio("data/scarlet.mp3");
    }

    @Test
    public void seekThenUnloadAudioTest() {
        assertTrue(handler.audioIsLoaded());
        handler.startAudioDecoderThread();
        handler.playAudio();
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            fail("Why am I interrupted?");
        }
        handler.pauseAudio();
        handler.seekTo(handler.getFileDuration() - 5);
        handler.playAudio();
        handler.waitForAudioFinish();
        assertFalse(handler.audioIsLoaded());
    }

    @Test
    public void playAudioTest() {
        assertTrue(handler.audioIsLoaded());
        handler.startAudioDecoderThread();
        handler.playAudio();
        assertTrue(handler.audioIsLoaded());
        assertFalse(handler.paused());
        handler.pauseAudio();
        assertTrue(handler.paused());
        handler.playAudio();
        assertFalse(handler.paused());
        handler.waitForAudioFinish(); // Yes this is now a 2.5 min test
        assertFalse(handler.audioIsLoaded());
    }
}
