package com.rewardshopplanner.calc;

import com.rewardshopplanner.data.Activity;
import com.rewardshopplanner.data.Reward;
import com.rewardshopplanner.data.RewardData;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Works out how much of each currency is still needed to finish the purchasable part of the
 * collection log.
 *
 * <p>Each page's plan answers "what does it take to finish this page", so an item shared by two
 * pages (e.g. the angler outfit) appears in both. The account-wide total counts every item once.
 *
 * <p>When an item is sold by more than one shop (e.g. the prospector kit at the Motherlode Mine or
 * the Volcanic Mine), the player picks the shop. Until they do, the item is reported as undecided
 * and left out of every cost.
 */
public class PlannerCalculator
{
	private final RewardData data;

	public PlannerCalculator(RewardData data)
	{
		this.data = data;
	}

	public Plan plan(PlannerInput input)
	{
		Map<String, ActivityPlan> activities = new LinkedHashMap<>();
		Set<String> allNeeded = new LinkedHashSet<>();
		Set<String> undecided = new LinkedHashSet<>();

		for (Activity activity : data.getActivities().values())
		{
			ActivityPlan plan = planActivity(activity, input);
			activities.put(activity.getId(), plan);
			allNeeded.addAll(plan.missing);
			allNeeded.addAll(plan.prerequisites);
			undecided.addAll(plan.undecided);
		}

		CostSheet total = costOf(allNeeded, input);
		for (ActivityPlan plan : activities.values())
		{
			addMilestoneTargets(plan, total);
		}
		for (Map.Entry<String, Long> goal : input.getExtraGoals().entrySet())
		{
			total.addCurrency(goal.getKey(), goal.getValue(), goal.getValue());
		}

		Map<String, Long> remainingGross = new HashMap<>();
		Map<String, Long> remainingNet = new HashMap<>();
		for (String currency : total.getGross().keySet())
		{
			long balance = input.getBalance(currency);
			remainingGross.put(currency, Math.max(0, total.getGross(currency) - balance));
			remainingNet.put(currency, Math.max(0, total.getNet(currency) - balance));
		}

		return new Plan(activities, total, remainingGross, remainingNet, undecided);
	}

	private ActivityPlan planActivity(Activity activity, PlannerInput input)
	{
		ActivityPlan plan = new ActivityPlan(activity.getId());
		for (String item : activity.getClogItems())
		{
			Reward reward = data.getReward(item);
			if (reward == null)
			{
				continue; // drop-only slot: not part of what the planner can complete
			}
			plan.slotsTotal++;
			if (input.isOwned(item))
			{
				plan.slotsOwned++;
				continue;
			}
			if (!input.isWanted(item))
			{
				continue;
			}

			switch (reward.getType())
			{
				case RANDOM:
					plan.random.add(item);
					break;
				case MILESTONE:
					plan.milestones.add(item);
					break;
				default:
					if (!reward.isCostable())
					{
						break;
					}
					if (chooseOffer(reward, input) == null)
					{
						plan.undecided.add(item);
					}
					else
					{
						plan.missing.add(item);
					}
			}
		}

		// Upgrades trade in other items (Lumberjack -> Forestry, base graceful -> recolour).
		// Anything consumed that the player has never obtained has to be bought first.
		Set<String> needed = new LinkedHashSet<>(plan.missing);
		Deque<String> queue = new ArrayDeque<>(plan.missing);
		while (!queue.isEmpty())
		{
			for (String consumed : data.getReward(queue.poll()).getConsumes())
			{
				Reward prerequisite = data.getReward(consumed);
				if (input.isOwned(consumed) || prerequisite == null || !prerequisite.isCostable() || needed.contains(consumed))
				{
					continue;
				}
				if (chooseOffer(prerequisite, input) == null)
				{
					if (!plan.undecided.contains(consumed))
					{
						plan.undecided.add(consumed);
					}
					continue;
				}
				needed.add(consumed);
				plan.prerequisites.add(consumed);
				queue.add(consumed);
			}
		}

		plan.cost = costOf(needed, input);
		addMilestoneTargets(plan, plan.cost);
		return plan;
	}

	/** Wanted milestone slots add the count they need (highest per counter). */
	private void addMilestoneTargets(ActivityPlan plan, CostSheet sheet)
	{
		for (String item : plan.milestones)
		{
			Reward.Milestone milestone = data.getReward(item).getMilestone();
			if (milestone != null && milestone.getCounter() != null)
			{
				sheet.addTarget(milestone.getCounter(), milestone.getAmount());
			}
		}
	}

	private CostSheet costOf(Collection<String> items, PlannerInput input)
	{
		CostSheet sheet = new CostSheet();
		Set<String> setsPaid = new HashSet<>();
		for (String item : items)
		{
			Reward reward = data.getReward(item);
			if (reward.getSet() != null && !setsPaid.add(reward.getSet()))
			{
				continue; // the whole set was bought with its first piece
			}

			Reward.Offer offer = chooseOffer(reward, input);
			Reward.Refund refund = refundFor(reward, input);
			for (Map.Entry<String, Integer> cost : offer.getCost().entrySet())
			{
				long amount = cost.getValue();
				sheet.addCurrency(cost.getKey(), amount, amount - refunded(refund, cost.getKey(), amount));
			}
			for (Map.Entry<String, Integer> material : reward.getMaterials().entrySet())
			{
				long amount = material.getValue();
				long back = refund != null && refund.getRate() != null ? (long) Math.floor(amount * refund.getRate()) : 0;
				sheet.addMaterial(material.getKey(), amount, amount - back);
			}
		}
		return sheet;
	}

	/**
	 * The offer the player buys from: the only one, or the one from the shop they picked.
	 * Returns null when the item has several shops and none was picked.
	 */
	private static Reward.Offer chooseOffer(Reward reward, PlannerInput input)
	{
		if (reward.getOffers().size() == 1)
		{
			return reward.getOffers().get(0);
		}
		String preferred = input.getPreferredActivity().get(reward.getName());
		if (preferred != null)
		{
			for (Reward.Offer offer : reward.getOffers())
			{
				if (offer.getActivities().contains(preferred))
				{
					return offer;
				}
			}
		}
		return null;
	}

	private static Reward.Refund refundFor(Reward reward, PlannerInput input)
	{
		Reward.Refund refund = reward.getRefund();
		if (refund == null || !input.isApplyRefunds())
		{
			return null;
		}
		if (refund.isNotForUim() && input.getAccountMode() == AccountMode.ULTIMATE_IRONMAN)
		{
			return null;
		}
		if (refund.isOnlyIron() && !input.getAccountMode().isIron())
		{
			return null;
		}
		return refund;
	}

	private static long refunded(Reward.Refund refund, String currency, long amount)
	{
		if (refund == null)
		{
			return 0;
		}
		if (refund.getRate() != null)
		{
			return (long) Math.floor(amount * refund.getRate());
		}
		if (refund.getFixed() != null)
		{
			return Math.min(amount, refund.getFixed().getOrDefault(currency, 0));
		}
		return 0;
	}
}
