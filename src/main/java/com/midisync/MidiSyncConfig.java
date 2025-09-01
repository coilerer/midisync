package com.midisync;

import net.runelite.client.config.*;

@ConfigGroup("MidiSync")
public interface MidiSyncConfig extends Config
{
	@ConfigSection(
			name = "MidiSync",
			description = "Sync the osrs midi tracks to the tick rate",
			position =1,
			closedByDefault = false
	)
	String midisync_section = "MidiSync";

	@ConfigItem(
			position = 0,
			keyName = "MidiSync",
			name = "MidiSync",
			description = "MidiSync enable",
			section = "MidiSync"
	)
	default boolean MidiSync()
	{
		return false;
	}
	@ConfigItem(
			keyName = "bpm",
			name = "BPM",
			description = "Set playback speed in BPM (try 50 or 200 if you don't like 100BPM)",
			section = "MidiSync",
			position = 1
	)
	default int bpm()
	{
		return 100;
	}

	@ConfigItem(
			keyName = "midiFolder",
			name = "MIDI Folder",
			description = "Folder containing OSRS (or other) MIDI files. Track must be in correct format (1-trackname.mid)",
			section = "MidiSync",
			position = 3
	)
	default String midiFolder()
	{
		return "C:/osrs_midis";
	}
	@ConfigItem(
			keyName = "midiVolume",
			name = "Midi Volume",
			description = "Main volume. 0-100",
			section = midisync_section,
			position = 4
	)
	default int midiVolume() {
		return 50;
	}

	@ConfigItem(
			keyName = "midiSoundfont",
			name = "SoundFont File",
			description = "Optional .sf2 SoundFont file for OSRS instruments. Not tested with other soundfonts",
			section = midisync_section,
			position = 7
	)
	default String midiSoundfont() {
		return "";
	}
	@ConfigItem(
			keyName = "startingTrack",
			name = "Start Track",
			description = "Track number (from 1..N in the folder) to load when starting the plugin",
			section = midisync_section,
			position = 8
	)
	default int startingTrack()
	{
		return 16; //16 is scapemain
	}
	@ConfigItem(
			keyName = "shuffleTracks",
			name = "Shuffle Tracks",
			description = "If enabled, tracks will play in random order",
			section = midisync_section,
			position = 9
	)
	default boolean shuffleTracks() {
		return false;
	}
	@ConfigItem(
			keyName = "nextTrack",
			name = "Next Track",
			description = "Manually skip to the next track. Only works once per tick for now",
			section = midisync_section,
			position = 10
	)
	default boolean nextTrack()
	{
		return false;
	}

	@ConfigItem(
			keyName = "prevTrack",
			name = "Previous Track",
			description = "Manually skip to the previous track. Only works once per tick for now",
			section = midisync_section,
			position = 11
	)
	default boolean prevTrack()
	{
		return false;
	}
	// ======================
	// Quantization (sub-tick support)
	// ======================
	@ConfigItem(
			keyName = "quantizeDivisor",
			name = "Quantization Divisor",
			description = "Number of beats per tick (1 = full beat, 2 = half beat, 3 = third, ...). Try very low numbers for more obvious beats.",
			section = midisync_section,
			position = 2

	)
	@Range(min = 1, max = 256)
	default int quantizeDivisor() {
		return 64; // default = 64
	}

	// ======================
	// Tick duration (ms) for sub-tick scheduling
	// ======================
	@ConfigItem(
			keyName = "tickDurationMs",
			name = "Game Tick Duration (ms)",
			description = "DON'T MESS WITH THIS. Milliseconds per game tick (default 600 ms). Adjust if needed for timing.",
			section = midisync_section
	)
	default int tickDurationMs() { return 600; }

	@ConfigItem(
			keyName = "trackWhitelist",
			name = "Track Whitelist",
			description = "Comma-separated list of track numbers to include (empty = all tracks)",
			section = midisync_section
	)
	default String trackWhitelist()
	{
		return ""; // empty = all tracks
	}

