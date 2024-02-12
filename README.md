# Chance's CPSC 210 Personal Project

~~***NOTE***: This project cannot properly run within the IntelliJ IDE shell environment. This is due to it not properly supporting ANSI terminal commands, which I use extensively to assist with indicators. I will publish a JAR file release before I submit it to mark for Phase 1.~~ No longer an issue, I added a secondary mode that sort of fixes it under IntelliJ, enough to where it's usable.<br>
***NOTE 2***: This project expects you have music files to play. Due to copyright law, I have not included any; as such, there is no way to do automatic testing of the audio encoding and decoding functions.<br>
***NOTE 3***: This project saves data at an absolute location in the user's home directory, and it saves filenames as absolute references within the database. The former could be changed, but the latter is required for the database to function if the program is launched from a different location on the filesystem.

## AudioDex
![CLI Preview](./previewcli.png)<br>
An audiofile (hehe get it?) manager, which can play audio and reencode audio into different formats.

### Plans
- [x] Decode and play audio
  - [x] On a seperate thread so audio playback doesn't kill the main thread if something goes horribly wrong
  - [x] Uses a `getNextSample()` method for all codecs
- [x] Re-encode audio
  - [x] Also on a seperate thread
- [x] Manage a list of music files
  - [x] Includes ID3-based music titles
  - [x] Notes filetype, bitrate, codec and other useful information about the file (cache)
  - [x] Queue files from music list for playback
- [x] ID3 tag management
  - [x] Read ID3 tags
  - [x] Write ID3 tags
    - Tested using AIFF re-encoding
    - Will add full editing capability during GUI stage
  - [ ] (Hopefully) Read album art
    - Won't touch this with a ten foot pole until GUI time
  - [ ] (Maybe) Write album art
- [x] All this, preferably without requiring native binaries
  - I'm going to use libraries to handle decoding and encoding
  
## Filetype Support
### Decode (Library-dependent)
- [x] MP4, M4A, M4B: AAC
  - M4B needs verification (I have none of these files)
- [x] MP3, MP2
  - MP1 is probably decodable as well, but quite literally nobody uses this format so I can't test
- [x] OGG, OGA, MOGG: Vorbis
- [ ] (Probably) AAC
- [x] WAV
- [x] AIFF
- [ ] FLAC
- [x] M4A: ALAC
- [ ] (Maybe) APE
- [ ] (Maybe) WMA

### Encode (Library-dependent)
- [ ] (Hopefully) M4A: AAC
  - MP4 and M4B are redundant formats
- [ ] (Hopefully) MP3
  - MP2 and MP1 are irrelevant to encode to nowadays, and there are no Java libraries to do so
- [ ] (Hopefully) OGG: Vorbis
- [ ] (Hopefully) AAC
- [x] WAV
- [x] AIFF
  - Doesn't copy album artwork (I'll have to handle that differently)
- [ ] (Maybe) FLAC
- [ ] (Maybe) M4A: ALAC
- [ ] (Maybe) APE
- [ ] (Maybe) WMA

## Known Issues
- You can't remove audio files from the database (will be fixed soon)
- Slow storage interfaces can cause audio popping
  - Likely due to the decoder being starved for data
  - Doesn't apply to the MP3 audio decoder, which seems to cache the entire file compared to the others streaming the audio off disk
- ALAC vs AAC detection is weird since they share their container, there's no fix for this
- Sometimes seeking to the very last second of an MP3 file will cause an ArrayIndexOutOfBoundsException, which causes the main thread to die
  - Will fix by performing a bounds check, but I'm too tired rn

## Lucky Breaks
- jaudiotagger handles most of the ID3 metadata I care about
  - jaudiotagger header returns encoding type for *.m4a and *.ogg files
  - jaudiotagger can **write** ID3 metadata
- `InputStream.read(bytes[] b)` returns the number of bytes actually read
- viva-sound-alac existing
- Tritonus being open-source so I could add in AIFF `swot` support (little endian vs the standard big endian)
  - My code was merged into the only active branch! [See this commit](https://github.com/umjammer/tritonus/commit/37d9111a01c4ee6a8fa505627b4934d19c7e753d)
- JAADec playback example being easy to read so I could figure out what's going on
- mp3spi existing
- Tritonus `AudioOutputStream` supports AIFF out of the box
- java-vorbis-support existing

_I'm a really lucky person ain't I?_

## User Stories
- As a user, I want to play my music
  - As a user, I want to play my music library nonstop for an extended period of time
- As a user, I want to be able to convert my music to a different format
- As a user, I want to manage my music library

In effect I'm creating a iTunes competitor.<br>
I have made a program that I would intentionally go out of my way to use. It's still command line. Wow.
