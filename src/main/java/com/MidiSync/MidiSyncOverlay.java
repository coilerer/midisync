package com.MidiSync;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;

public class MidiSyncOverlay extends Overlay
{
    private final Client client;
    private final MidiSyncPlugin plugin;
    private final MidiSyncConfig config;

    @Inject
    public MidiSyncOverlay(Client client, MidiSyncPlugin plugin, MidiSyncConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(0);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if(config.MidiSyncDevOverlay()) {
            int x = 10;
            int y = 10;
            int lineHeight = 18;

            // Track info
            OverlayUtil.renderTextLocation(g, new Point(x, y), "Track: " + plugin.getCurrentTrackName(), Color.CYAN);
            y += lineHeight;

            OverlayUtil.renderTextLocation(g, new Point(x, y),
                    "Track Index: " + plugin.getCurrentTrackIndexDisplay() + "/" + plugin.getTrackCount(),
                    Color.CYAN);
            y += lineHeight;

            // Notes remaining
            OverlayUtil.renderTextLocation(g, new Point(x, y), "Notes Remaining: " + plugin.getNotesRemaining(), Color.CYAN);
            y += lineHeight;

            // Game tick
            OverlayUtil.renderTextLocation(g, new Point(x, y), "Game Tick: " + plugin.getGameTickCounter(), Color.CYAN);
            y += lineHeight;

            // SoundFont status
            OverlayUtil.renderTextLocation(g, new Point(x, y), "SF2 Status: " + plugin.getSf2Status(), Color.ORANGE);
            y += lineHeight;

            // Quantization info
            OverlayUtil.renderTextLocation(g, new Point(x, y),
                    "Quantization Divisor: 1/" + plugin.configQuantizationValue(),
                    Color.MAGENTA);
            y += lineHeight;
        }

        return null;
    }
}