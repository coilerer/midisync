package com.midisync;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class midisynctest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(midisyncplugin.class);
		RuneLite.main(args);
	}
}