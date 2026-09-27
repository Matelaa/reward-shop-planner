package com.rewardshopplanner;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RewardShopPlannerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RewardShopPlannerPlugin.class);
		RuneLite.main(args);
	}
}
