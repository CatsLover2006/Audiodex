package audio;

public enum AudioFileType {
    AAC_MP4(2),
    ALAC_MP4(5),
    MP3(3),
    MPEG(3),
    PCM_WAV(1),
    FLAC(6),
    AIFF(1),
    VORBIS(4),
    EMPTY(0);

    public final int iconIndex;

    AudioFileType(int iconIndex) {
        this.iconIndex = iconIndex;
    }
}
