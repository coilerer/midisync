package com.MidiSync;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MidiSyncTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MidiSyncPlugin.class);
		RuneLite.main(args);
	}
}