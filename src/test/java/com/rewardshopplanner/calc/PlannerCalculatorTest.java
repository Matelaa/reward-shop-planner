package com.rewardshopplanner.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.rewardshopplanner.data.RewardData;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

public class PlannerCalculatorTest
{
	private static final String BARK = "anima_bark";

	private static RewardData data;
	private static PlannerCalculator calculator;

	@BeforeClass
	public static void load() throws Exception
	{
		data = RewardData.load(new Gson());
		calculator = new PlannerCalculator(data);
	}

	/**
	 * The ironman Forestry goal worked out by hand: the four Forestry pieces (Lumberjack owned),
	 * the listed tools and the pheasant costume, plus 10,760 sawmill vouchers (161,400 bark)
	 * for 99 Construction, starting from 89,800 bark.
	 */
	private static PlannerInput forestryGoal(AccountMode mode)
	{
		return PlannerInput.builder()
			.owned(Set.of("Lumberjack hat", "Lumberjack top", "Lumberjack legs", "Lumberjack boots", "Log basket"))
			.wanted(Set.of("Forestry hat", "Forestry top", "Forestry legs", "Forestry boots", "Twitcher's gloves",
				"Funky shaped log", "Log brace", "Clothes pouch blueprint", "Cape pouch", "Felling axe handle",
				"Pheasant hat", "Pheasant legs", "Pheasant boots", "Pheasant cape"))
			.balances(Map.of(BARK, 89_800L))
			.extraGoals(Map.of(BARK, 161_400L))
			.accountMode(mode)
			.build();
	}

	@Test
	public void forestryIronmanMatchesHandCalculation()
	{
		Plan plan = calculator.plan(forestryGoal(AccountMode.IRONMAN));
		ActivityPlan forestry = plan.getActivity("forestry");

		assertEquals(14, forestry.getMissing().size());
		assertEquals(50_500, forestry.getCost().getGross(BARK));
		// Funky shaped log and cape pouch sell back for 80%: 15,000 -> 3,000 and 2,500 -> 500
		assertEquals(36_500, forestry.getCost().getNet(BARK));
		assertEquals(60, forestry.getCost().getGross("pheasant_feather"));
		// 4 x 60 for the Forestry outfit + 500 for the funky shaped log, 400 of which come back
		assertEquals(740, forestry.getCost().getMaterialGross("Mahogany logs"));
		assertEquals(340, forestry.getCost().getMaterialNet("Mahogany logs"));

		assertEquals(122_100, plan.getRemainingGross(BARK));
		assertEquals(108_100, plan.getRemainingNet(BARK));
	}

	@Test
	public void ultimateIronmanCannotSellBack()
	{
		Plan plan = calculator.plan(forestryGoal(AccountMode.ULTIMATE_IRONMAN));
		assertEquals(50_500, plan.getActivity("forestry").getCost().getNet(BARK));
		assertEquals(122_100, plan.getRemainingNet(BARK));
	}

	@Test
	public void emptyAccountTotalsMatchWikiShopTotals()
	{
		Plan plan = calculator.plan(PlannerInput.builder().build());

		// Honest Jimmy's House of Stuff: "39,590 pieces of eight are required to buy everything"
		assertEquals(39_590, plan.getActivity("trouble_brewing").getCost().getGross("pieces_of_eight"));
		// Vale Research Exchange: "1,120 research points are required to buy one of everything"
		assertEquals(1_120, plan.getActivity("vale_totems").getCost().getGross("vale_research"));
		// Rewards Guardian combined total minus the rune pouch, which has no log slot
		ActivityPlan mta = plan.getActivity("mta");
		assertEquals(2_675, mta.getCost().getGross("mta_tele"));
		assertEquals(3_075, mta.getCost().getGross("mta_alch"));
		assertEquals(27_500, mta.getCost().getGross("mta_ench"));
		assertEquals(2_675, mta.getCost().getGross("mta_grave"));
	}

	@Test
	public void sharedItemsAreCountedOnceInTotal()
	{
		Plan plan = calculator.plan(PlannerInput.builder().build());

		// The angler outfit (200 pearls) is on both the Aerial Fishing and Fishing Trawler pages
		assertEquals(1_070, plan.getActivity("aerial_fishing").getCost().getGross("molch_pearl"));
		assertEquals(200, plan.getActivity("fishing_trawler").getCost().getGross("molch_pearl"));
		assertEquals(1_070, plan.getTotal().getGross("molch_pearl"));
	}

	@Test
	public void recolouredSetIsPaidOnceAndNeedsTheBaseSet()
	{
		Plan plan = calculator.plan(PlannerInput.builder().build());
		ActivityPlan wyrm = plan.getActivity("colossal_wyrm");

		// teleport scroll 40 + calcified acorn 900 + one graceful crafting kit 650
		assertEquals(1_590, wyrm.getCost().getGross("termites"));
		assertEquals(6, wyrm.getPrerequisites().size());
		assertEquals(260, wyrm.getCost().getGross("mark_of_grace"));
		// both recolours need the base set, but it is only bought once
		assertEquals(260, plan.getTotal().getGross("mark_of_grace"));
	}

