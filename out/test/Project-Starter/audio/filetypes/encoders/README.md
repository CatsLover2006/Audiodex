# Encoder tests

## There's not going to be any automated testing

Due to the nature of audio encoding needing to drop something on the filesystem, there is no way I am comfortable doing tests on a system which might have a failing hard drive. The decoders are already fairly temperamental at times, so automated testing of the encoders is going to be impossible. I have verified they work; in fact, all of the uncompressed test comparison files (scarlet.mp3.wav, scarlet.vorbis.wav, and scarlet.acc.wav) were generated using the WAV encoder, due to the nature of some decoders (MP3 in particular) being inconsistent across different programs.
