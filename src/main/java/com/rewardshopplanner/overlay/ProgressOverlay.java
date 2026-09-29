package com.rewardshopplanner.overlay;

import com.rewardshopplanner.RewardShopPlannerConfig;
import com.rewardshopplanner.data.Currency;
import com.rewardshopplanner.data.RewardData;
import com.rewardshopplanner.ui.ItemIcons;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * The goal's progress on the game screen for the activity being played: the page name with
 * "N left · M ready", then each currency as "have / need", with a slim bar in the Bars style.
 */
public class ProgressOverlay extends OverlayPanel
{
	private static final int WIDTH = 200;
	private static final Color MUTED = new Color(0xb0b0b0);
	private static final Color GOOD = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;

	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 4;
	private static final Runnable NOTHING = () -> { };

	private final RewardShopPlannerConfig config;
	private final ItemIcons icons;
	private final RewardData data;
	private final Supplier<List<PageProgress>> pages;

	public ProgressOverlay(Plugin plugin, RewardShopPlannerConfig config, ItemIcons icons, RewardData data, Supplier<List<PageProgress>> pages)
	{
		super(plugin);
		this.config = config;
		this.icons = icons;
		this.data = data;
		this.pages = pages;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || config.overlayStyle() == RewardShopPlannerConfig.OverlayStyle.ICONS)
		{
			return null;
		}
		List<PageProgress> shown = pages.get();
		if (shown.isEmpty())
		{
			return null;
		}
		boolean bars = config.overlayStyle() == RewardShopPlannerConfig.OverlayStyle.BARS;
		boolean first = true;
		for (PageProgress page : shown)
		{
			if (!first)
			{
				panelComponent.getChildren().add(LineComponent.builder().build());
			}
			first = false;
			String name = page.getActivity().getName();
			String summary = summary(page);
			Color summaryColor = page.getReady() > 0 ? GOOD : MUTED;
			// long names (Mage Training Arena) push the summary onto its own line instead of wrapping both
			int needed = graphics.getFontMetrics(FontManager.getRunescapeBoldFont()).stringWidth(name)
				+ graphics.getFontMetrics(FontManager.getRunescapeSmallFont()).stringWidth(summary) + 12;
			boolean fits = needed <= WIDTH - 8;
			panelComponent.getChildren().add(LineComponent.builder()
				.left(name)
				.leftFont(FontManager.getRunescapeBoldFont())
				.right(fits ? summary : "")
				.rightColor(summaryColor)
				.build());
			if (!fits)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.right(summary)
					.rightColor(summaryColor)
					.build());
			}
			for (PageProgress.Bar bar : page.getBars())
			{
				BufferedImage icon = smallIcon(bar.getCurrencyId());
				int room = WIDTH - 8 - (icon == null ? 0 : ICON_SIZE + ICON_GAP);
				// full numbers when they fit next to the name, else 4,666 / 75K so the line doesn't wrap
				String amounts = amounts(bar, false);
				int width = graphics.getFontMetrics(FontManager.getRunescapeSmallFont()).stringWidth(bar.getName() + amounts) + 12;
				if (width > room)
				{
					amounts = amounts(bar, true);
				}
				LayoutableRenderableEntity row = LineComponent.builder()
					.left(bar.getName())
					.leftColor(MUTED)
					.right(amounts)
					.rightColor(bar.isDone() ? GOOD : Color.WHITE)
					.build();
				if (bars)
				{
					ThinBarComponent thin = new ThinBarComponent();
					thin.setFraction(bar.getHave() == null ? 0 : (double) bar.getHave() / bar.getNeed());
					thin.setColor(bar.isDone() ? GOOD : ACCENT);
					row = SplitComponent.builder().first(row).second(thin)
						.orientation(ComponentOrientation.VERTICAL).gap(new Point(0, 1)).build();
				}
				if (icon != null)
				{
					// the currency's icon on the left, like the resin bars of the Mastering Mixology plugin
					row = SplitComponent.builder().first(new ImageComponent(icon)).second(row)
						.orientation(ComponentOrientation.HORIZONTAL).gap(new Point(ICON_GAP, 0)).build();
				}
				panelComponent.getChildren().add(row);
			}
		}
		return super.render(graphics);
	}

	/** The currency's icon shrunk to fit a line; null when it has none (yet: a sprite may be loading). */
	private BufferedImage smallIcon(String currencyId)
	{
		Currency currency = data.getCurrencies().get(currencyId);
		BufferedImage image = currency == null ? null : icons.currency(currency, NOTHING);
		if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
		{
			return null;
		}
		// centred on a fixed square, so every currency's text starts at the same place
		BufferedImage scaled = ImageUtil.resizeImage(image, ICON_SIZE, ICON_SIZE, true);
		BufferedImage square = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = square.createGraphics();
		g.drawImage(scaled, (ICON_SIZE - scaled.getWidth()) / 2, (ICON_SIZE - scaled.getHeight()) / 2, null);
		g.dispose();
		return square;
	}

	static String amounts(PageProgress.Bar bar, boolean compact)
	{
		return (bar.getHave() == null ? "?" : format(bar.getHave(), compact)) + " / " + format(bar.getNeed(), compact);
	}

	/** 4,666 stays whole; compact shortens from 10,000 up: 75K, 12.4K, 1.25M. */
	static String format(long value, boolean compact)
	{
		if (!compact || value < 10_000)
		{
			return QuantityFormatter.formatNumber(value);
		}
		if (value < 1_000_000)
		{
			return trim(String.format(Locale.US, "%.1f", value / 1_000.0)) + "K";
		}
		return trim(String.format(Locale.US, "%.2f", value / 1_000_000.0)) + "M";
	}

	private static String trim(String number)
	{
		return number.contains(".") ? number.replaceAll("0+$", "").replaceAll("\\.$", "") : number;
	}

	/** "2 left · 1 ready", or nothing when only an extra goal keeps the page on screen. */
	static String summary(PageProgress page)
	{
		if (page.getLeft() == 0)
		{
			return "";
		}
		return page.getLeft() + " left · " + page.getReady() + " ready";
	}
}
