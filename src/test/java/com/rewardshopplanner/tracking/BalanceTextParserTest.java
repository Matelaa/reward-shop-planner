package com.rewardshopplanner.tracking;

import static com.rewardshopplanner.tracking.BalanceTextParser.after;
import static com.rewardshopplanner.tracking.BalanceTextParser.before;
import static com.rewardshopplanner.tracking.BalanceTextParser.first;
import static com.rewardshopplanner.tracking.BalanceTextParser.read;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BalanceTextParserTest
{
	@Test
	public void readsShopFooters()
	{
		// Last Man Standing
		assertEquals(Long.valueOf(0), read("Points: 0 Wins: 0", after("Points")));
		assertEquals(Long.valueOf(0), read("Points: 0 Wins: 0", after("Wins")));
		assertEquals(Long.valueOf(1245), read("Points: 1,245 Wins: 57", after("Points")));
		assertEquals(Long.valueOf(57), read("Points: 1,245 Wins: 57", after("Wins")));
		// Void Knights' reward options
		assertEquals(Long.valueOf(480), read("Points: 480 Confirm", after("Points")));
		// Petrified Pete's ore shop (Volcanic Mine)
		assertEquals(Long.valueOf(0), read("Points: 0", after("Points")));
		assertEquals(Long.valueOf(40200), read("Points: 40,200", after("Points")));
		// Magic Training Arena shop: balance / price of the selected item
		String mta = "Cost to buy the item Telekinetic: 0/30 Graveyard: 0/30 Enchantment: 123/300 Alchemist: 0/30";
		assertEquals(Long.valueOf(0), read(mta, after("Telekinetic")));
		assertEquals(Long.valueOf(123), read(mta, after("Enchantment")));
	}

	@Test
	public void readsMahoganyHomes()
	{
		// reward shop footer
		assertEquals(Long.valueOf(1525), read("Carpenter Points: 1,525 Confirm", after("Points")));
		// chat after finishing a contract
		assertEquals(Long.valueOf(1530), read("You have completed 377 contracts with a total of 1,530 points.",
			BalanceTextParser.MAHOGANY_CONTRACT));
		assertNull(read("Jess seems happy with your work. Talk to her for your reward.", BalanceTextParser.MAHOGANY_CONTRACT));
	}

	@Test
	public void readsTheProgressHatDialogue()
	{
		String hat = "Ok, I suppose it's my job. You have: 0 Telekinetic, 0 Alchemist, 123 Enchantment, and 0 Graveyard Pizazz Points.";
		assertEquals(Long.valueOf(0), read(hat, before("Telekinetic", "Pizazz Points")));
		assertEquals(Long.valueOf(123), read(hat, before("Enchantment", "Pizazz Points")));
		assertEquals(Long.valueOf(0), read(hat, before("Graveyard", "Pizazz Points")));
		// other dialogue mentioning a room is ignored
		assertNull(read("You have 5 Enchantment runes.", before("Enchantment", "Pizazz Points")));
	}

	@Test
	public void readsValueOnlyComponents()
	{
		assertEquals(Long.valueOf(123), read("123", first()));
		assertEquals(Long.valueOf(480), read("Commendation points: 480", first()));
		assertNull(read("No numbers here", first()));
	}

	@Test
	public void ignoresTextWithoutTheValue()
	{
		assertNull(read("Tier 1 Cape One win", after("Points")));
		assertNull(read("160 points", after("Wins")));
	}
}
