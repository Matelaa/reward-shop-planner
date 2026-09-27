package com.rewardshopplanner.ui;

import javax.swing.JComponent;
import javax.swing.JToolTip;
import net.runelite.client.ui.FontManager;

/**
 * Tooltips in the panel's RuneScape font instead of the look and feel's default one. HTML
 * tooltips take the tooltip's font as their base font, so this covers them too.
 */
final class Tooltips
{
	private Tooltips()
	{
	}

	static JToolTip create(JComponent owner)
	{
		JToolTip tip = new JToolTip();
		tip.setComponent(owner);
		tip.setFont(FontManager.getRunescapeSmallFont());
		return tip;
	}
}
