package com.rewardshopplanner;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChatTextTest
{
	@Test
	public void gameMarkersAndTagsAreRemoved()
	{
		// as the game sent it (seen in the client's debug log)
		assertEquals("You've been awarded 11 Anima-infused bark.",
			RewardShopPlannerPlugin.cleanChat("You've been awarded @mes_hl_blu@11 Anima-infused bark</col>."));
		assertEquals("Unfortunately you weren't able to save the fox from the poachers...",
			RewardShopPlannerPlugin.cleanChat("@mes_hl_blu@Unfortunately you weren't able to save the fox from the poachers..."));
		assertEquals("You've been awarded 4 Anima-infused bark.",
			RewardShopPlannerPlugin.cleanChat("You've been awarded <col=0000ff>4 Anima-infused bark</col>."));
	}
}
