package com.rewardshopplanner.overlay;

import com.rewardshopplanner.RewardShopPlannerConfig;
import com.rewardshopplanner.data.Activity;
import com.rewardshopplanner.data.Currency;
import com.rewardshopplanner.data.Reward;
import com.rewardshopplanner.data.RewardData;
import com.rewardshopplanner.ui.ItemIcons;
import com.rewardshopplanner.ui.PanelModel;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

/**
 * Decides which pages the on-screen progress shows: the activity you're standing in, or, for
 * activities without a fixed place, the ones whose currency you earned recently.
 */
@Slf4j
public class ProgressTracker
{

	private final Plugin plugin;
	private final RewardShopPlannerConfig config;
	private final ConfigManager configManager;
	private final ItemIcons icons;
	private static final Runnable NOTHING = () -> { };
	private final InfoBoxManager infoBoxManager;
	private final RewardData data;
	/** Each activity's currencies plus the counters its milestones use (chompy kills, LMS wins). */
	private final Map<String, Set<String>> activityCurrencies = new HashMap<>();

	private volatile PanelModel model;
	/**
	 * Activities the player is at, with the currencies to show there (empty for all of them);
	 * replaced as a whole each tick.
	 */
	private volatile Map<String, Set<String>> here = Collections.emptyMap();
	/** When each currency was last earned, in millis. */
	private final Map<String, Long> lastEarned = new ConcurrentHashMap<>();
	/** Item currencies in the inventory, and in the bank and Forestry kit, at the end of the last tick. */
	private Map<String, Long> lastInventory;
	private Map<String, Long> lastStored;
	/** Item currencies picked up from the ground this tick. */
	private final Map<String, Long> pickups = new HashMap<>();
	/** Non-item currencies already read from the game this session. */
	private final Set<String> readThisSession = new HashSet<>();
	/** The region last logged, so only changes are logged. */
	private int loggedRegion = -1;
	/** Icons-style boxes by "activity:currency"; client thread only. */
	private final Map<String, ProgressInfoBox> infoBoxes = new HashMap<>();

	public ProgressTracker(Plugin plugin, RewardShopPlannerConfig config, ConfigManager configManager, ItemIcons icons,
		InfoBoxManager infoBoxManager, RewardData data)
	{
		this.plugin = plugin;
		this.config = config;
		this.configManager = configManager;
		this.icons = icons;
		this.infoBoxManager = infoBoxManager;
		this.data = data;
		for (Activity activity : data.getActivities().values())
		{
			Set<String> currencies = new LinkedHashSet<>();
			if (activity.getCurrencies() != null)
			{
				currencies.addAll(activity.getCurrencies());
			}
			for (String item : activity.getClogItems())
			{
				Reward reward = data.getReward(item);
				if (reward != null && reward.getMilestone() != null && reward.getMilestone().getCounter() != null)
				{
					currencies.add(reward.getMilestone().getCounter());
				}
			}
			activityCurrencies.put(activity.getId(), currencies);
		}
	}

	/** The latest plan and balances. */
	public void update(PanelModel model)
	{
		this.model = model;
	}


	public void onLogout()
	{
		startSession();
		lastEarned.clear();
		here = Collections.emptyMap();
	}

	private void startSession()
	{
		lastInventory = null;
		lastStored = null;
		readThisSession.clear();
		pickups.clear();
	}

	/**
	 * A currency the game reports as a number (points in a var or on a shop screen, a kill count).
	 * The first reading of a session only sets the baseline: it may catch up on play elsewhere.
	 */
	public void onGameValue(String currency, Long before, long now)
	{
		boolean first = readThisSession.add(currency);
		if (!first && before != null && now > before)
		{
			earned(currency);
		}
	}

	/** Whether the currency was earned since the session started; for tests. */
	boolean wasEarned(String currency)
	{
		return lastEarned.containsKey(currency);
	}

	/** Something that is always a real gain, like a chompy kill message. */
	public void earned(String currency)
	{
		lastEarned.put(currency, System.currentTimeMillis());
	}

	/** An item currency picked up from the ground next to the player (the player's own drop). */
	public void onPickup(String currency, long quantity)
	{
		pickups.merge(currency, quantity, Long::sum);
	}

	/**
	 * Item currencies count as earned only when the inventory gains them, minus what left the bank
	 * or Forestry kit in the same tick (a withdrawal) and what was picked up from the ground. Reading
	 * the bank or kit never counts: it only shows what was already there.
	 */
	public void onItemCounts(Map<String, Long> inventory, Map<String, Long> stored)
	{
		if (lastInventory != null)
		{
			for (Map.Entry<String, Long> entry : inventory.entrySet())
			{
				String currency = entry.getKey();
				Long before = lastInventory.get(currency);
				if (before == null)
				{
					continue;
				}
				long gained = entry.getValue() - before;
				long withdrawn = Math.max(0, lastStored.getOrDefault(currency, 0L) - stored.getOrDefault(currency, 0L));
				if (gained - withdrawn - pickups.getOrDefault(currency, 0L) > 0)
				{
					earned(currency);
				}
			}
		}
		if (!inventory.isEmpty())
		{
			lastInventory = new HashMap<>(inventory);
			lastStored = new HashMap<>(stored);
		}
		pickups.clear();
	}

