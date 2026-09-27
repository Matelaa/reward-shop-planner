package com.rewardshopplanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(RewardShopPlannerConfig.GROUP)
public interface RewardShopPlannerConfig extends Config
{
	String GROUP = "rewardshopplanner";

	enum AccountType
	{
		AUTO,
		MAIN,
		IRONMAN,
		ULTIMATE_IRONMAN
	}

	@ConfigItem(
		keyName = "accountType",
		name = "Account type",
		description = "Decides which items can be sold back to their shop. Auto reads it from the game.",
		position = 0
	)
	default AccountType accountType()
	{
		return AccountType.AUTO;
	}

	enum KaramjaGloves
	{
		AUTO,
		YES,
		NO
	}

	@ConfigItem(
		keyName = "karamjaGloves",
		name = "Karamja gloves at TzHaar",
		description = "Wearing Karamja gloves makes TzHaar items cheaper and sell back for more."
			+ " Auto assumes you wear them once you've claimed them from the Karamja easy diary.",
		position = 1
	)
	default KaramjaGloves karamjaGloves()
	{
		return KaramjaGloves.AUTO;
	}

	@ConfigItem(
		keyName = "hideCompleted",
		name = "Hide completed pages",
		description = "Hide pages with nothing left to buy",
		position = 2
	)
	default boolean hideCompleted()
	{
		return false;
	}
}
