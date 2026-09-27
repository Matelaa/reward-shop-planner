package com.rewardshopplanner.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;

/**
 * A thin rounded progress bar.
 */
class ProgressBar extends JComponent
{
	private final double fraction;
	private final Color color;

	ProgressBar(double fraction, Color color, int height)
	{
		this.fraction = Math.max(0, Math.min(1, fraction));
		this.color = color;
		setPreferredSize(new Dimension(10, height));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		setMinimumSize(new Dimension(10, height));
		setAlignmentX(LEFT_ALIGNMENT);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		g.setColor(ColorScheme.DARKER_GRAY_COLOR.darker());
		g.fillRoundRect(0, 0, w, h, h, h);
		int filled = (int) Math.round(w * fraction);
		if (filled > 0)
		{
			g.setColor(color);
			g.fillRoundRect(0, 0, Math.max(filled, h), h, h, h);
		}
		g.dispose();
	}
}