	/** Reads where the player is (instances map back to their real region) and refreshes the icons. */
	public void onGameTick(Client client)
	{
		Player player = client.getLocalPlayer();
		Map<String, Set<String>> at = new HashMap<>();
		if (player != null)
		{
			// like RuneLite's Discord plugin: instanced areas map back to their template region
			WorldPoint point = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
			if (point != null)
			{
				for (Activity activity : data.getActivities().values())
				{
					Set<String> currencies = activity.currenciesAt(point.getRegionID(), point.getX(), point.getY(), point.getPlane());
					if (currencies != null)
					{
						at.put(activity.getId(), currencies);
					}
				}
				if (point.getRegionID() != loggedRegion)
				{
					// lets a player report the region of a spot where the progress should show but doesn't
					loggedRegion = point.getRegionID();
					log.debug("Reward Shop Planner: region {} at {}, activities {}", loggedRegion, point, at.keySet());
				}
			}
		}
		here = at;
		updateInfoBoxes();
	}

	/** The pages to show right now, in the data's order. */
	public List<PageProgress> shown()
	{
		PanelModel current = model;
		List<PageProgress> pages = new ArrayList<>();
		if (current == null || !config.showOverlay())
		{
			return pages;
		}
		for (Activity activity : data.getActivities().values())
		{
			RewardShopPlannerConfig.OverlayMode mode = mode(activity);
			if (mode == RewardShopPlannerConfig.OverlayMode.OFF)
			{
				continue;
			}
			Set<String> currencies = here.get(activity.getId());
			if (mode == RewardShopPlannerConfig.OverlayMode.ALWAYS
				|| (currencies == null && !activity.hasPlace() && earnedRecently(activity)))
			{
				// always on screen, or earned recently somewhere: every currency of the page
				currencies = Collections.emptySet();
			}
			if (currencies == null)
			{
				continue;
			}
			PageProgress page = PageProgress.of(current, activity, currencies);
			if (page != null && !page.getBars().isEmpty())
			{
				pages.add(page);
			}
		}
		return pages;
	}

	public void shutDown()
	{
		infoBoxManager.removeIf(box -> box instanceof ProgressInfoBox);
		infoBoxes.clear();
		lastEarned.clear();
	}

	private RewardShopPlannerConfig.OverlayMode mode(Activity activity)
	{
		return modeOf(configManager.getConfiguration(RewardShopPlannerConfig.GROUP,
			RewardShopPlannerConfig.OVERLAY_KEY_PREFIX + activity.getId()));
	}

	/**
	 * The activity's setting as saved: an OverlayMode name, nothing (the default), or "true"/"false"
	 * from before it was a choice of three.
	 */
	static RewardShopPlannerConfig.OverlayMode modeOf(String saved)
	{
		if (saved == null || saved.equals("true"))
		{
			return RewardShopPlannerConfig.OverlayMode.AT_THE_ACTIVITY;
		}
		if (saved.equals("false"))
		{
			return RewardShopPlannerConfig.OverlayMode.OFF;
		}
		try
		{
			return RewardShopPlannerConfig.OverlayMode.valueOf(saved);
		}
		catch (IllegalArgumentException e)
		{
			return RewardShopPlannerConfig.OverlayMode.AT_THE_ACTIVITY;
		}
	}

	private boolean earnedRecently(Activity activity)
	{
		long window = config.overlayHideAfter() * 60_000L;
		long now = System.currentTimeMillis();
		for (String currency : activityCurrencies.getOrDefault(activity.getId(), Collections.emptySet()))
		{
			Long when = lastEarned.get(currency);
			if (when != null && (window == 0 || now - when < window))
			{
				return true;
			}
		}
		return false;
	}

	/** Keeps one info box per shown currency in the Icons style, and none otherwise. */
	private void updateInfoBoxes()
	{
		Map<String, PageProgress> wanted = new HashMap<>();
		Map<String, PageProgress.Bar> bars = new HashMap<>();
		if (config.overlayStyle() == RewardShopPlannerConfig.OverlayStyle.ICONS)
		{
			for (PageProgress page : shown())
			{
				for (PageProgress.Bar bar : page.getBars())
				{
					String key = page.getActivity().getId() + ":" + bar.getCurrencyId();
					wanted.put(key, page);
					bars.put(key, bar);
				}
			}
		}
		infoBoxes.entrySet().removeIf(entry ->
		{
			if (wanted.containsKey(entry.getKey()))
			{
				return false;
			}
			infoBoxManager.removeInfoBox(entry.getValue());
			return true;
		});
		for (Map.Entry<String, PageProgress> entry : wanted.entrySet())
		{
			ProgressInfoBox box = infoBoxes.get(entry.getKey());
			if (box == null)
			{
				PageProgress page = entry.getValue();
				Currency currency = data.getCurrencies().get(bars.get(entry.getKey()).getCurrencyId());
				box = new ProgressInfoBox(() -> iconFor(page, currency), plugin, entry.getKey());
				infoBoxes.put(entry.getKey(), box);
				box.setPage(entry.getValue());
				box.setBar(bars.get(entry.getKey()));
				infoBoxManager.addInfoBox(box);
			}
			box.setPage(entry.getValue());
			box.setBar(bars.get(entry.getKey()));
		}
	}

	/** The currency's icon (its item, stand-in item or spell sprite), else the first item sold on the page. */
	private BufferedImage iconFor(PageProgress page, Currency currency)
	{
		if (currency != null && (currency.getIconItem() != null || currency.getIconSpriteId() != null))
		{
			// a sprite may still be loading: the box asks again next frame
			return icons.currency(currency, NOTHING);
		}
		for (String item : page.getActivity().getClogItems())
		{
			Reward reward = data.getReward(item);
			if (reward != null && reward.getItemId() != null)
			{
				return icons.get(reward.getItemId(), NOTHING);
			}
		}
		return null;
	}
}
