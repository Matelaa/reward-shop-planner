package com.rewardshopplanner.overlay;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.util.QuantityFormatter;

/**
 * The Icons style: one info box per currency, like buff timers, with the percentage under the icon.
 */
public class ProgressInfoBox extends InfoBox
{
	@Getter
	private final String key;
	@Setter
	private PageProgress page;
	@Setter
	private PageProgress.Bar bar;
	/** Asked each time the box is drawn: a spell sprite may only arrive after the box was added. */
	private final Supplier<BufferedImage> icon;

	public ProgressInfoBox(Supplier<BufferedImage> icon, Plugin plugin, String key)
	{
		super(null, plugin);
		this.icon = icon;
		this.key = key;
	}

	@Override
	public BufferedImage getImage()
	{
		return icon.get();
	}

	/** "have/need" doesn't fit under an icon, so the box shows the percentage; the tooltip has the numbers. */
	@Override
	public String getText()
	{
		if (bar.getHave() == null)
		{
			return "?";
		}
		return Math.min(100, (int) Math.floor(100.0 * bar.getHave() / bar.getNeed())) + "%";
	}

	@Override
	public Color getTextColor()
	{
		return bar.isDone() ? ColorScheme.PROGRESS_COMPLETE_COLOR : Color.WHITE;
	}

	@Override
	public String getTooltip()
	{
		String have = bar.getHave() == null ? "unknown" : QuantityFormatter.formatNumber(bar.getHave());
		String summary = ProgressOverlay.summary(page);
		return page.getActivity().getName() + (summary.isEmpty() ? "" : " (" + summary + ")")
			+ "</br>" + bar.getName() + ": " + have + " / " + QuantityFormatter.formatNumber(bar.getNeed());
	}
}
