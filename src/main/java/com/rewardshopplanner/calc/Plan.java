package com.rewardshopplanner.calc;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * Result of a planner run: per-page plans plus account-wide totals.
 */
@Getter
public class Plan
{
	private final Map<String, ActivityPlan> activities;
	/** Unique missing slots across every page (shared items counted once) plus extra goals. */
	private final CostSheet total;
	private final Map<String, Long> remainingGross;
	private final Map<String, Long> remainingNet;
	/** Items waiting for the player to pick a shop; not included in any cost. */
	private final Set<String> undecided;

	Plan(Map<String, ActivityPlan> activities, CostSheet total, Map<String, Long> remainingGross, Map<String, Long> remainingNet,
		Set<String> undecided)
	{
		this.activities = Collections.unmodifiableMap(activities);
		this.total = total;
		this.remainingGross = Collections.unmodifiableMap(remainingGross);
		this.remainingNet = Collections.unmodifiableMap(remainingNet);
		this.undecided = Collections.unmodifiableSet(undecided);
	}

	public ActivityPlan getActivity(String activityId)
	{
		return activities.get(activityId);
	}

	/** Currency still to earn, counting items sold back after their slot is obtained. */
	public long getRemainingNet(String currency)
	{
		return remainingNet.getOrDefault(currency, 0L);
	}

	/** Currency still to earn if nothing is sold back. */
	public long getRemainingGross(String currency)
	{
		return remainingGross.getOrDefault(currency, 0L);
	}
}
