package audio;

import audio.filetypes.decoders.*;
import de.jarnbjo.ogg.FileStream;
import de.jarnbjo.ogg.LogicalOggStream;
import de.jarnbjo.ogg.PhysicalOggStream;
import model.Event;
import model.EventLog;
import model.ExceptionIgnore;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;

import java.io.File;
import java.io.RandomAccessFile;

// Simply allows you to pass a file into the loadFile function
// and forwards that to the right filetype handler
public class AudioFileLoader {
    private static EventLog logger = EventLog.getInstance();
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
            case "m4p":
                return m4aAudioType(filename);
            default:
                return AudioFileType.EMPTY;
        }
    }

    // Effects: detects if a .m4a file is ALAC or AAC
    private static AudioFileType m4aAudioType(String filename) {
        File file;
        try {
            file = new File(filename);
            AudioFile audio = AudioFileIO.readAs(file, "m4a");
            logger.logEvent(new Event("Got format " + audio.getAudioHeader().getEncodingType().toLowerCase()
                    + " for file " + filename + "."));
            switch (audio.getAudioHeader().getEncodingType().toLowerCase()) {
                case "aac":
                    return AudioFileType.AAC_MP4;
                case "alac":
                    return AudioFileType.ALAC_MP4;
            }
        } catch (Exception e) {
            ExceptionIgnore.logException(e);
        }
        return AudioFileType.EMPTY_MP4;
    }

    // Effects: detects encoding of a .ogg-like file
    private static AudioFileType oggAudioType(String filename) {
        RandomAccessFile file;
        try {
            file = new RandomAccessFile(new File(filename), "r");
            PhysicalOggStream streams = new FileStream(file);
            for (Object stream : streams.getLogicalStreams()) {
                if (stream instanceof LogicalOggStream) {
                    LogicalOggStream oggStream = (LogicalOggStream) stream;
                    switch (oggStream.getFormat()) {
                        case LogicalOggStream.FORMAT_VORBIS:
                            return AudioFileType.VORBIS;
                        default:
                            return AudioFileType.EMPTY_OGG;
                    }
                }
            }
        } catch (Exception e) {
            ExceptionIgnore.logException(e);
        }
        return AudioFileType.EMPTY_OGG;
    }
}
