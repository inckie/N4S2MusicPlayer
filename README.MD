# Need for Speed 2 Music Player PoC

Based on https://github.com/ValeryAnisimovsky/GameAudioPlayer/blob/master/Specs/EA-MUS.txt

You'll need to obtain original .mus and .map files from the game and put them the to SD card.

Actual ADPCM decoding is done in C++ since dealing with byte level in Java or Kotlin is a pain.

## To do:
- add testing UI for interactive music
- add GPS interactive music supports with presets
- add MediaSession support and audio focus handling
- add better UI with cover files, controls
