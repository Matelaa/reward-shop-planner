package com.rewardshopplanner.calc;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

/**
 * Everything the planner needs to know about the player.
 */
@Getter
@Builder
public class PlannerInput
{
	/** Collection log slots already obtained, by item name. */
	@Builder.Default
	private final Set<String> owned = Collections.emptySet();

	/**
	 * Slots the player wants: the goal. Null plans every missing slot (used for whole-page totals).
	 */
	@Builder.Default
	private final Set<String> wanted = null;

	/** Current amount of each currency, by currency id. */
	@Builder.Default
	private final Map<String, Long> balances = Collections.emptyMap();

	/** Extra currency targets outside the log (e.g. sawmill vouchers), by currency id. */
	@Builder.Default
	private final Map<String, Long> extraGoals = Collections.emptyMap();

	/**
	 * Shop picked by the player for items sold in more than one place (item name -> activity id).
	 * Items without a pick are left out of the costs; there is no default.
	 */
	@Builder.Default
	private final Map<String, String> preferredActivity = Collections.emptyMap();

	@Builder.Default
	private final AccountMode accountMode = AccountMode.MAIN;

	/** Count items that can be sold back to their shop at their net cost. */
	@Builder.Default
	private final boolean applyRefunds = true;

	public boolean isOwned(String item)
	{
		return owned.contains(item);
	}

	public boolean isWanted(String item)
	{
		return wanted == null || wanted.contains(item);
	}

	public long getBalance(String currency)
	{
		return balances.getOrDefault(currency, 0L);
	}
}
