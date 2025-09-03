package com.midisync;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class midisyncoverlay extends Overlay
{
    private final Client client;
    private final midisyncplugin plugin;
    private final midisyncconfig config;
    private final Font osrsFont;

    @Inject
    public midisyncoverlay(Client client, midisyncplugin plugin, midisyncconfig config, FontManager fontManager)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.osrsFont = fontManager.getRunescapeSmallFont();

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (!config.midisyncoverlay()) return null;

        int x = 10;
        int y = 10;
        int width = 220; // fixed overlay width
        int padding = 6;
        int lineHeight = g.getFontMetrics(osrsFont).getHeight();

        g.setFont(osrsFont);

        // Wrap all dynamic text fields
        java.util.List<String> trackLines = wrapText(plugin.getCurrentTrackName(), width - 2 * padding, g);
        java.util.List<String> sf2Lines = wrapText(plugin.getSf2Status(), width - 2 * padding, g);
        java.util.List<String> quantLines = wrapText("Quant: 1/" + plugin.configQuantizationValue(), width - 2 * padding, g);

        int textY = y + padding;

        // Calculate total lines for dynamic height
        int totalLines = trackLines.size() + sf2Lines.size() + quantLines.size() + 3;
        // 3 for index, notes remaining, game tick
        int height = totalLines * lineHeight + 2 * padding;

        // Draw semi-transparent background
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(x, y, width, height);

        g.setColor(Color.CYAN);
        for (String line : trackLines)
        {
            textY += lineHeight;
            g.drawString(line, x + padding, textY);
        }

        // Track index
        textY += lineHeight;
        g.drawString("Index: " + plugin.getCurrentTrackIndexDisplay() + "/" + plugin.getTrackCount(), x + padding, textY);

        // Notes remaining
        textY += lineHeight;
        g.drawString("Notes Remaining: " + plugin.getNotesRemaining(), x + padding, textY);

        // Game tick
        textY += lineHeight;
        g.drawString("Game Tick: " + (int) plugin.getGameTickCounter(), x + padding, textY);

        // SF2 status
        g.setColor(Color.ORANGE);
        for (String line : sf2Lines)
        {
            textY += lineHeight;
            g.drawString(line, x + padding, textY);
        }

        // Quantization info
        g.setColor(Color.MAGENTA);
        for (String line : quantLines)
        {
            textY += lineHeight;
            g.drawString(line, x + padding, textY);
        }

        return new Dimension(width, height);
    }

    /**
     * Wraps text into multiple lines to fit within maxWidth
     */
    private java.util.List<String> wrapText(String text, int maxWidth, Graphics2D g)
    {
        List<String> lines = new ArrayList<>();
        FontMetrics fm = g.getFontMetrics();
        if (text == null || text.isEmpty())
        {
            lines.add("");
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words)
        {
            String testLine = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(testLine) > maxWidth)
            {
                if (line.length() > 0)
                {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                }
                else
                {
                    // single word too long, force split
                    lines.add(word);
                    line = new StringBuilder();
                }
            }
            else
            {
                line = new StringBuilder(testLine);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}