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

	@ConfigItem(
		keyName = "countSellBacks",
		name = "Count sell-backs",
		description = "Show what you still need after selling items back to their shop once the slot is logged",
		position = 1
	)
	default boolean countSellBacks()
	{
		return true;
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
