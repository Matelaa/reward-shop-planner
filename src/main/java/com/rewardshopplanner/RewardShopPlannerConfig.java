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
	/** Per-activity overlay modes are stored as this prefix plus the activity id. */
	String OVERLAY_KEY_PREFIX = "overlay_";

	enum OverlayStyle
	{
		BARS,
		ICONS,
		TEXT
	}

	/** When an activity's progress shows on screen. */
	enum OverlayMode
	{
		AT_THE_ACTIVITY("At the activity"),
		ALWAYS("Always"),
		OFF("Off");

		private final String label;

		OverlayMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
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
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 0
	)
	default OverlayMode overlayBarbarianAssault()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "brimhaven_agility",
		name = "Brimhaven Agility Arena",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 1
	)
	default OverlayMode overlayBrimhavenAgility()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "castle_wars",
		name = "Castle Wars",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 2
	)
	default OverlayMode overlayCastleWars()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "fishing_trawler",
		name = "Fishing Trawler",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 3
	)
	default OverlayMode overlayFishingTrawler()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "giants_foundry",
		name = "Giants' Foundry",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 4
	)
	default OverlayMode overlayGiantsFoundry()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "gotr",
		name = "Guardians of the Rift",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 5
	)
	default OverlayMode overlayGotr()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "hallowed_sepulchre",
		name = "Hallowed Sepulchre",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 6
	)
	default OverlayMode overlayHallowedSepulchre()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "lms",
		name = "Last Man Standing",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 7
	)
	default OverlayMode overlayLms()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "mta",
		name = "Mage Training Arena",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 8
	)
	default OverlayMode overlayMta()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "mahogany_homes",
		name = "Mahogany Homes",
		description = "At the activity: shows after you earn its currency. Always: stays on screen. Off: never shows.",
		position = 9
	)
	default OverlayMode overlayMahoganyHomes()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "mixology",
		name = "Mastering Mixology",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 10
	)
	default OverlayMode overlayMixology()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "pest_control",
		name = "Pest Control",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 11
	)
	default OverlayMode overlayPestControl()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "soul_wars",
		name = "Soul Wars",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 12
	)
	default OverlayMode overlaySoulWars()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "temple_trekking",
		name = "Temple Trekking",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 13
	)
	default OverlayMode overlayTempleTrekking()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "tithe_farm",
		name = "Tithe Farm",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 14
	)
	default OverlayMode overlayTitheFarm()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "trouble_brewing",
		name = "Trouble Brewing",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 15
	)
	default OverlayMode overlayTroubleBrewing()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "vale_totems",
		name = "Vale Totems",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 16
	)
	default OverlayMode overlayValeTotems()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "volcanic_mine",
		name = "Volcanic Mine",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 17
	)
	default OverlayMode overlayVolcanicMine()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "tempoross",
		name = "Tempoross",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 18
	)
	default OverlayMode overlayTempoross()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "aerial_fishing",
		name = "Aerial Fishing",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 19
	)
	default OverlayMode overlayAerialFishing()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "colossal_wyrm",
		name = "Colossal Wyrm Agility",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 20
	)
	default OverlayMode overlayColossalWyrm()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "forestry",
		name = "Forestry",
		description = "At the activity: shows after you earn its currency. Always: stays on screen. Off: never shows.",
		position = 21
	)
	default OverlayMode overlayForestry()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "motherlode_mine",
		name = "Motherlode Mine",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 22
	)
	default OverlayMode overlayMotherlodeMine()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "rooftop_agility",
		name = "Rooftop Agility",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 23
	)
	default OverlayMode overlayRooftopAgility()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "shooting_stars",
		name = "Shooting Stars",
		description = "At the activity: shows after you earn its currency. Always: stays on screen. Off: never shows.",
		position = 24
	)
	default OverlayMode overlayShootingStars()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "tzhaar",
		name = "TzHaar",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 25
	)
	default OverlayMode overlayTzhaar()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "miscellaneous",
		name = "Miscellaneous",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 26
	)
	default OverlayMode overlayMiscellaneous()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}

	@ConfigItem(
		section = OVERLAY_ACTIVITIES,
		keyName = OVERLAY_KEY_PREFIX + "chompy",
		name = "Chompy Bird Hunting",
		description = "At the activity: shows while you're there. Always: stays on screen. Off: never shows.",
		position = 27
	)
	default OverlayMode overlayChompy()
	{
		return OverlayMode.AT_THE_ACTIVITY;
	}
}
