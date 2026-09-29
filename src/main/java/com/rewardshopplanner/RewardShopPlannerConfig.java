package com.rewardshopplanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(RewardShopPlannerConfig.GROUP)
public interface RewardShopPlannerConfig extends Config
{
	String GROUP = "rewardshopplanner";
	/** Per-activity overlay switches are stored as this prefix plus the activity id. */
	String OVERLAY_KEY_PREFIX = "overlay_";

	enum OverlayStyle
	{
		BARS,
		ICONS,
		TEXT
	}

	@ConfigSection(
		name = "On-screen progress",
		description = "Your goal's progress on the game screen while you play",
		position = 1
	)
	String OVERLAY = "overlay";

	@ConfigSection(
		name = "On-screen progress: activities",
		description = "Where the on-screen progress shows",
		position = 2,
		closedByDefault = true
	)
	String OVERLAY_ACTIVITIES = "overlayActivities";

	@ConfigItem(
		keyName = "hideCompleted",
		name = "Hide completed pages",
		description = "Hide pages with nothing left to buy",
		position = 0
	)
	default boolean hideCompleted()
	{
		return false;
	}

	@ConfigItem(
		section = OVERLAY,
		keyName = "showOverlay",
		name = "Show on-screen progress",
		description = "Show the goal's progress for the activity you're doing. It only shows when that page has something in your goal.",
		position = 0
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY,
		keyName = "overlayStyle",
		name = "Style",
		description = "Bars: a small bar per currency. Icons: an icon per currency with the percentage, like buff timers (hover for the numbers). Text: text lines only.",
		position = 1
	)
	default OverlayStyle overlayStyle()
	{
		return OverlayStyle.BARS;
	}

	@Range(max = 120)
	@Units(Units.MINUTES)
	@ConfigItem(
		section = OVERLAY,
		keyName = "overlayHideAfter",
		name = "Hide after",
		description = "For activities without a fixed place (Forestry, Shooting Stars...): the progress shows when you earn the currency"
			+ " and hides after this long without earning more. 0 keeps it until you log out.",
		position = 2
	)
	default int overlayHideAfter()
	{
		return 10;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "barbarian_assault",
		name = "Barbarian Assault",
		description = "Show Barbarian Assault progress while you're there",
		position = 0
	)
	default boolean overlayBarbarianAssault()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "brimhaven_agility",
		name = "Brimhaven Agility Arena",
		description = "Show Brimhaven Agility Arena progress while you're there",
		position = 1
	)
	default boolean overlayBrimhavenAgility()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "castle_wars",
		name = "Castle Wars",
		description = "Show Castle Wars progress while you're there",
		position = 2
	)
	default boolean overlayCastleWars()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "fishing_trawler",
		name = "Fishing Trawler",
		description = "Show Fishing Trawler progress while you're there",
		position = 3
	)
	default boolean overlayFishingTrawler()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "giants_foundry",
		name = "Giants' Foundry",
		description = "Show Giants' Foundry progress while you're there",
		position = 4
	)
	default boolean overlayGiantsFoundry()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "gotr",
		name = "Guardians of the Rift",
		description = "Show Guardians of the Rift progress while you're there",
		position = 5
	)
	default boolean overlayGotr()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "hallowed_sepulchre",
		name = "Hallowed Sepulchre",
		description = "Show Hallowed Sepulchre progress while you're there",
		position = 6
	)
	default boolean overlayHallowedSepulchre()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "lms",
		name = "Last Man Standing",
		description = "Show Last Man Standing progress while you're there",
		position = 7
	)
	default boolean overlayLms()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "mta",
		name = "Mage Training Arena",
		description = "Show Mage Training Arena progress while you're there",
		position = 8
	)
	default boolean overlayMta()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "mahogany_homes",
		name = "Mahogany Homes",
		description = "Show Mahogany Homes progress after you earn its currency",
		position = 9
	)
	default boolean overlayMahoganyHomes()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "mixology",
		name = "Mastering Mixology",
		description = "Show Mastering Mixology progress while you're there",
		position = 10
	)
	default boolean overlayMixology()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "pest_control",
		name = "Pest Control",
		description = "Show Pest Control progress while you're there",
		position = 11
	)
	default boolean overlayPestControl()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "soul_wars",
		name = "Soul Wars",
		description = "Show Soul Wars progress while you're there",
		position = 12
	)
	default boolean overlaySoulWars()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "temple_trekking",
		name = "Temple Trekking",
		description = "Show Temple Trekking progress while you're there",
		position = 13
	)
	default boolean overlayTempleTrekking()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "tithe_farm",
		name = "Tithe Farm",
		description = "Show Tithe Farm progress while you're there",
		position = 14
	)
	default boolean overlayTitheFarm()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "trouble_brewing",
		name = "Trouble Brewing",
		description = "Show Trouble Brewing progress while you're there",
		position = 15
	)
	default boolean overlayTroubleBrewing()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "vale_totems",
		name = "Vale Totems",
		description = "Show Vale Totems progress while you're there",
		position = 16
	)
	default boolean overlayValeTotems()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "volcanic_mine",
		name = "Volcanic Mine",
		description = "Show Volcanic Mine progress while you're there",
		position = 17
	)
	default boolean overlayVolcanicMine()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "tempoross",
		name = "Tempoross",
		description = "Show Tempoross progress while you're there",
		position = 18
	)
	default boolean overlayTempoross()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "aerial_fishing",
		name = "Aerial Fishing",
		description = "Show Aerial Fishing progress while you're there",
		position = 19
	)
	default boolean overlayAerialFishing()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "colossal_wyrm",
		name = "Colossal Wyrm Agility",
		description = "Show Colossal Wyrm Agility progress while you're there",
		position = 20
	)
	default boolean overlayColossalWyrm()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "forestry",
		name = "Forestry",
		description = "Show Forestry progress after you earn its currency",
		position = 21
	)
	default boolean overlayForestry()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "motherlode_mine",
		name = "Motherlode Mine",
		description = "Show Motherlode Mine progress while you're there",
		position = 22
	)
	default boolean overlayMotherlodeMine()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "rooftop_agility",
		name = "Rooftop Agility",
		description = "Show Rooftop Agility progress while you're there",
		position = 23
	)
	default boolean overlayRooftopAgility()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "shooting_stars",
		name = "Shooting Stars",
		description = "Show Shooting Stars progress after you earn its currency",
		position = 24
	)
	default boolean overlayShootingStars()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "tzhaar",
		name = "TzHaar",
		description = "Show TzHaar progress while you're there",
		position = 25
	)
	default boolean overlayTzhaar()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "miscellaneous",
		name = "Miscellaneous",
		description = "Show Miscellaneous progress after you earn its currency",
		position = 26
	)
	default boolean overlayMiscellaneous()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "chompy",
		name = "Chompy Bird Hunting",
		description = "Show Chompy Bird Hunting progress after you earn its currency",
		position = 27
	)
	default boolean overlayChompy()
	{
		return true;
	}
}