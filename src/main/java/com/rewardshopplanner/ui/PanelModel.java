package com.rewardshopplanner.ui;

import com.rewardshopplanner.calc.AccountMode;
import com.rewardshopplanner.calc.Plan;
import com.rewardshopplanner.data.RewardData;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Immutable snapshot handed from the plugin to the panel on each refresh.
 */
@Value
public class PanelModel
{
	RewardData data;
	Plan plan;
	/** Known balance per currency id; missing when unknown. */
	Map<String, Long> balances;
	/** Owned slots, with the player's corrections applied. */
	Set<String> owned;
	/** Slots whose owned state was set by the player rather than read from the log. */
	Set<String> ownedEdited;
	Set<String> syncedPages;
	/** Slots in the goal. */
	Set<String> wanted;
	Map<String, String> preferredActivity;
	Map<String, Long> extraGoals;
	Map<String, Long> manualBalances;
	AccountMode accountMode;
	/** Show amounts after selling items back. */
	boolean net;
	boolean hideCompleted;
}
