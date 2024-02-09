package audio;

import audio.filetypes.decoders.*;

// Simply allows you to pass a file into the loadFile function
// and forwards that to the right filetype handler
public class AudioFileLoader {
    // No documentation needed, effectively a setter
    public static AudioDecoder loadFile(String filename) {
        String filetype = filename.substring(filename.length() - 4);
        switch (filetype.toLowerCase()) {
            case ".mp3":
                return new MP3(filename);
            case ".mp4":
            case ".m4a":
            case ".m4b":
                return new MP4(filename);
            default:
                return null;
        }
    }
}
