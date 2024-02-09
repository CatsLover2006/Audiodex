package audio;

public class AudioSample {
    private byte[] data;
    private int length;

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
