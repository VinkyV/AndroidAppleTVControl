# AppleTVControl for Android!

Connects to AppleTV using companion protocol!

# Built using [chaquopy](https://github.com/chaquo/chaquopy) & [pyatv](https://github.com/postlund/pyatv)
## Pairing

The app pairs two protocols, and each one shows its own PIN on the TV:

1. **Companion**: remote buttons, D-pad, volume, play/pause.
2. **AirPlay**: now-playing info, album artwork and seeking. On tvOS 15+ these come from MRP, which is tunneled over AirPlay.

You can skip the AirPlay step and pair it later from the Audio tab.

## Player & volume keys

- While connected with AirPlay paired, the Apple TV's now playing info appears in Android's system media player (notification shade and lock screen), with artwork, play/pause, next/previous, seeking and disconnect.
- On the **Audio** tab the phone's volume buttons change the Apple TV's volume. On other tabs they control the phone as usual.
- While the phone is locked or the screen is off, the volume buttons also change the Apple TV's volume (Android shows its remote volume slider). Once unlocked, they control the phone again.

## Touchpad

The Remote tab has a Siri Remote style touchpad (switch to the D-pad with the toggle above it):
- Swipe or drag to move. Long swipes move several steps.
- Tap to select.
- Hold for options (select + hold). This needs AirPlay paired.

