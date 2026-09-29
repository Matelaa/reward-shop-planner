package com.rewardshopplanner.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.Icon;

/**
 * An image shrunk to a small square next to a text, keeping its proportions. It is scaled when
 * painted, so an item icon still loading shows up by itself on the next repaint.
 */
public class SmallIcon implements Icon
{
	private BufferedImage image;
	private final int size;

	public SmallIcon(BufferedImage image, int size)
	{
		this.image = image;
		this.size = size;
	}

	/** For images that arrive later (spell sprites); the owner repaints afterwards. */
	public void setImage(BufferedImage image)
	{
		this.image = image;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
		{
			return;
		}
		double scale = Math.min((double) size / image.getWidth(), (double) size / image.getHeight());
		int w = (int) Math.round(image.getWidth() * scale);
		int h = (int) Math.round(image.getHeight() * scale);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(image, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
		g2.dispose();
	}

	@Override
	public int getIconWidth()
	{
		return size;
	}

	@Override
	public int getIconHeight()
	{
		return size;
	}
}
