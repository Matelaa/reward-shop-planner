package com.rewardshopplanner.ui;

import com.rewardshopplanner.data.Currency;
import java.awt.image.BufferedImage;

/**
 * Supplies item sprites to the panel. Images may load asynchronously; {@code onLoaded} is run
 * once the image is ready so the slot can repaint.
 */
public interface ItemIcons
{
	/** The sprite for an item, or null when there is none. */
	BufferedImage get(int itemId, Runnable onLoaded);

	/** A game sprite (a spell icon), or null until it has loaded. */
	default BufferedImage sprite(int spriteId, Runnable onLoaded)
	{
		return null;
	}

	/** The icon standing for a currency: its item, its stand-in item or its sprite; null if none. */
	default BufferedImage currency(Currency currency, Runnable onLoaded)
	{
		if (currency.getIconItem() != null)
		{
			return get(currency.getIconItem(), onLoaded);
		}
		return currency.getIconSpriteId() == null ? null : sprite(currency.getIconSpriteId(), onLoaded);
	}
}
