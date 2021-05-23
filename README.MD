# Need for Speed 2 Music Player PoC

Based on https://github.com/ValeryAnisimovsky/GameAudioPlayer/blob/master/Specs/EA-MUS.txt

You'll need to obtain original .mus and .map files from the game and put them the to SD card.

Actual ADPCM decoding is done in C++ since dealing with byte level in Java or Kotlin is a pain.

Interactive music can be tested using seek bar on main screen while playing. Most tracks have branching brackets 0-29, 30-72, 73-100.

## To do:
- add GPS interactive music supports with presets and scaling
- add better UI with cover files, controls