	@Test
	public void ownedBaseSetIsNotAPrerequisite()
	{
		Plan plan = calculator.plan(PlannerInput.builder()
			.owned(Set.of("Graceful hood", "Graceful top", "Graceful legs", "Graceful gloves", "Graceful boots", "Graceful cape"))
			.build());
		assertTrue(plan.getActivity("colossal_wyrm").getPrerequisites().isEmpty());
		assertEquals(0, plan.getTotal().getGross("mark_of_grace"));
	}

	@Test
	public void sharedItemUsesPreferredShop()
	{
		// The prospector kit is sold at both mines: nothing is priced until the player picks one
		Plan undecided = calculator.plan(PlannerInput.builder().build());
		assertEquals(Set.of("Prospector helmet", "Prospector jacket", "Prospector legs", "Prospector boots"), undecided.getUndecided());
		assertEquals(4, undecided.getActivity("motherlode_mine").getUndecided().size());
		assertEquals(4, undecided.getActivity("volcanic_mine").getUndecided().size());
		assertEquals(40_000 + 200, undecided.getTotal().getGross("vm_points"));
		assertEquals(200, undecided.getTotal().getGross("golden_nugget"));

		Plan withNuggets = calculator.plan(PlannerInput.builder()
			.preferredActivity(Map.of(
				"Prospector helmet", "motherlode_mine",
				"Prospector jacket", "motherlode_mine",
				"Prospector legs", "motherlode_mine",
				"Prospector boots", "motherlode_mine"))
			.build());
		assertTrue(withNuggets.getUndecided().isEmpty());
		assertEquals(40_000 + 200, withNuggets.getTotal().getGross("vm_points"));
		assertEquals(200 + 40 + 60 + 50 + 30, withNuggets.getTotal().getGross("golden_nugget"));
		// the pick applies on both pages: finishing the Volcanic Mine page now costs nuggets for the kit
		assertEquals(40 + 60 + 50 + 30, withNuggets.getActivity("volcanic_mine").getCost().getGross("golden_nugget"));
	}

	@Test
	public void greenmanMaskRefundIsIronOnly()
	{
		Plan main = calculator.plan(PlannerInput.builder().build());
		Plan iron = calculator.plan(PlannerInput.builder().accountMode(AccountMode.IRONMAN).build());

		// spool 250 -> 125 back and knife 350 -> 175 back for everyone; mask 500 -> 125 back for irons only
		assertEquals(1_120 - 125 - 175, main.getActivity("vale_totems").getCost().getNet("vale_research"));
		assertEquals(1_120 - 125 - 175 - 125, iron.getActivity("vale_totems").getCost().getNet("vale_research"));
	}

	@Test
	public void emptyGoalCostsNothing()
	{
		Plan plan = calculator.plan(PlannerInput.builder()
			.wanted(Set.of())
			.extraGoals(Map.of(BARK, 100L))
			.build());

		assertTrue(plan.getTotal().getGross().keySet().stream().allMatch(BARK::equals));
		// extra goals always count
		assertEquals(100, plan.getTotal().getGross(BARK));
		assertTrue(plan.getUndecided().isEmpty());
	}

	@Test
	public void onlyWantedItemsCountButTradeInsAreAdded()
	{
		// wanting just the Varlamore hood still pulls in the whole recolour set and the base set it trades in
		Plan plan = calculator.plan(PlannerInput.builder()
			.wanted(Set.of("Graceful hood (Varlamore)", "Decorative helm (gold)"))
			.build());

		assertEquals(650, plan.getTotal().getGross("termites"));
		assertEquals(260, plan.getTotal().getGross("mark_of_grace"));
		assertEquals(400, plan.getTotal().getGross("cw_ticket"));
		assertEquals(1, plan.getActivity("castle_wars").getMissing().size());
	}

	@Test
	public void milestoneNeedsTheHighestCountNotTheSum()
	{
		Plan plan = calculator.plan(PlannerInput.builder()
			.wanted(Set.of("Victor's cape (10)", "Victor's cape (100)",
				"Chompy bird hat (ogre bowman)", "Chompy bird hat (bowman)"))
			.owned(Set.of("Victor's cape (1)"))
			.balances(Map.of("lms_wins", 40L, "chompy_kills", 55L))
			.build());

		assertEquals(100, plan.getTotal().getGross("lms_wins"));
		assertEquals(60, plan.getRemainingGross("lms_wins"));
		assertEquals(40, plan.getTotal().getGross("chompy_kills"));
		assertEquals(0, plan.getRemainingGross("chompy_kills"));
		assertEquals(100, plan.getActivity("lms").getCost().getGross("lms_wins"));
	}

	@Test
	public void pageProgressOnlyCountsPurchasableSlots()
	{
		// Tempoross has 12 log slots but only the tackle box and fish barrel can be bought
		Plan plan = calculator.plan(PlannerInput.builder()
			.owned(Set.of("Tackle box", "Fish barrel", "Tiny tempor"))
			.wanted(Set.of())
			.build());
		ActivityPlan tempoross = plan.getActivity("tempoross");
		assertEquals(2, tempoross.getSlotsTotal());
		assertEquals(2, tempoross.getSlotsOwned());
		assertTrue(tempoross.isComplete());
	}

	@Test
	public void undecidedOnlyForWantedItems()
	{
		Plan plan = calculator.plan(PlannerInput.builder().wanted(Set.of("Prospector helmet")).build());
		assertEquals(Set.of("Prospector helmet"), plan.getUndecided());
	}
}
