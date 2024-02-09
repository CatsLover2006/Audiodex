# Chance's CPSC 210 Personal Project

***NOTE***: This project cannot properly run within the IntelliJ IDE shell environment. This is due to it not properly supporting ANSI terminal commands, which I use extensively to assist with indicators. I will publish a JAR file release before I submit it to mark for Phase 1.<br>
***NOTE 2***: This project expects you have music files to play. Due to copyright law, I have not included any; as such, there is no way to do automatic testing of the audio encoding and decoding functions. 

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
- MP4, M4A, M4B: AAC (Decode only)
  - M4B needs verification
- MP3 (Decode only)
  - MP2, MP1 might be decodable as well, filetype detection is unimplemented

### Planned filetypes/codecs:
- MP4, M4A, M4B: AAC (Encode)
- AAC (Present but completely untested and probably broken)
- WAV (Uncompressed audio)
- AIFF (Uncompressed audio with ID3 tags)
- MP3 (Encode)
- OGG, OGA, MOGG: Vorbis
- FLAC (Lossless audio)
- (Hopefully) ALAC (Lossless audio)
- (Maybe) APE (Lossless audio)
- (Maybe) WMA

### Known Issues:
- Slow storage interfaces can cause audio popping
  - Likely due to the decoder being starved for data

## User Stories
- As a user, I want to play my music
- As a user, I want to be able to convert my music to a different format
- As a user, I want to manage my music library

In effect I'm creating a iTunes competitor.
