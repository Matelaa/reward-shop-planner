package com.rewardshopplanner.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * A slim progress bar for overlays: RuneLite's own bar is at least 16px tall with the text
 * inside, while this one sits under a line of text.
 */
class ThinBarComponent implements LayoutableRenderableEntity
{
	private static final Color BACKGROUND = new Color(255, 255, 255, 40);
	private static final int HEIGHT = 4;

	@Setter
	private double fraction;
	@Setter
	private Color color = Color.ORANGE;
	@Setter
	private Point preferredLocation = new Point();
	private int width = 129;
	@Getter
	private final Rectangle bounds = new Rectangle();

	/** The panel passes its width with a height of 0; the bar keeps its own height. */
	@Override
	public void setPreferredSize(Dimension size)
	{
		width = size.width;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int height = HEIGHT;
		int fill = (int) Math.round(width * Math.max(0, Math.min(1, fraction)));
		graphics.setColor(BACKGROUND);
		graphics.fillRect(preferredLocation.x + fill, preferredLocation.y, width - fill, height);
		graphics.setColor(color);
		graphics.fillRect(preferredLocation.x, preferredLocation.y, fill, height);
		bounds.setLocation(preferredLocation);
		bounds.setSize(width, height);
		return new Dimension(width, height);
	}
}
