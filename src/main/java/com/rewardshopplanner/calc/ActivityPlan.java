package com.rewardshopplanner.calc;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * What is left to finish one collection log page.
 */
@Getter
public class ActivityPlan
{
	private final String activityId;
	/** Plannable slots on the page (bought, crafted or unlocked); drop-only slots are not counted. */
	int slotsTotal;
	int slotsOwned;
	/** Wanted, missing slots on this page that have a price. */
	final List<String> missing = new ArrayList<>();
	/** Items from other pages that must be bought first because an upgrade consumes them. */
	final List<String> prerequisites = new ArrayList<>();
	/** Missing slots obtained by rolling with points. */
	final List<String> random = new ArrayList<>();
	/** Missing slots unlocked by reaching a count. */
	final List<String> milestones = new ArrayList<>();
	/** Items sold by more than one shop, left out of the cost until the player picks a shop. */
	final List<String> undecided = new ArrayList<>();
	CostSheet cost = new CostSheet();

	ActivityPlan(String activityId)
	{
		this.activityId = activityId;
	}

	public boolean isComplete()
	{
		return slotsOwned >= slotsTotal;
	}
}
