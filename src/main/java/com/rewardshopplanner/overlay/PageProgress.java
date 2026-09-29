package com.rewardshopplanner.overlay;

import com.rewardshopplanner.calc.ActivityPlan;
import com.rewardshopplanner.calc.PlannerCalculator;
import com.rewardshopplanner.data.Activity;
import com.rewardshopplanner.data.Currency;
import com.rewardshopplanner.data.Reward;
import com.rewardshopplanner.ui.PanelModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * What the on-screen progress shows for one collection log page: how many goal items are left,
 * how many can be bought right now, and a bar per currency.
 */
@Value
public class PageProgress
{
	@Value
	public static class Bar
	{
		String currencyId;
		String name;
		/** Current balance, or null when it hasn't been read yet. */
		Long have;
		/** Total needed for the whole goal (all pages sharing the currency), like the home screen. */
		long need;
		/** Item whose icon stands for the currency, or null. */
		Integer iconItemId;

		public boolean isDone()
		{
			return have != null && have >= need;
		}
	}

	Activity activity;
	/** Goal items on this page not obtained yet. */
	int left;
	/** Of those, the ones the current balances already pay for. */
	int ready;
	List<Bar> bars;

	/**
	 * The progress for a page, or null when there is nothing to show: nothing from the page in the
	 * goal and no extra goal for its currencies.
	 */
	public static PageProgress of(PanelModel model, Activity activity)
	{
		return of(model, activity, Collections.emptySet());
	}

	/**
	 * The progress limited to some currencies (empty for all): in the Mining Guild, the
	 * Miscellaneous page only counts the items bought with unidentified minerals.
	 */
	public static PageProgress of(PanelModel model, Activity activity, Set<String> only)
	{
		ActivityPlan page = model.getPlan().getActivity(activity.getId());
		List<String> goalItems = new ArrayList<>();
		for (String item : activity.getClogItems())
		{
			Reward reward = model.getData().getReward(item);
			if (reward != null && model.getWanted().contains(item) && !model.getOwned().contains(item)
				&& (only.isEmpty() || paidWith(reward, only)))
			{
				goalItems.add(item);
			}
		}

		// the page's own needs, plus its currencies that have an extra goal (e.g. sawmill vouchers)
		Set<String> currencies = new LinkedHashSet<>();
		if (!goalItems.isEmpty())
		{
			page.getCost().getNet().forEach((currency, amount) ->
			{
				if (amount > 0)
				{
					currencies.add(currency);
				}
			});
		}
		for (String currency : activity.getCurrencies() == null ? Collections.<String>emptyList() : activity.getCurrencies())
		{
			if (model.getExtraGoals().getOrDefault(currency, 0L) > 0)
			{
				currencies.add(currency);
			}
		}

		List<Bar> bars = new ArrayList<>();
		for (String id : currencies)
		{
			long need = model.getPlan().getTotal().getNet(id);
			if (need <= 0 || (!only.isEmpty() && !only.contains(id)))
			{
				continue;
			}
			Currency currency = model.getData().getCurrencies().get(id);
			bars.add(new Bar(id, currency == null ? id : currency.getName(), model.getBalances().get(id), need,
				currency == null ? null : currency.getItemId()));
		}
		if (goalItems.isEmpty() && bars.isEmpty())
		{
			return null;
		}
		return new PageProgress(activity, goalItems.size(), readyCount(model, goalItems), bars);
	}

	/**
	 * How many goal items the current balances buy one after another: each purchase spends its
	 * price and, when the item is sold back after logging it, gets its refund back. Items that cost
	 * the least for good go first, which buys the most. A set counts once.
	 */
	static int readyCount(PanelModel model, List<String> goalItems)
	{
		List<Map<String, Integer>> prices = new ArrayList<>();
		List<Map<String, Long>> refunds = new ArrayList<>();
		Set<String> sets = new HashSet<>();
		for (String item : goalItems)
		{
			Reward reward = model.getData().getReward(item);
			if (!reward.isCostable() || (reward.getSet() != null && !sets.add(reward.getSet())))
			{
				continue;
			}
			Reward.Offer offer = chosenOffer(model, item, reward);
			if (offer == null)
			{
				continue;
			}
			prices.add(offer.costFor(model.isKaramjaGloves()));
			refunds.add(model.getSellBack().contains(item)
				? PlannerCalculator.sellBackValue(reward, offer, model.getAccountMode(), model.isKaramjaGloves())
				: Collections.emptyMap());
		}

		Map<String, Long> balances = new HashMap<>(model.getBalances());
		int ready = 0;
		while (true)
		{
			int best = -1;
			double bestSpend = Double.MAX_VALUE;
			for (int i = 0; i < prices.size(); i++)
			{
				if (prices.get(i) == null || !affordable(balances, prices.get(i)))
				{
					continue;
				}
				double spend = spendShare(balances, prices.get(i), refunds.get(i));
				if (spend < bestSpend)
				{
					best = i;
					bestSpend = spend;
				}
			}
			if (best < 0)
			{
				return ready;
			}
			for (Map.Entry<String, Integer> price : prices.get(best).entrySet())
			{
				long back = refunds.get(best).getOrDefault(price.getKey(), 0L);
				balances.merge(price.getKey(), back - price.getValue(), Long::sum);
			}
			prices.set(best, null);
			ready++;
		}
	}

	/** What a purchase costs for good, as a share of the balance it comes out of (the worst currency). */
	private static double spendShare(Map<String, Long> balances, Map<String, Integer> price, Map<String, Long> refund)
	{
		double worst = 0;
		for (Map.Entry<String, Integer> entry : price.entrySet())
		{
			long spent = entry.getValue() - refund.getOrDefault(entry.getKey(), 0L);
			long have = Math.max(1, balances.getOrDefault(entry.getKey(), 0L));
			worst = Math.max(worst, (double) spent / have);
		}
		return worst;
	}

	/** Whether any of the item's prices, or its milestone counter, uses one of these currencies. */
	private static boolean paidWith(Reward reward, Set<String> currencies)
	{
		if (reward.getMilestone() != null && currencies.contains(reward.getMilestone().getCounter()))
		{
			return true;
		}
		return reward.getOffers().stream().anyMatch(o -> o.getCost().keySet().stream().anyMatch(currencies::contains));
	}

	private static Reward.Offer chosenOffer(PanelModel model, String item, Reward reward)
	{
		if (reward.getOffers().size() == 1)
		{
			return reward.getOffers().get(0);
		}
		String preferred = model.getPreferredActivity().get(item);
		return reward.getOffers().stream().filter(o -> o.getActivities().contains(preferred)).findFirst().orElse(null);
	}

	private static boolean affordable(Map<String, Long> balances, Map<String, Integer> cost)
	{
		for (Map.Entry<String, Integer> price : cost.entrySet())
		{
			Long have = balances.get(price.getKey());
			if (have == null || have < price.getValue())
			{
				return false;
			}
		}
		return true;
	}
}
