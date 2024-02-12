package audio;

import audio.filetypes.decoders.*;

// Simply allows you to pass a file into the loadFile function
// and forwards that to the right filetype handler
public class AudioFileLoader {
    // No documentation needed, effectively a setter
    public static AudioDecoder loadFile(String filename) {
        switch (getAudioFiletype(filename)) {
            case AIFF:
                return new Aiff(filename);
            case PCM_WAV:
                return new WAV(filename);
            case MP3:
                return new MP3(filename);
            case AAC_MP4:
                return new MP4(filename);
        }
        return null;
    }

    // No documentation needed, effectively a getter
    public static AudioFileType getAudioFiletype(String filename) {
        String filetype = filename.substring(filename.length() - 4);
        switch (filetype.toLowerCase()) {
            case ".wav":
            case "wave":
                return AudioFileType.PCM_WAV;
            case ".aif":
            case "aiff":
            case "aifc":
                return AudioFileType.AIFF;
            case ".mp1":
            case ".mp2":
            case ".mp3":
                return AudioFileType.MP3;
            case ".mp4":
            case ".m4a":
            case ".m4b":
                return AudioFileType.AAC_MP4;
            default:
                return AudioFileType.EMPTY;
        }
    }
}
