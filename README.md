# Audiodex

This is a temp title

## About
![GUI Preview](./previewgui.png)<br>
An audiofile (hehe get it?) manager, which can play audio and reencode audio into different formats.

### Plans
- [x] Decode and play audio
  - [x] On a seperate thread so audio playback doesn't kill/stall the main thread if something goes horribly wrong
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
  - [x] Read album art
    - GUI resizes the artwork on separate thread to speed up GUI loading (relevant on my 2008 machine)
  - [x] Write album art
    - Works in encoders, will add in GUI options later
- [x] All this, preferably without requiring native binaries
  - I'm going to use libraries to handle decoding and encoding
  
## Filetype Support
### Decode (Library-dependent)
- [x] MP4, M4A, M4B: AAC
  - M4B needs verification (I have none of these files)
- [x] MP3, MP2
  - MP1 is probably decodable as well, but quite literally nobody uses this format so I can't test
  - Needs improvement
- [x] OGG, OGA, MOGG: Vorbis
- [x] WAV
- [x] AIFF
- [x] FLAC
  - That was easy
- [x] M4A: ALAC
- [ ] (Maybe) APE
- [ ] (Maybe) WMA

### Encode (Library-dependent)
- [ ] (Hopefully) M4A: AAC
  - MP4 and M4B are redundant formats
- [x] MP3
  - MP2 and MP1 are irrelevant to encode to nowadays, and there are no Java libraries to do so
- [ ] (Hopefully) OGG: Vorbis
- [x] WAV
- [x] AIFF
- [ ] (Maybe) FLAC
  - [_Found library!_](https://sourceforge.net/projects/javaflacencoder/)
- [ ] (Maybe) M4A: ALAC
- [ ] (Maybe) APE
- [ ] (Maybe) WMA

## Known Issues
- You can't remove audio files from the database via the CLI
  - Only accessible through GUI or removing backend file
- Slow storage interfaces can cause audio popping
  - Likely due to the decoder being starved for data
  - Doesn't apply to the MP3 audio decoder, which seems to cache the entire file compared to the others streaming the audio off disk
- ALAC vs AAC detection is weird since they share their container, there's no fix for this
  - Similar situation for detection in the OGG container, but there's only one supported playback mode within this container so it's just to avoid throwing errors
- ~~Gain system is partially dependent on format and system~~
  - ~~No way to fix this, it's entirely dependent on Java and OS implementation~~
  - Pops up a quality error if it can't use gain (and then it'll crudely discard bits to make it work)
- ~~Sometimes hi-res audio, 24 bit samples, will fail to play~~
  - ~~Entirely OS and JDK dependent~~ Will pop up a quality error if it can't play (and then crudely discard bits to make it work)
  - Theoretically possible for 16 bit samples, but no implementation is going to gut that functionality (that's the standard for... EVERYTHING)
- If audio encodes too fast, it ends up skipping some code (AudioEncoder.encodedPercent never is checked during encoding)<br>That said you'd have to decode an ALAC file at over 1400 times realtime, which... good luck.

## Lucky Breaks
- jaudiotagger handles most of the ID3 metadata I care about
  - jaudiotagger header returns encoding type for \*.m4a and \*.ogg files
  - jaudiotagger can ***write*** ID3 metadata
    - <big>jaudiotagger can ***write*** album artwork</big>
- `InputStream.read(bytes[] b)` returns the number of bytes actually read
- viva-sound-alac existing
- Tritonus being open-source so I could add in AIFF `swot` support (little endian vs the standard big endian)
  - My code was merged into the only active branch! [See this commit](https://github.com/umjammer/tritonus/commit/37d9111a01c4ee6a8fa505627b4934d19c7e753d)
- JAADec playback example being easy to read so I could figure out what's going on
- mp3spi existing
- Tritonus `AudioOutputStream` supports AIFF out of the box
- java-vorbis-support existing
- The Java port of LAME existing
  - I genuinely didn't think I'd get lossy audio decoding due to library support so that was an insane find
- The port of JDK 11 to Mac OS X 10.6 to 10.11 existing
  - This project might've been dead if not for this
  - [Here's the port](https://github.com/Jazzzny/jdk-macos-legacy)
- GridBagLayout
  - All hail GridBagLayout
- [Dazzle UI Icons](https://dazzleui.pro/library/)

_I'm a really lucky person ain't I?_
