# Need for Speed 2 Music Player PoC

Based on https://github.com/ValeryAnisimovsky/GameAudioPlayer/blob/master/Specs/EA-MUS.txt

You'll need to obtain original .mus and .map files from the game and put them the to SD card somewhere. .asf files are also supported.

Interactive music can be tested using seek bar on main screen while playing. Most tracks have branching brackets 0-29, 30-72, 73-100.

To display track images, copy file tracks.json from the `files` repo directory to the same music directory and download required images from https://nfs.fandom.com/wiki/Need_for_Speed_II/Courses.
Wiki file names are preserved. Only icons files are used at the moment.

Actual ADPCM decoding is done in C++ since dealing with byte level in Java or Kotlin is a pain.

## To do:
- add GPS interactive music supports with presets and scaling
- get rid of binary-streams library, not really needed there