	@ConfigItem(
			keyName = "trackBlacklist",
			name = "Track Blacklist",
			description = "Comma-separated list of track numbers to exclude",
			section = midisync_section
	)
	default String trackBlacklist()
	{
		return ""; // empty = no exclusions
	}
	@ConfigItem(
			keyName = "Dev Overlay",
			name = "Dev Overlay",
			description = "Shows an overlay with track, tick, and timing info",
			section = midisync_section
	)
	default boolean MidiSyncDevOverlay()
	{
		return false;
	}
	@ConfigItem(
			keyName = "osrsOnly",
			name = "EXPERIMENTAL: OSRS-Only Sounds",
			description = "EXPERIMENTAL: If enabled, only OSRS-mapped instruments will play, blocking all others",
			section = midisync_section
	)
	default boolean osrsOnly()
	{
		return false;
	}
	@ConfigSection(
			name = "MIDI Volume Per Channel",
			description = "Fine-grained volume control for each MIDI channel (0–15)",
			position = 2,
			closedByDefault = true
	)
	String midi_channel_volumes = "midiChannelVolumes";

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel0Volume",
			name = "Channel 0 Volume",
			description = "Adjust volume multiplier for MIDI channel 0",
			section = midi_channel_volumes,
			position = 0

	)
	default int channel0Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel1Volume",
			name = "Channel 1 Volume",
			description = "Adjust volume multiplier for MIDI channel 1",
			section = midi_channel_volumes,
			position = 1
	)
	default int channel1Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel2Volume",
			name = "Channel 2 Volume",
			description = "Adjust volume multiplier for MIDI channel 2",
			section = midi_channel_volumes,
			position = 2
	)
	default int channel2Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel3Volume",
			name = "Channel 3 Volume",
			description = "Adjust volume multiplier for MIDI channel 3",
			section = midi_channel_volumes,
			position = 3
	)
	default int channel3Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel4Volume",
			name = "Channel 4 Volume",
			description = "Adjust volume multiplier for MIDI channel 4",
			section = midi_channel_volumes,
			position = 4
	)
	default int channel4Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel5Volume",
			name = "Channel 5 Volume",
			description = "Adjust volume multiplier for MIDI channel 5",
			section = midi_channel_volumes,
			position = 5
	)
	default int channel5Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel6Volume",
			name = "Channel 6 Volume",
			description = "Adjust volume multiplier for MIDI channel 6",
			section = midi_channel_volumes,
			position = 6
	)
	default int channel6Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel7Volume",
			name = "Channel 7 Volume",
			description = "Adjust volume multiplier for MIDI channel 7",
			section = midi_channel_volumes,
			position = 6
	)
	default int channel7Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel8Volume",
			name = "Channel 8 Volume",
			description = "Adjust volume multiplier for MIDI channel 8",
			section = midi_channel_volumes,
			position = 7
	)
	default int channel8Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel9Volume",
			name = "Channel 9 Volume",
			description = "Adjust volume multiplier for MIDI channel 9 (percussion)",
			section = midi_channel_volumes,
			position = 8
	)
	default int channel9Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel10Volume",
			name = "Channel 10 Volume",
			description = "Adjust volume multiplier for MIDI channel 10",
			section = midi_channel_volumes,
			position = 9
	)
	default int channel10Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel11Volume",
			name = "Channel 11 Volume",
			description = "Adjust volume multiplier for MIDI channel 11",
			section = midi_channel_volumes,
			position = 10
	)
	default int channel11Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel12Volume",
			name = "Channel 12 Volume",
			description = "Adjust volume multiplier for MIDI channel 12",
			section = midi_channel_volumes,
			position = 11
	)
	default int channel12Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel13Volume",
			name = "Channel 13 Volume",
			description = "Adjust volume multiplier for MIDI channel 13",
			section = midi_channel_volumes,
			position = 12
	)
	default int channel13Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel14Volume",
			name = "Channel 14 Volume",
			description = "Adjust volume multiplier for MIDI channel 14",
			section = midi_channel_volumes,
			position = 13
	)
	default int channel14Volume() { return 100; }

	@Range(min = 0, max = 200)
	@ConfigItem(
			keyName = "channel15Volume",
			name = "Channel 15 Volume",
			description = "Adjust volume multiplier for MIDI channel 15",
			section = midi_channel_volumes,
			position = 14
	)
	default int channel15Volume() { return 100; }
}
