package audio;

import java.util.LinkedList;
import java.util.List;

// Represents an audio sample
// This class only exists so that different audio decoders
// can cooperate better with each other
// No dedicated test class since it's used LITERALLY everywhere
public class AudioSample {
    private byte[] data;
    private int length;
    private static final List<Byte> byteList = new LinkedList<Byte>();

    // Empty audio sample (zero bytes long and zero data)
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

    // Modifies: this
    // Effects:  reduces sample bit depth by 8 bits (remove 1 byte)
    //           implementation looks cursed due to the fact that I'm never allocating a new object
    //           (I tried it that way, anything above 16 bit was unlistenable due to slowdown)
    public void reduceBitdepth(int currentSampleBitdepth, boolean bigEndian) {
        int j = 0;
        for (int i = 0; i < data.length; i++) {
            if ((i + (bigEndian ? 1 : 0)) % currentSampleBitdepth != 0) {
                data[j] = data[i];
                j++;
            }
        }
        length = length * (currentSampleBitdepth - 1) / currentSampleBitdepth;
    }
}
