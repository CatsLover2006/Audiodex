# Chance's CPSC 210 Personal Project

~~***NOTE***: This project cannot properly run within the IntelliJ IDE shell environment. This is due to it not properly supporting ANSI terminal commands, which I use extensively to assist with indicators. I will publish a JAR file release before I submit it to mark for Phase 1.~~ No longer an issue, I added a secondary mode that sort of fixes it under IntelliJ, enough to where it's usable.<br>
***NOTE 2***: This project expects you have music files to play. Due to copyright law, I have not included any; as such, there is no way to do automatic testing of the audio encoding and decoding functions.<br>
***NOTE 3***: This project saves data at an absolute location in the user's home directory, and it saves filenames as absolute references within the database. The former could be changed, but the latter is required for the database to function if the program is launched from a different location on the filesystem.

## AudioDex
An audiofile (hehe get it?) manager, which can play audio and reencode audio into different formats.

### Plans
- [x] Decode and play audio
  - [x] On a seperate thread so audio playback doesn't kill the main thread if something goes horribly wrong
  - [x] Uses a `getNextSample()` method for all codecs
- [ ] Modify and reencode audio
  - [ ] Also on a seperate thread
- [x] Manage a list of music files
  - [x] Includes ID3-based music titles
  - [x] Notes filetype, bitrate, codec and other useful information about the file (cache)
- [x] Read and [ ] modify ID3 tags
  - [x] (Maybe) Including album art
- [x] All this, preferably without requiring native binaries
  - I'm going to use libraries to handle decoding and encoding
  
## Filetype Support
### Decode:
- [x] MP4, M4A, M4B: AAC
  - M4B needs verification
- [x] MP3
  - MP2, MP1 might be decodable as well, their filetype detection is unimplemented
- [ ] OGG, OGA, MOGG: Vorbis
- [ ] AAC
- [ ] WAV
- [ ] AIFF
- [ ] FLAC
- [ ] (Hopefully) ALAC
- [ ] (Maybe) APE
- [ ] (Maybe) WMA

### Encode:
- [ ] MP4, M4A, M4B: AAC
- [ ] MP3
- [ ] OGG, OGA, MOGG: Vorbis
- [ ] AAC
- [ ] WAV
- [ ] AIFF
- [ ] FLAC
- [ ] (Hopefully) ALAC
- [ ] (Maybe) APE
- [ ] (Maybe) WMA

## Known Issues:
- Slow storage interfaces can cause audio popping
  - Likely due to the decoder being starved for data

## User Stories
- As a user, I want to play my music
- As a user, I want to be able to convert my music to a different format
- As a user, I want to manage my music library

In effect I'm creating a iTunes competitor.
