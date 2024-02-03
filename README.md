# Chance's CPSC 210 Personal Project

## AudioDex
An audiofile (hehe get it?) manager, which can play audio and reencode audio into different formats.

### Plans
- Decode and play audio
  - On a seperate thread so audio playback doesn't kill the main thread if something goes horribly wrong
  - Uses a `getNextSample()` method for all codecs
- Modify and reencode audio
  - Also on a seperate thread
- Manage a list of music files
  - Includes ID3-based music titles
  - Notes filetype, bitrate, codec and other useful information about the file (cache?)
- Read and modify ID3 tags
  - (Maybe) Including album art
- All this, preferably without requiring native binaries
  - I'm going to use libraries to handle decoding and encoding, most use native executables
  
### Currently supported filetypes/codecs:
- MP4, M4A, M4B: AAC (Decode only, jank AF)
  - M4B needs verification

### Planned filetypes/codecs:
- MP4, M4A, M4B: AAC (Encode)
- AAC (Present but completely untested and probably broken)
- WAV (Uncompressed audio)
- AIFF (Uncompressed audio with ID3 tags)
- MP3
- OGG, OGA, MOGG: Vorbis
- FLAC (Lossless audio)
- (Hopefully) ALAC (Lossless audio)
- (Maybe) APE (Lossless audio)
- (Maybe) WMA
