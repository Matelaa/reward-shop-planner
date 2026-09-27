package com.rewardshopplanner.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.rewardshopplanner.data.Activity;
import com.rewardshopplanner.data.RewardData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

public class CollectionLogMatcherTest
{
	private static RewardData data;
	private static CollectionLogMatcher matcher;

	@BeforeClass
	public static void load() throws Exception
	{
		data = RewardData.load(new Gson());
		matcher = new CollectionLogMatcher(data);
	}

	/** Builds the slots the game would draw for a page, marking the given names as obtained. */
	private static List<CollectionLogMatcher.Slot> slotsFor(Activity activity, List<String> obtained)
	{
		List<CollectionLogMatcher.Slot> slots = new ArrayList<>();
		for (int i = 0; i < activity.getClogItems().size(); i++)
		{
			String name = activity.getClogItems().get(i);
			slots.add(new CollectionLogMatcher.Slot(activity.getClogItemIds()[i], name, obtained.contains(name)));
		}
		return slots;
	}

	@Test
	public void findsPagesByInGameTitle()
	{
		assertEquals("mta", matcher.activityForPage("Magic Training Arena").getId());
		assertEquals("forestry", matcher.activityForPage("Forestry").getId());
		assertNull(matcher.activityForPage("Zulrah"));
	}

	@Test
	public void everySlotHasAnIdOrAName()
	{
		for (Activity activity : data.getActivities().values())
		{
			assertNotNull(activity.getId(), activity.getClogItemIds());
			assertEquals(activity.getId(), activity.getClogItems().size(), activity.getClogItemIds().length);
		}
	}

	@Test
	public void matchesAFullPage()
	{
		Activity forestry = data.getActivities().get("forestry");
		Map<String, Boolean> result = matcher.match(forestry, slotsFor(forestry, List.of("Lumberjack hat", "Cape pouch")));

		assertEquals(forestry.getClogItems().size(), result.size());
		assertTrue(result.get("Lumberjack hat"));
		assertTrue(result.get("Cape pouch"));
		assertFalse(result.get("Forestry hat"));
	}

	@Test
	public void recolouredGracefulIsNotConfusedWithBaseSet()
	{
		// the game names every recolour "Graceful hood"; ids tell them apart
		Activity wyrm = data.getActivities().get("colossal_wyrm");
		Map<String, Boolean> result = matcher.match(wyrm, slotsFor(wyrm, List.of("Graceful hood (Varlamore)")));
		assertTrue(result.get("Graceful hood (Varlamore)"));
		assertFalse(result.containsKey("Graceful hood"));
	}

	@Test
	public void fallsBackToPositionWhenIdIsUnknown()
	{
		Activity lms = data.getActivities().get("lms");
		List<CollectionLogMatcher.Slot> slots = slotsFor(lms, List.of("Animation overrides"));
		// the unlock has no item id in the data; the game shows some placeholder item
		int index = lms.getClogItems().indexOf("Animation overrides");
		slots.set(index, new CollectionLogMatcher.Slot(99999, "Animation overrides", true));

		assertTrue(matcher.match(lms, slots).get("Animation overrides"));
	}

	@Test
	public void matchesAnotherVersionOfAnItemByName()
	{
		// the log can show the Volcanic Mine prospector helmet (29472) instead of the Motherlode one (12013)
		Activity mlm = data.getActivities().get("motherlode_mine");
		List<CollectionLogMatcher.Slot> slots = slotsFor(mlm, mlm.getClogItems());
		int helmet = mlm.getClogItems().indexOf("Prospector helmet");
		slots.set(helmet, new CollectionLogMatcher.Slot(29472, "Prospector helmet", true));
		// and an extra drawn child makes the page shape differ, so position can't be used
		slots.add(new CollectionLogMatcher.Slot(1, "", false));

		Map<String, Boolean> result = matcher.match(mlm, slots);
		assertTrue(result.get("Prospector helmet"));
		assertEquals(mlm.getClogItems().size(), result.size());
	}

	@Test
	public void chatNameResolvesOnlyWhenUnambiguous()
	{
		assertEquals("Castlewars hood (Saradomin)", matcher.resolveChatName("Castlewars hood (Saradomin)"));
		// on two pages, but the same item
		assertEquals("Angler hat", matcher.resolveChatName("Angler hat"));
		// base set and two recolours share the in-game name
		assertNull(matcher.resolveChatName("Graceful hood"));
		assertNull(matcher.resolveChatName("Not an item"));
	}
}
