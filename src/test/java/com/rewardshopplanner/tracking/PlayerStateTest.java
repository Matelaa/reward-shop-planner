package com.rewardshopplanner.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlayerStateTest
{
	@Test
	public void playerCorrectionOverridesTheLog()
	{
		PlayerState state = new PlayerState();
		state.setLogOwned("Forestry hat", false);
		state.setOwnedByPlayer("Forestry hat", true);

		assertTrue(state.effectiveOwned().contains("Forestry hat"));
		assertTrue(state.getOwnedOverrides().containsKey("Forestry hat"));
	}

	@Test
	public void correctionIsDroppedOnceTheLogAgrees()
	{
		PlayerState state = new PlayerState();
		state.setOwnedByPlayer("Forestry hat", true);
		state.setLogOwned("Forestry hat", true);

		assertTrue(state.effectiveOwned().contains("Forestry hat"));
		assertFalse(state.getOwnedOverrides().containsKey("Forestry hat"));
	}

	@Test
	public void theLogWinsOverAnOldCorrection()
	{
		// the player once marked the helmet as "not owned"; the log later shows it obtained
		PlayerState state = new PlayerState();
		state.setLogOwned("Prospector helmet", true);
		state.setOwnedByPlayer("Prospector helmet", false);
		assertFalse(state.effectiveOwned().contains("Prospector helmet"));

		state.setLogOwned("Prospector helmet", true);
		assertTrue(state.effectiveOwned().contains("Prospector helmet"));
		assertTrue(state.getOwnedOverrides().isEmpty());
	}

	@Test
	public void itemCurrenciesAddBankInventoryAndForestryKit() throws Exception
	{
		com.rewardshopplanner.data.Currency bark = com.rewardshopplanner.data.RewardData.load(new com.google.gson.Gson())
			.getCurrencies().get("anima_bark");
		PlayerState state = new PlayerState();
		assertEquals(null, state.balanceOf(bark));
		state.getBankItems().put("anima_bark", 4_000L);
		state.getInventoryItems().put("anima_bark", 166L);
		state.getForestryKitItems().put("anima_bark", 500L);
		assertEquals(Long.valueOf(4_666), state.balanceOf(bark));
	}

	@Test
	public void honourPointsCombineBaseAndExtra() throws Exception
	{
		com.rewardshopplanner.data.Currency attacker = com.rewardshopplanner.data.RewardData.load(new com.google.gson.Gson())
			.getCurrencies().get("ba_attacker");
		assertTrue(PlayerState.isTracked(attacker));
		// values from the Barbarian Assault shop: 367 attacker points, extra varbit still 0
		assertEquals(367, PlayerState.compositeValue(attacker, 367, 0));
		// the base varbit wraps at 512; each wrap adds 1 to the extra varbit
		assertEquals(600, PlayerState.compositeValue(attacker, 88, 1));
	}

	@Test
	public void unmarkingALoggedItemIsKeptAsCorrection()
	{
		PlayerState state = new PlayerState();
		state.setLogOwned("Cape pouch", true);
		state.setOwnedByPlayer("Cape pouch", false);
		assertFalse(state.effectiveOwned().contains("Cape pouch"));

		// marking it again matches the log, so no correction is stored
		state.setOwnedByPlayer("Cape pouch", true);
		assertTrue(state.effectiveOwned().contains("Cape pouch"));
		assertTrue(state.getOwnedOverrides().isEmpty());
	}

	@Test
	public void materialsAddBankAndInventory()
	{
		PlayerState state = new PlayerState();
		assertNull(state.materialOf("Oak logs"));
		state.getInventoryMaterials().put("Oak logs", 28L);
		assertEquals(Long.valueOf(28), state.materialOf("Oak logs"));
		state.getBankMaterials().put("Oak logs", 700L);
		assertEquals(Long.valueOf(728), state.materialOf("Oak logs"));
		state.getLogBasketMaterials().put("Oak logs", 28L);
		assertEquals(Long.valueOf(756), state.materialOf("Oak logs"));
	}

	@Test
	public void karamjaGlovesFollowTheDiaryUntilThePlayerChooses()
	{
		PlayerState state = new PlayerState();
		assertFalse(state.wearsKaramjaGloves());
		state.setKaramjaGlovesClaimed(true);
		assertTrue(state.wearsKaramjaGloves());

		// the player's choice wins either way, and "auto" goes back to the diary
		state.setKaramjaGlovesChoice(false);
		assertFalse(state.wearsKaramjaGloves());
		state.setKaramjaGlovesClaimed(false);
		state.setKaramjaGlovesChoice(true);
		assertTrue(state.wearsKaramjaGloves());
		state.setKaramjaGlovesChoice(null);
		assertFalse(state.wearsKaramjaGloves());
	}
}
