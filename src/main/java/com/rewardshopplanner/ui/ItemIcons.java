package com.rewardshopplanner.ui;

import java.awt.image.BufferedImage;

/**
 * Supplies item sprites to the panel. Images may load asynchronously; {@code onLoaded} is run
 * once the image is ready so the slot can repaint.
 */
public interface ItemIcons
{
	/** The sprite for an item, or null when there is none. */
	BufferedImage get(int itemId, Runnable onLoaded);
}
