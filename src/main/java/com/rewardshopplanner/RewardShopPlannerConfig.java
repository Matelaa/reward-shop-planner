package com.rewardshopplanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(RewardShopPlannerConfig.GROUP)
public interface RewardShopPlannerConfig extends Config
{
	String GROUP = "rewardshopplanner";

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
}
