package com.rewardshopplanner.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.rewardshopplanner.RewardShopPlannerConfig;
import com.rewardshopplanner.calc.AccountMode;
import com.rewardshopplanner.calc.PlannerCalculator;
import com.rewardshopplanner.calc.PlannerInput;
import com.rewardshopplanner.data.Activity;
import com.rewardshopplanner.data.RewardData;
import com.rewardshopplanner.ui.PanelModel;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import org.junit.BeforeClass;
import org.junit.Test;

public class PageProgressTest
{
	private static RewardData data;

	@BeforeClass
	public static void load() throws Exception
	{
		data = RewardData.load(new Gson());
	}

	private static PanelModel model(Set<String> owned, Set<String> wanted, Map<String, Long> balances, Map<String, Long> extraGoals)
	{
		PlannerInput input = PlannerInput.builder().owned(owned).wanted(wanted).balances(balances).extraGoals(extraGoals).build();
		return new PanelModel(data, new PlannerCalculator(data).plan(input), balances, Map.of(), owned, Set.of(), Set.of(), wanted,
			Map.of(), extraGoals, Map.of(), AccountMode.MAIN, Set.of(), false, true, false, false);
	}

	@Test
	public void showsGoalItemsLeftAndReady()
	{
		// Trouble Brewing: two items in the goal, and enough pieces of eight for only the cheaper one
		Activity brewing = data.getActivities().get("trouble_brewing");
		String cheap = "Purple tricorn hat";
		String dear = "Lucky shot flag";
		int cheapPrice = data.getReward(cheap).getOffers().get(0).getCost().get("pieces_of_eight");
		PanelModel model = model(Set.of(), Set.of(cheap, dear), Map.of("pieces_of_eight", (long) cheapPrice), Map.of());

		PageProgress page = PageProgress.of(model, brewing);
		assertNotNull(page);
		assertEquals(2, page.getLeft());
		assertEquals(1, page.getReady());
		assertEquals("2 left · 1 ready", ProgressOverlay.summary(page));
		assertEquals(1, page.getBars().size());
		assertEquals("pieces_of_eight", page.getBars().get(0).getCurrencyId());
		assertEquals(Long.valueOf(cheapPrice), page.getBars().get(0).getHave());
		assertEquals(500 + 4_000, page.getBars().get(0).getNeed());
	}

	@Test
	public void readyCountsPurchasesOneAfterAnother()
	{
		// the whole TzHaar page, sold back as it's logged, with Karamja gloves and 99,629 tokkul:
		// each item alone is affordable except the platebody, but in a row only three fit
		Activity tzhaar = data.getActivities().get("tzhaar");
		Set<String> page = tzhaar.getClogItems().stream().filter(i -> data.getReward(i) != null).collect(java.util.stream.Collectors.toSet());
		Map<String, Long> balances = Map.of("tokkul", 99_629L);
		PlannerInput input = PlannerInput.builder().wanted(page).balances(balances).sellBack(page).karamjaGloves(true).build();
		PanelModel model = new PanelModel(data, new PlannerCalculator(data).plan(input), balances, Map.of(), Set.of(), Set.of(), Set.of(),
			page, Map.of(), Map.of(), Map.of(), AccountMode.MAIN, page, true, true, false, false);

		PageProgress progress = PageProgress.of(model, tzhaar);
		assertEquals(10, progress.getLeft());
		// Toktz-xil-ul (325, 87 back), Toktz-xil-ek (32,500, 8,750 back), Toktz-mej-tal (45,500, 12,250 back)
		// leave 42,391: not enough for the next cheapest, the Toktz-xil-ak at 52,000
		assertEquals(3, progress.getReady());
	}

	@Test
	public void longLinesUseShortNumbers()
	{
		assertEquals("4,666", ProgressOverlay.format(4_666, true));
		assertEquals("75K", ProgressOverlay.format(75_000, true));
		assertEquals("12.4K", ProgressOverlay.format(12_400, true));
		assertEquals("1.25M", ProgressOverlay.format(1_250_000, true));
		assertEquals("75,000", ProgressOverlay.format(75_000, false));
	}

	@Test
	public void nothingInTheGoalShowsNothing()
	{
		assertNull(PageProgress.of(model(Set.of(), Set.of(), Map.of(), Map.of()), data.getActivities().get("castle_wars")));
	}

