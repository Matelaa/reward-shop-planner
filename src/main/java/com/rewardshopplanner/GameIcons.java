package com.rewardshopplanner;

import com.rewardshopplanner.ui.ItemIcons;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Icons from the game cache: items through RuneLite's ItemManager, spell icons through its
 * SpriteManager. Nothing is downloaded.
 */
class GameIcons implements ItemIcons
{
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final Map<Integer, BufferedImage> sprites = new ConcurrentHashMap<>();
	/** Sprites already asked for, so each is only requested once. */
	private final Map<Integer, Boolean> requested = new ConcurrentHashMap<>();

	GameIcons(ItemManager itemManager, SpriteManager spriteManager)
	{
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
	}

	@Override
	public BufferedImage get(int itemId, Runnable onLoaded)
	{
		AsyncBufferedImage image = itemManager.getImage(itemId);
		image.onLoaded(onLoaded);
		return image;
	}

	@Override
	public BufferedImage sprite(int spriteId, Runnable onLoaded)
	{
		BufferedImage loaded = sprites.get(spriteId);
		if (loaded == null && requested.putIfAbsent(spriteId, true) == null)
		{
			spriteManager.getSpriteAsync(spriteId, 0, image ->
			{
				sprites.put(spriteId, image);
				onLoaded.run();
			});
		}
		return loaded;
	}
}
