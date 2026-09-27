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

	/** Items the player will sell back to their shop once the slot is logged. */
	@Builder.Default
	private final Set<String> sellBack = Collections.emptySet();

	/** The player wears Karamja gloves at TzHaar: cheaper prices and better sell-backs. */
	@Builder.Default
	private final boolean karamjaGloves = false;

	public boolean isSellBack(String item)
	{
		return sellBack.contains(item);
	}

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
