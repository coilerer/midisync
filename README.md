# MIDI Sync — README
This document describes the MIDI synchronization runelite plugin (“midisync”) that plays MIDI tracks in alignment with game ticks. 

## What it does
- (Optional) Loads a SoundFont (.sf2) into a Java MIDI synthesizer and routes MIDI events through dedicated channels.
- Parses one or more MIDI tracks and schedules note on/off events precisely relative to game tick boundaries.
- Maintains tick-accurate playback by coupling MIDI timing to the game’s 600 ms tick cadence.
- Quantizes the MIDI notes over the entire tick. i.e notes can be played only at tick boundries, or spread across the tick via a divisor.
- Handles General MIDI percussion (channel 10, zero-based index 9) with a consistent drum kit selection per session.
- Provides a debug overlay to verify that the SoundFont and track selection are active.
- IT DOES NOT CHANGE ANY OF THE DEFAULT SOUND/MUSIC PLAYBACK

## External Requirements
- OPTIONAL: A General MIDI-compatible SoundFont (.sf2), Will fallback to the default java soundfont if a custom one (e.g. [the one made by lequietriot](https://rune-server.org/threads/204-soundbank-data-sf2-format.702247/#post5769900)) isnt provided.
- MIDI files you want to play in sync with ticks (dont ask me how to extract them from your game cache)
- TO RECREATE OSRS MUSIC PROPERLY, YOU NEED BOTH AN SF2 AND THE OSRS MIDIS.

## Quick start
1. Prepare assets
    - Obtain a GM-compatible SoundFont file, for example at:
        - <path-to-project-assets>/soundfonts/YourSoundFont.sf2

    - Place your MIDI files somewhere accessible with the following naming scheme:
        - C:/osrs_midi/1 - track1.mid
        - C:/osrs_midi/2 - scape_main.mid
        - C:/osrs_midi/73 - track73 lumby.mid
        - C:/osrs_midi/421 - track421.mid

2. Configure the midisync settings
    - SoundFont file path
    - Midi file path
    - Default track number: which track to start with.
    - Tack white/back lists (numerical)


## How synchronization works
- LLMs wrote most of this, so I had to ask them)...

- Game tick baseline
    - The core loop advances once per game tick (nominally every 600 ms). A monotonic counter is incremented at each tick.

- MIDI time model
    - MIDI events are parsed with their delta times (PPQ or SMPTE). These are converted into absolute times anchored to the current tick boundary.

- Sub-tick scheduling/quantization
    - Notes do not need to start exactly on tick edges. A dedicated scheduler queues note on/off events with fine-grained delays within each tick interval.

- Drift avoidance
    - Synchronization is anchored to tick boundaries. If the system experiences jitter, upcoming events are re-queued relative to the next tick to keep alignment stable.

## SoundFont guidance
- Use a General MIDI compliant .sf2 for best coverage of instruments and drums.
- If the SoundFont fails to load:
    - Double-check the file path and read permissions.
    - Ensure the file is a valid .sf2 and not compressed within an archive.

## Usage tips
- Switching tracks
    - new track will start on the next game tick. you can only move one track per game tick.

- Volume and mixing
    - Adjust master volume first. If you want more percussion (channel 9) or another channel then you can adjust each channel's volume. 

## Known limitations
- Java’s built-in software synthesizer (Gervill) is convenient but not identical to hardware synths or advanced samplers.
- Advanced MIDI features (aftertouch, RPN/NRPN, SysEx) may have limited or no effect depending on the synthesizer and SoundFont.
- Extremely dense MIDI data may require tuning the scheduling buffer and CPU headroom.

## Frequently asked questions
- What quantization do i use?
    - If you want a punchy but less accurate sound, then try low vales like 1-5.
    - if you want the music to sound as close to the origonal as posible, set qualization top multiples of 32 (e.g. 32, 64, 128, 256).
 
- I can still hear the default game music
    - mute in game music, this plugin cannot interface with the in game player.
- Whewre do i get the OSRS sound font and midis?
-   -Ask a search engine or an llm. I cant provice them, and currently plugins cannot extract them from the game cache.
