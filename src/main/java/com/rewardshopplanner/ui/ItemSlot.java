package com.rewardshopplanner.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One collection log slot in the item grid, drawn like the in-game log: full colour when owned,
 * faded when missing, with a gold frame when it is part of the goal.
 */
class ItemSlot extends JComponent
{
	static final int WIDTH = 38;
	static final int HEIGHT = 36;
	private static final Color GOAL = ColorScheme.BRAND_ORANGE;
	private static final Color HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;

	private final BufferedImage icon;
	private final String fallbackText;
	private final boolean owned;
	private final boolean wanted;
	private final boolean needsChoice;
	private boolean hover;

	ItemSlot(BufferedImage icon, String name, boolean owned, boolean wanted, boolean needsChoice)
	{
		this.icon = icon;
		this.fallbackText = initials(name);
		this.owned = owned;
		this.wanted = wanted;
		this.needsChoice = needsChoice;
		setPreferredSize(new Dimension(WIDTH, HEIGHT));
		setCursor(Cursor.getPredefinedCursor(owned ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
		addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				hover = false;
				repaint();
			}
		});
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();

		g.setColor(hover ? HOVER : ColorScheme.DARKER_GRAY_COLOR);
		g.fillRoundRect(1, 1, w - 2, h - 2, 6, 6);

		if (!owned)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, wanted ? 0.55f : 0.3f));
		}
		if (icon != null)
		{
			g.drawImage(icon, (w - icon.getWidth()) / 2, (h - icon.getHeight()) / 2, null);
		}
		else
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			g.setColor(Color.WHITE);
			FontMetrics fm = g.getFontMetrics();
			g.drawString(fallbackText, (w - fm.stringWidth(fallbackText)) / 2, (h + fm.getAscent()) / 2 - 2);
		}
		g.setComposite(AlphaComposite.SrcOver);

		if (wanted && !owned)
		{
			g.setColor(GOAL);
			g.setStroke(new BasicStroke(2f));
			g.drawRoundRect(2, 2, w - 4, h - 4, 6, 6);
		}
		if (needsChoice)
		{
			g.setColor(GOAL);
			g.fillOval(w - 12, 3, 9, 9);
			g.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g.setFont(FontManager.getRunescapeSmallFont());
			g.drawString("?", w - 10, 11);
		}
		g.dispose();
	}

	private static String initials(String name)
	{
		StringBuilder out = new StringBuilder();
		for (String word : name.split("[^A-Za-z]+"))
		{
			if (!word.isEmpty() && out.length() < 2)
			{
				out.append(Character.toUpperCase(word.charAt(0)));
			}
		}
		return out.toString();
	}
}
