package ui.audio;

// Represents an audio sample
// This class only exists so that different audio decoders
// can cooperate better with each other
public class AudioSample {
    private final byte[] data;
    private final int length;

    public AudioSample() {
        data = new byte[] {};
        length = 0;
    }

    public AudioSample(byte[] data) {
        this.data = data;
        length = data.length;
    }

    public AudioSample(byte[] data, int length) {
        this.data = data;
        this.length = length;
    }

    public byte[] getData() {
        return data;
    }

    public int getLength() {
        return length;
    }
}
