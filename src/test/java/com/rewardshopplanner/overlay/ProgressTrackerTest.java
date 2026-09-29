package com.rewardshopplanner.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.rewardshopplanner.RewardShopPlannerConfig;
import com.rewardshopplanner.data.RewardData;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/**
 * When an item currency counts as earned, so the progress of activities without a fixed place
 * (Forestry, Shooting Stars...) only shows for real gains.
 */
public class ProgressTrackerTest
{
	private static final String BARK = "anima_bark";

	private ProgressTracker tracker;

	@Before
	public void setUp() throws Exception
	{
		tracker = new ProgressTracker(null, null, null, null, null, RewardData.load(new Gson()));
	}

	/** One game tick: bark in the inventory, and in the bank plus Forestry kit. */
	private void tick(long inventory, long stored)
	{
		tracker.onItemCounts(Map.of(BARK, inventory), Map.of(BARK, stored));
	}

	@Test
	public void gainingBarkCounts()
	{
		tick(100, 0);
		tick(150, 0);
		assertTrue(tracker.wasEarned(BARK));
	}

	@Test
	public void readingTheKitForTheFirstTimeDoesNot()
	{
		tick(0, 0);
		tick(0, 4_666);
		assertFalse(tracker.wasEarned(BARK));
	}

	@Test
	public void takingBarkOutOfTheKitOrBankDoesNot()
	{
		tick(0, 4_666);
		tick(4_666, 0);
		assertFalse(tracker.wasEarned(BARK));
	}

	@Test
	public void droppingAndPickingUpDoesNot()
	{
		tick(4_666, 0);
		tick(0, 0);
		tracker.onPickup(BARK, 4_666);
		tick(4_666, 0);
		assertFalse(tracker.wasEarned(BARK));
	}

	@Test
	public void depositBoxThenOpeningTheBankDoesNot()
	{
		tick(4_666, 0);
		tick(0, 0);
		tick(0, 4_666);
		assertFalse(tracker.wasEarned(BARK));
	}

	@Test
	public void earningAfterSpendingStillCounts()
	{
		tick(164_100, 0);
		tick(0, 0);
		tick(50, 0);
		assertTrue(tracker.wasEarned(BARK));
	}

	@Test
	public void activitySettingIsReadWithItsOlderForm()
	{
		assertEquals(RewardShopPlannerConfig.OverlayMode.AT_THE_ACTIVITY, ProgressTracker.modeOf(null));
		assertEquals(RewardShopPlannerConfig.OverlayMode.ALWAYS, ProgressTracker.modeOf("ALWAYS"));
		assertEquals(RewardShopPlannerConfig.OverlayMode.OFF, ProgressTracker.modeOf("OFF"));
		// saved by the checkbox this setting used to be
		assertEquals(RewardShopPlannerConfig.OverlayMode.AT_THE_ACTIVITY, ProgressTracker.modeOf("true"));
		assertEquals(RewardShopPlannerConfig.OverlayMode.OFF, ProgressTracker.modeOf("false"));
	}

	@Test
	public void firstReadingOfASessionIsOnlyTheBaseline()
	{
		// played elsewhere since the saved value: the first reading catches up, it isn't earned
		tracker.onGameValue("carpenter_points", 50L, 100);
		assertFalse(tracker.wasEarned("carpenter_points"));
		tracker.onGameValue("carpenter_points", 100L, 120);
		assertTrue(tracker.wasEarned("carpenter_points"));

		// a new session starts over
		tracker.onLogout();
		tracker.onGameValue("carpenter_points", 120L, 200);
		assertFalse(tracker.wasEarned("carpenter_points"));
	}
}
