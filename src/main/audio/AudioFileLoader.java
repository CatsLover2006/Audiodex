package audio;

import audio.filetypes.decoders.*;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;

import java.io.File;

// Simply allows you to pass a file into the loadFile function
// and forwards that to the right filetype handler
public class AudioFileLoader {
    public static final String[] KNOWN_FILETYPES = {
            "wav", "wave", "aif", "aiff", "aifc", "mp1", "mp2", "mp3",
            "ogg", "oga", "mogg", "mp4", "m4a", "flac"
    };

    // Loads an audio file from disk
    public static AudioDecoder loadFile(String filename) {
        switch (getAudioFiletype(filename)) {
            case AIFF:
                return new Aiff(filename);
            case PCM_WAV:
                return new WAV(filename);
            case MPEG:
            case MP3: // Encoder catchall
                return new MpegType(filename);
            case FLAC:
                return new Flac(filename);
            case AAC_MP4:
                return new MP4AAC(filename);
            case ALAC_MP4:
                return new MP4alac(filename);
            case VORBIS:
                return new Vorbis(filename);
        }
        return null;
    }

    @SuppressWarnings("methodlength") // Large switch/case
    // No documentation needed, effectively a getter
    public static AudioFileType getAudioFiletype(String filename) {
        try {
            String filetype = filename.substring(filename.lastIndexOf('.') + 1);
            switch (filetype.toLowerCase()) {
                case "wav":
                case "wave":
                    return AudioFileType.PCM_WAV;
                case "aif":
                case "aiff":
                case "aifc":
                    return AudioFileType.AIFF;
                case "mp1":
                case "mp2":
                    return AudioFileType.MPEG;
                case "mp3":
                    return AudioFileType.MP3;
                case "flac":
                    return AudioFileType.FLAC;
                case "ogg":
                case "oga":
                case "mogg":
                    return oggAudioType(filename);
                case "mp4":
                case "m4a":
                    return m4aAudioType(filename);
                default:
                    return AudioFileType.EMPTY;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return AudioFileType.EMPTY;
        }
    }

    // Effects: detects if a .m4a file is ALAC or AAC
    private static AudioFileType m4aAudioType(String filename) {
        File file;
        try {
            file = new File(filename);
            AudioFile audio = AudioFileIO.readAs(file, "m4a");
            switch (audio.getAudioHeader().getEncodingType().toLowerCase()) {
                case "aac":
                    return AudioFileType.AAC_MP4;
                case "alac":
                    return AudioFileType.ALAC_MP4;
                default:
                    System.out.println("Unknown format: " + audio.getAudioHeader().getEncodingType().toLowerCase());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return AudioFileType.EMPTY;
    }

    // Effects: detects encoding of a .ogg-like file
    private static AudioFileType oggAudioType(String filename) {
        File file = new File(filename);
        try {
            AudioFile audio = AudioFileIO.readAs(file, "ogg");
            switch (audio.getAudioHeader().getEncodingType().toLowerCase()) {
                case "ogg vorbis v1":
                    return AudioFileType.VORBIS;
                default:
                    System.out.println("Unknown format: " + audio.getAudioHeader().getEncodingType().toLowerCase());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return AudioFileType.EMPTY;
    }
}
