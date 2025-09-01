package com.MidiSync;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.sound.midi.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
		name = "MIDI Sync",
		description = "Plays MIDI tracks synchronized to game ticks",
		tags = {"music", "midi", "audio"}
)
public class MidiSyncPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ConfigManager configManager;
	@Inject private MidiSyncConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private MidiSyncOverlay debugOverlay;

	private Synthesizer synth;
	private MidiChannel[] channels;

	private final Map<Integer, MidiChannel> instrumentChannelMap = new HashMap<>();
	private int nextAvailableChannel = 0;

	private List<danielMidiNote> notes;
	private List<List<danielMidiNote>> trackNotes;
	private int currentTrackIndex = 0;
	private double gameTickCounter = 0.0;
	private String sf2Status = "No SoundFont loaded";

	private List<String> trackNames;

	private MidiSyncOverlay overlay;
	private ScheduledExecutorService noteScheduler = Executors.newSingleThreadScheduledExecutor();
	private final Set<String> availableBankProgram = new HashSet<>();

	// Cache per-channel bank/program to avoid redundant CC/programChange spam
	private final int[] chBank = new int[16];
	private final int[] chProgram = new int[16];

	// Drum-kit selection (for channel 9) — resolved once per session / SF2
	private boolean percussionKitLocked = false;
	private int percussionBank = -1;
	private int percussionProgram = -1;

	private int getChannelVolume(int channel)
	{
		switch (channel)
		{
			case 0: return config.channel0Volume();
			case 1: return config.channel1Volume();
			case 2: return config.channel2Volume();
			case 3: return config.channel3Volume();
			case 4: return config.channel4Volume();
			case 5: return config.channel5Volume();
			case 6: return config.channel6Volume();
			case 7: return config.channel7Volume();
			case 8: return config.channel8Volume();
			case 9: return config.channel9Volume();  // percussion
			case 10: return config.channel10Volume();
			case 11: return config.channel11Volume();
			case 12: return config.channel12Volume();
			case 13: return config.channel13Volume();
			case 14: return config.channel14Volume();
			case 15: return config.channel15Volume();
			default: return 100;
		}
	}

	@Provides
	MidiSyncConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MidiSyncConfig.class);
	}

	/** Returns the tick subdivision (e.g., 1/4, 1/8, ...). */
	public double configQuantizationValue()
	{
		int divisor = config.quantizeDivisor();
		return divisor > 0 ? 1.0 / divisor : 1.0;
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Starting Daniel MIDI Sync...");
		if (!config.MidiSync()) return;

		try { initSynth(); } catch (Exception e) { log.error("Failed to initialise synthesizer", e); return; }

		File folder = new File(config.midiFolder());
		if (!folder.exists() || !folder.isDirectory()) return;

		File[] midiFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".mid"));
		if (midiFiles == null || midiFiles.length == 0) return;

		Set<Integer> whitelist = parseTrackList(config.trackWhitelist());
		Set<Integer> blacklist = parseTrackList(config.trackBlacklist());

		Arrays.sort(midiFiles, Comparator.comparingInt(f -> {
			try { return Integer.parseInt(f.getName().split(" - ")[0]); }
			catch (NumberFormatException e) { return Integer.MAX_VALUE; }
		}));

		trackNames = new ArrayList<>();
		trackNotes = new ArrayList<>();
		Map<Integer, Integer> trackNumberToIndex = new HashMap<>();
		double quantization = configQuantizationValue();

		for (File midiFile : midiFiles)
		{
			int trackNumber;
			try { trackNumber = Integer.parseInt(midiFile.getName().split(" - ")[0]); }
			catch (NumberFormatException e) { trackNumber = -1; }

			if ((!whitelist.isEmpty() && !whitelist.contains(trackNumber)) ||
					blacklist.contains(trackNumber)) continue;

			try
			{
				List<danielMidiNote> fileNotes = loadMidi(midiFile, config.bpm(), quantization);
				trackNotes.add(fileNotes);
				trackNames.add(midiFile.getName());
				if (trackNumber != -1) trackNumberToIndex.put(trackNumber, trackNotes.size() - 1);
				log.info("Loaded {} notes from {}", fileNotes.size(), midiFile.getName());
			}
			catch (Exception e)
			{
				log.warn("Failed to load MIDI {}: {}", midiFile.getName(), e.toString());
			}
		}

		int startingTrackNumber = config.startingTrack();
		Integer startIndex = trackNumberToIndex.getOrDefault(startingTrackNumber, 0);

		currentTrackIndex = startIndex;
		if (!trackNotes.isEmpty()) notes = trackNotes.get(currentTrackIndex);
		gameTickCounter = 0.0;

		overlay = new MidiSyncOverlay(client, this, config);
		overlayManager.add(overlay);

		log.info("Overlay added. Synth channels = {}", channels != null ? channels.length : 0);
	}

	@Override
	protected void shutDown() throws Exception
	{
		stopAllNotes();
		if (notes != null) notes.clear();
		if (trackNotes != null) trackNotes.clear();
		if (synth != null) synth.close();

		gameTickCounter = 0;
		currentTrackIndex = 0;

		if (overlay != null)
		{
			overlayManager.remove(overlay);
			overlay = null;
		}

		noteScheduler.shutdownNow();
		log.info("Daniel MIDI Sync stopped.");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.MidiSync() || notes == null || notes.isEmpty()) return;

		double tickDurationMs = config.tickDurationMs();

		for (danielMidiNote note : notes)
		{
			if (note.getStartTickFraction() >= gameTickCounter
					&& note.getStartTickFraction() < gameTickCounter + 1.0)
			{
				scheduleNoteDynamic(note, tickDurationMs);
			}
		}

		gameTickCounter += 1.0;

		double maxTick = notes.stream().mapToDouble(danielMidiNote::getEndTickFraction).max().orElse(0.0);
		if (gameTickCounter > maxTick && trackNotes.size() > 1) skipTrack(true);

		if (config.nextTrack())
		{
			skipTrack(true);
			configManager.setConfiguration("danielPlugin", "nextTrack", false);
		}

		if (config.prevTrack())
		{
			skipTrack(false);
			configManager.setConfiguration("danielPlugin", "prevTrack", false);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("danielPlugin")) return;
		if (event.getKey().equals("quantizeDivisor")) reQuantizeCurrentTrack();
	}

	private void initSynth() throws Exception
	{
		synth = MidiSystem.getSynthesizer();
		if (!synth.isOpen()) synth.open();
		channels = synth.getChannels();

		String sf2Path = config.midiSoundfont();
		if (sf2Path != null && !sf2Path.isEmpty())
		{
			File sf2File = new File(sf2Path);
			if (sf2File.exists())
			{
				Soundbank sb = MidiSystem.getSoundbank(sf2File);
				if (sb != null && synth.isSoundbankSupported(sb))
				{
					synth.loadAllInstruments(sb);
					sf2Status = "SoundFont loaded: " + sf2File.getName();

					for (Instrument instr : sb.getInstruments())
					{
						Patch p = instr.getPatch();
						availableBankProgram.add(p.getBank() + "-" + p.getProgram());
					}
				}
				else sf2Status = "SoundFont not supported or returned null";
			}
			else sf2Status = "SoundFont file does not exist: " + sf2Path;
		}
		else sf2Status = "No custom SoundFont specified, using default GM bank";

		log.info(sf2Status);
		resetChannelState();
	}

	/** Find/assign a channel for a melodic/vocal instrument (never channel 9). */
	private MidiChannel getChannelForInstrument(int osrsInstrument)
	{
		if (instrumentChannelMap.containsKey(osrsInstrument))
			return instrumentChannelMap.get(osrsInstrument);

		// Find next free non-9 channel deterministically
		for (int tries = 0; tries < channels.length; tries++)
		{
			if (nextAvailableChannel >= channels.length) nextAvailableChannel = 0;
			if (nextAvailableChannel == 9) { nextAvailableChannel++; continue; } // skip drums
			MidiChannel candidate = channels[nextAvailableChannel++];
			if (candidate != null && !instrumentChannelMap.containsValue(candidate))
			{
				instrumentChannelMap.put(osrsInstrument, candidate);
				applyProgramChange(candidate, osrsInstrument);
				return candidate;
			}
		}
		// Fallback (shouldn't happen): use channel 0
		MidiChannel ch = channels[0];
		applyProgramChange(ch, osrsInstrument);
		return ch;
	}

	/** Apply OSRS program mapping with correct 14-bit bank select (MSB/LSB). */
	private void applyProgramChange(MidiChannel ch, int osrsInstrument)
	{
		int bank = OSRSMidiMapper.mapBank(osrsInstrument);
		int program = OSRSMidiMapper.mapProgram(osrsInstrument);

		// If SF2 doesn't have this patch, try a graceful fallback to bank 0
		if (!patchExists(bank, program) && patchExists(0, program))
			bank = 0;

		setBankAndProgram(ch, 0, bank, program); // CC0=MSB, CC32=LSB (derived inside)
	}

	/** True if the loaded SF2 provides (bank,program). */
	private boolean patchExists(int bank, int program)
	{
		return availableBankProgram.isEmpty() || availableBankProgram.contains(bank + "-" + program);
	}

	/** Send proper bank select (MSB/LSB) + program change, with per-channel caching. */
	private void setBankAndProgram(MidiChannel ch, int chIndex, int bank, int program)
	{
		if (chIndex < 0 || chIndex >= chBank.length) return;

		if (chBank[chIndex] == bank && chProgram[chIndex] == program)
			return; // already set

		int msb = (bank >> 7) & 0x7F;  // high 7 bits
		int lsb = bank & 0x7F;         // low 7 bits

		ch.controlChange(0, msb);      // Bank Select MSB
		ch.controlChange(32, lsb);     // Bank Select LSB
		ch.programChange(program);

		chBank[chIndex] = bank;
		chProgram[chIndex] = program;
	}

	private void scheduleNoteDynamic(danielMidiNote note, double tickDurationMs)
	{
		if (config.osrsOnly() && !OSRSMidiMapper.isOsrsInstrument(note.getInstrument()) && note.getChannel() != 9)
			return;

		if (noteScheduler.isShutdown() || noteScheduler.isTerminated())
			noteScheduler = Executors.newSingleThreadScheduledExecutor();

		double startOffset = (note.getStartTickFraction() - gameTickCounter) * tickDurationMs;
		double endOffset   = (note.getEndTickFraction()   - gameTickCounter) * tickDurationMs;

		MidiChannel channel;
		int chIndex;

		if (note.getChannel() == 9)
		{
			// Always play percussion on channel 9, and ensure a real kit is selected.
			channel = channels[9];
			chIndex = 9;
			ensurePercussionKit(note.getInstrument());
		}
		else
		{
			channel = getChannelForInstrument(note.getInstrument());
			chIndex = indexOfChannel(channel);
		}

		int baseVelocity   = note.getVelocity();
		int globalVolume   = config.midiVolume();
		int channelVolume  = getChannelVolume(chIndex);

		final int scaledVelocity = Math.max(
				0,
				Math.min(127, (int) (baseVelocity
						* (channelVolume / 100.0)
						* (globalVolume / 100.0)))
		);

		final int key = note.getKey();

		noteScheduler.schedule(() -> channel.noteOn(key, scaledVelocity),
				(long) Math.max(0, startOffset), TimeUnit.MILLISECONDS);

		noteScheduler.schedule(() -> channel.noteOff(key),
				(long) Math.max(0, endOffset), TimeUnit.MILLISECONDS);
	}



	/** Choose a valid drum kit patch present in the SF2 and lock it on channel 9. */
	private void ensurePercussionKit(int desiredKitProgram)
	{
		if (channels == null || channels.length <= 9 || channels[9] == null) return;

		// If already set, we're done
		if (percussionKitLocked && percussionBank >= 0 && percussionProgram >= 0)
			return;

		// Common banks where drum kits live in SF2s: 128 (GM), sometimes 1, sometimes 0.
		int[] banksToTry = new int[] { 128, 1, 0 };

		// Try the program the MIDI asked for on ch9, else fall back to common ones.
		int[] progsToTry;
		if (desiredKitProgram >= 0 && desiredKitProgram <= 127)
			progsToTry = new int[] { desiredKitProgram, 0 };
		else
			progsToTry = new int[] { 0 };

		for (int bank : banksToTry)
		{
			for (int prog : progsToTry)
			{
				if (patchExists(bank, prog))
				{
					setBankAndProgram(channels[9], 9, bank, prog);
					percussionBank = bank;
					percussionProgram = prog;
					percussionKitLocked = true;
					log.debug("Percussion kit set: bank={} program={}, requested={}", bank, prog, desiredKitProgram);
					return;
				}
			}
		}

		// Last resort: send only program change on ch9 (some synths ignore bank on ch9).
		channels[9].programChange(Math.max(0, desiredKitProgram));
		chProgram[9] = Math.max(0, desiredKitProgram);
		percussionBank = -1;
		percussionProgram = chProgram[9];
		percussionKitLocked = true;
		log.debug("Percussion kit fallback: program={} (bank not set or not found)", chProgram[9]);
	}

	/** Locate the index of a MidiChannel in the array (for caching). */
	private int indexOfChannel(MidiChannel ch)
	{
		if (channels == null) return -1;
		for (int i = 0; i < channels.length; i++)
			if (channels[i] == ch) return i;
		return -1;
	}

	private List<danielMidiNote> loadMidi(File midiFile, int bpm, double quantization) throws Exception
	{
		Sequence sequence = MidiSystem.getSequence(midiFile);
		List<danielMidiNote> notes = new ArrayList<>();

		int ppq = sequence.getResolution();
		double microsPerQuarter = 60000000.0 / bpm;
		double microsPerGameTick = config.tickDurationMs() * 1000;
		double midiTicksPerGameTick = (ppq * microsPerGameTick) / microsPerQuarter;

		int[] lastProgram = new int[16];
		Arrays.fill(lastProgram, 0);
		Map<Integer, danielMidiNote> activeNotes = new HashMap<>();

		for (Track track : sequence.getTracks())
		{
			for (int i = 0; i < track.size(); i++)
			{
				MidiEvent event = track.get(i);
				MidiMessage message = event.getMessage();
				long midiTick = event.getTick();

				double tickFraction = midiTick / midiTicksPerGameTick;

				if (message instanceof ShortMessage)
				{
					ShortMessage msg = (ShortMessage) message;

					if (msg.getCommand() == ShortMessage.PROGRAM_CHANGE)
					{
						lastProgram[msg.getChannel()] = msg.getData1();
						continue;
					}

					if (msg.getCommand() == ShortMessage.NOTE_ON && msg.getData2() > 0)
					{
						int key = msg.getData1();
						int velocity = msg.getData2();
						int channel = msg.getChannel();

						danielMidiNote note;
						if (channel == 9)
						{
							// Keep the KIT program seen on channel 9 so we can pick a matching drum kit.
							int kitProgram = lastProgram[9]; // 0..127 if file set it; else 0
							note = new danielMidiNote(key, velocity, channel, /*bank unused for ch9*/ 0, kitProgram);
						}
						else
						{
							int instrument = lastProgram[channel];
							note = new danielMidiNote(
									key, velocity, channel,
									OSRSMidiMapper.mapBank(instrument), instrument
							);
						}

						// Floor start to avoid skipping
						note.setStartTickFraction(Math.floor(tickFraction / quantization) * quantization);
						activeNotes.put((channel << 8) | key, note);
					}
					else if ((msg.getCommand() == ShortMessage.NOTE_OFF) ||
							(msg.getCommand() == ShortMessage.NOTE_ON && msg.getData2() == 0))
					{
						int key = (msg.getChannel() << 8) | msg.getData1();
						danielMidiNote note = activeNotes.remove(key);
						if (note != null)
						{
							// Ceil end to ensure note plays fully
							note.setEndTickFraction(Math.ceil(tickFraction / quantization) * quantization);
							notes.add(note);
						}
					}
				}
			}
		}

		return notes;
	}

	private void reQuantizeCurrentTrack()
	{
		if (notes == null) return;
		double divisor = configQuantizationValue();
		for (danielMidiNote note : notes)
		{
			note.setStartTickFraction(Math.floor(note.getStartTickFraction() / divisor) * divisor);
			note.setEndTickFraction(Math.ceil(note.getEndTickFraction() / divisor) * divisor);
		}
	}

	public void skipTrack(boolean forward)
	{
		if (trackNotes == null || trackNotes.isEmpty()) return;

		stopAllNotes();
		noteScheduler.shutdownNow();
		noteScheduler = Executors.newSingleThreadScheduledExecutor();

		if (config.shuffleTracks())
		{
			Random rand = new Random();
			int nextIndex;
			do { nextIndex = rand.nextInt(trackNotes.size()); }
			while (nextIndex == currentTrackIndex && trackNotes.size() > 1);
			currentTrackIndex = nextIndex;
		}
		else
		{
			if (forward)
				currentTrackIndex = (currentTrackIndex + 1) % trackNotes.size();
			else
				currentTrackIndex = (currentTrackIndex - 1 + trackNotes.size()) % trackNotes.size();
		}

		notes = trackNotes.get(currentTrackIndex);
		gameTickCounter = 0.0;
		log.info("Skipped to track {}: {}", currentTrackIndex, trackNames.get(currentTrackIndex));
	}

	private void stopAllNotes()
	{
		if (channels != null)
		{
			for (MidiChannel ch : channels)
				if (ch != null)
					ch.allNotesOff();
		}
		resetChannelState();
	}

	private void resetChannelState()
	{
		instrumentChannelMap.clear();
		nextAvailableChannel = 0;
		Arrays.fill(chBank, -1);
		Arrays.fill(chProgram, -1);
		percussionKitLocked = false;
		percussionBank = -1;
		percussionProgram = -1;

		if (channels != null)
		{
			for (MidiChannel ch : channels)
			{
				ch.allNotesOff();
				ch.resetAllControllers();
			}
		}
	}

	// ====== Data classes / helpers ======

	public static class danielMidiNote
	{
		private final int key, velocity, channel, bank, instrument;
		private double startTickFraction, endTickFraction;

		public danielMidiNote(int key, int velocity, int channel, int bank, int instrument)
		{
			this.key = key;
			this.velocity = velocity;
			this.channel = channel;
			this.bank = bank;
			this.instrument = instrument;
		}

		public int getKey() { return key; }
		public int getVelocity() { return velocity; }
		public int getChannel() { return channel; }
		public int getBank() { return bank; }
		public int getInstrument() { return instrument; }
		public double getStartTickFraction() { return startTickFraction; }
		public void setStartTickFraction(double val) { startTickFraction = val; }
		public double getEndTickFraction() { return endTickFraction; }
		public void setEndTickFraction(double val) { endTickFraction = val; }
	}

	public static class OSRSMidiMapper
	{
		public static final int MELODIC_BANK = 0;
		public static final int PERCUSSION_BANK = 1; // kept for legacy OSRS mapping (non-ch9 use)
		public static final int VOCAL_BANK = 2;

		private static final int[] OSRS_TO_MIDI_PROGRAM = new int[128];
		private static final int[] OSRS_TO_BANK = new int[128];

		static
		{
			for (int i = 0; i < 128; i++)
			{
				OSRS_TO_BANK[i] = MELODIC_BANK;
				OSRS_TO_MIDI_PROGRAM[i] = i;
			}

			int[] percussionIds = {0,1,8,16,24,25,40,48,50,56,127};
			for (int i : percussionIds)
			{
				OSRS_TO_BANK[i] = PERCUSSION_BANK;
				OSRS_TO_MIDI_PROGRAM[i] = i;
			}

			// Vocal bank (example mapping)
			OSRS_TO_BANK[0] = VOCAL_BANK; OSRS_TO_MIDI_PROGRAM[0] = 0;
			OSRS_TO_BANK[1] = VOCAL_BANK; OSRS_TO_MIDI_PROGRAM[1] = 1;
			OSRS_TO_BANK[2] = VOCAL_BANK; OSRS_TO_MIDI_PROGRAM[2] = 2;
			OSRS_TO_BANK[3] = VOCAL_BANK; OSRS_TO_MIDI_PROGRAM[3] = 3;
		}

		public static int mapProgram(int osrsProgram)
		{
			return (osrsProgram < 0 || osrsProgram >= OSRS_TO_MIDI_PROGRAM.length) ? 0 : OSRS_TO_MIDI_PROGRAM[osrsProgram];
		}

		public static int mapBank(int osrsProgram)
		{
			return (osrsProgram < 0 || osrsProgram >= OSRS_TO_BANK.length) ? MELODIC_BANK : OSRS_TO_BANK[osrsProgram];
		}

		public static boolean isOsrsInstrument(int osrsProgram)
		{
			return osrsProgram >= 0 && osrsProgram < OSRS_TO_MIDI_PROGRAM.length;
		}
	}

	private Set<Integer> parseTrackList(String csv)
	{
		Set<Integer> set = new HashSet<>();
		if (csv == null || csv.isEmpty()) return set;
		String[] parts = csv.split(",");
		for (String s : parts)
		{
			try { set.add(Integer.parseInt(s.trim())); }
			catch (NumberFormatException ignored) {}
		}
		return set;
	}

	// ====== Overlay getters ======

	public String getCurrentTrackName() {
		if (trackNames == null || currentTrackIndex >= trackNames.size()) return "N/A";
		return trackNames.get(currentTrackIndex);
	}

	public int getCurrentTrackIndexDisplay() { return currentTrackIndex + 1; }

	public int getTrackCount() { return trackNotes != null ? trackNotes.size() : 0; }

	public int getNotesRemaining() {
		if (notes == null) return 0;
		int remaining = 0;
		double tick = gameTickCounter;
		for (danielMidiNote note : notes)
			if (note.getEndTickFraction() > tick) remaining++;
		return remaining;
	}

	public double getGameTickCounter() { return gameTickCounter; }

	public String getSf2Status() { return sf2Status; }

}
