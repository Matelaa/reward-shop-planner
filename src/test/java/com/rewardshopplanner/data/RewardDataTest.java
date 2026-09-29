package com.rewardshopplanner.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Sanity checks on the generated data so a bad regeneration fails the build.
 */
public class RewardDataTest
{
	private static RewardData data;

	@BeforeClass
	public static void load() throws Exception
	{
		data = RewardData.load(new Gson());
	}

	@Test
	public void loadsEveryFile()
	{
		assertEquals(28, data.getActivities().size());
		assertFalse(data.getRewards().isEmpty());
		assertFalse(data.getCurrencies().isEmpty());
	}

	@Test
	public void offersOnlyUseKnownCurrencies()
	{
		for (Reward reward : data.getRewards().values())
		{
			for (Reward.Offer offer : reward.getOffers())
			{
				for (String currency : offer.getCost().keySet())
				{
					assertNotNull(reward.getName() + " uses unknown currency " + currency, data.getCurrencies().get(currency));
				}
				for (String activity : offer.getActivities())
				{
					assertNotNull(reward.getName() + " offered by unknown activity " + activity, data.getActivities().get(activity));
				}
			}
		}
	}

	@Test
	public void everyRewardIsOnALogPage()
	{
		for (Reward reward : data.getRewards().values())
		{
			assertFalse(reward.getName() + " has no collection log page", reward.getClogPages().isEmpty());
			for (String page : reward.getClogPages())
			{
				assertTrue(reward.getName() + " lists unknown page " + page,
					data.getActivities().values().stream().anyMatch(a -> a.getClogPage().equals(page)));
			}
		}
	}

	@Test
	public void costableRewardsHaveACost()
	{
		for (Reward reward : data.getRewards().values())
		{
			if (reward.getType() == Reward.Type.MILESTONE)
			{
				assertNotNull(reward.getName() + " has no milestone", reward.getMilestone());
				Currency counter = data.getCurrencies().get(reward.getMilestone().getCounter());
				assertNotNull(reward.getName() + " counts an unknown counter", counter);
				assertEquals(Currency.Source.COUNTER, counter.getSource());
			}
			else if (reward.getType() != Reward.Type.RANDOM)
			{
				assertTrue(reward.getName() + " has no price", reward.isCostable());
			}
		}
	}

	@Test
	public void everyCurrencyHasASource()
	{
		// every balance is read from the game: an item, a var, an interface or a counter
		for (Currency currency : data.getCurrencies().values())
		{
			assertTrue(currency.getId() + " has no source", currency.getSource() != Currency.Source.UNKNOWN);
		}
	}

	@Test
	public void consumedItemsExist()
	{
		for (Reward reward : data.getRewards().values())
		{
			for (String consumed : reward.getConsumes())
			{
				assertNotNull(reward.getName() + " consumes unknown item " + consumed, data.getReward(consumed));
			}
		}
	}

	@Test
	public void largeWaterContainerIsSoldAtVolcanicMine()
	{
		// the wiki sells it as "Heat-proof vessel"; the log slot has its own id
		Reward container = data.getReward("Large water container");
		assertNotNull(container);
		assertEquals(Integer.valueOf(25615), container.getItemId());
		assertEquals(Integer.valueOf(10_000), container.getOffers().get(0).getCost().get("vm_points"));
		assertEquals(7, data.getActivities().get("volcanic_mine").getClogItems().stream()
			.filter(i -> data.getReward(i) != null).count());
	}

	@Test
	public void logSlotsUseTheNormalVersionOfTheirItem() throws Exception
	{
		// The wiki lists broken and locked versions too (Castle Wars gold armour); the log shows
		// the normal one, and its "Search" sends that id.
		Map<Integer, String> names = new HashMap<>();
		for (java.lang.reflect.Field field : net.runelite.api.gameval.ItemID.class.getFields())
		{
			if (field.getType() == int.class)
			{
				names.putIfAbsent(field.getInt(null), field.getName());
			}
		}
		for (Activity activity : data.getActivities().values())
		{
			for (int i = 0; i < activity.getClogItems().size(); i++)
			{
				String slot = activity.getClogItems().get(i);
				String constant = names.getOrDefault(activity.getClogItemIds()[i], "");
				if (!slot.toLowerCase().contains("broken"))
				{
					assertFalse(slot + " uses " + constant, constant.endsWith("_BROKEN") || constant.contains("_LOCKED"));
				}
			}
		}
		assertEquals(4511, (int) data.getReward("Decorative helm (gold)").getItemId());
	}

	@Test
	public void everyMaterialHasAnItemId()
	{
		for (Reward reward : data.getRewards().values())
		{
			for (String material : reward.getMaterials().keySet())
			{
				Material known = data.getMaterials().get(material);
				assertNotNull(reward.getName() + " uses unknown material " + material, known);
				assertTrue(material + " has no item id", known.getItemId() > 0);
			}
		}
		assertEquals(1521, data.getMaterials().get("Oak logs").getItemId());
	}
}
