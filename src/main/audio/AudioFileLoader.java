package audio;

import audio.filetypes.decoders.*;

public class AudioFileLoader {
    public static AudioDecoder loadFile(String filename) {
        String filetype = filename.substring(filename.length() - 4);
        switch (filetype.toLowerCase()) {
            case ".mp4":
            case ".m4a":
            case ".m4b":
                return new MP4(filename);
            default:
                return null;
        }
    }
}