	@Test
	public void extraGoalKeepsACompletedPageOnScreen()
	{
		// every Forestry slot owned, but saving bark for sawmill vouchers
		Set<String> owned = new HashSet<>(data.getActivities().get("forestry").getClogItems());
		PanelModel model = model(owned, Set.of(), Map.of("anima_bark", 123_108L), Map.of("anima_bark", 164_100L));

		PageProgress page = PageProgress.of(model, data.getActivities().get("forestry"));
		assertNotNull(page);
		assertEquals(0, page.getLeft());
		assertEquals("", ProgressOverlay.summary(page));
		assertEquals(164_100, page.getBars().get(0).getNeed());
		assertFalse(page.getBars().get(0).isDone());
	}

	@Test
	public void fixedPlacesHaveRegions()
	{
		Activity brewing = data.getActivities().get("trouble_brewing");
		assertEquals(Set.of(), brewing.currenciesAt(15151, 3812, 3021, 0));
		assertNull(brewing.currenciesAt(12850, 3200, 3200, 0));
		// rooftop courses only count up on the roofs, not walking through Varrock
		Activity rooftops = data.getActivities().get("rooftop_agility");
		assertNull(rooftops.currenciesAt(12853, 3220, 3410, 0));
		assertNotNull(rooftops.currenciesAt(12853, 3220, 3410, 3));
		// no fixed place: shown after earning instead
		assertFalse(data.getActivities().get("forestry").hasPlace());
		assertFalse(data.getActivities().get("shooting_stars").hasPlace());
	}

	@Test
	public void areasWalkedInGameAreExact()
	{
		// Castle Wars lobby, but not across the river in the same map region
		Activity castleWars = data.getActivities().get("castle_wars");
		assertNotNull(castleWars.currenciesAt(9776, 2440, 3090, 0));
		assertNotNull(castleWars.currenciesAt(9776, 2443, 3090, 0)); // a tile on the edge counts
		assertNull(castleWars.currenciesAt(9776, 2460, 3090, 0));
		// chompy hunting: the marsh below Castle Wars and the Feldip Hills spot, not the land around
		Activity chompy = data.getActivities().get("chompy");
		assertNotNull(chompy.currenciesAt(9519, 2370, 3050, 0));
		assertNotNull(chompy.currenciesAt(10286, 2598, 2966, 0));
		assertNull(chompy.currenciesAt(9519, 2420, 3100, 0));
		// Barbarian Assault's lobby, not the whole outpost down to the fishing spot
		Activity assault = data.getActivities().get("barbarian_assault");
		assertNotNull(assault.currenciesAt(10039, 2530, 3570, 0));
		assertNull(assault.currenciesAt(10039, 2500, 3510, 0));
	}

	@Test
	public void miscellaneousShowsOnlyTheCurrencyOfThePlace()
	{
		Activity misc = data.getActivities().get("miscellaneous");
		assertEquals(Set.of("unid_minerals"), misc.currenciesAt(12183, 3015, 9715, 0));
		assertNull(misc.currenciesAt(12183, 3040, 9715, 0));
		assertEquals(Set.of("mermaids_tear"), misc.currenciesAt(15008, 3765, 10290, 1));

		// with all four in the goal, the Mining Guild counts only the gloves and shows only minerals
		Set<String> goal = Set.of("Mining gloves", "Superior mining gloves", "Expert mining gloves", "Merfolk trident");
		PageProgress guild = PageProgress.of(model(Set.of(), goal, Map.of(), Map.of()), misc, Set.of("unid_minerals"));
		assertEquals(3, guild.getLeft());
		assertEquals(1, guild.getBars().size());
		assertEquals("unid_minerals", guild.getBars().get(0).getCurrencyId());
	}

	@Test
	public void everyActivityHasAnOverlaySwitch()
	{
		Set<String> keys = new HashSet<>();
		for (Method method : RewardShopPlannerConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && item.keyName().startsWith(RewardShopPlannerConfig.OVERLAY_KEY_PREFIX))
			{
				keys.add(item.keyName().substring(RewardShopPlannerConfig.OVERLAY_KEY_PREFIX.length()));
			}
		}
		assertEquals(data.getActivities().keySet(), keys);
	}
}
