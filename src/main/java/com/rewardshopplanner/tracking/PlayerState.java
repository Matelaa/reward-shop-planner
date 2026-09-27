package com.rewardshopplanner.tracking;

import com.rewardshopplanner.data.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * Everything remembered about one character, saved to its RuneLite profile as JSON.
 */
@Getter
public class PlayerState
{
	/** Collection log slots obtained according to the game, by item name as used in the bundled data. */
	private Set<String> owned = new LinkedHashSet<>();
	/** Player corrections to {@link #owned} (item -> owned); dropped when the log is read again. */
	private Map<String, Boolean> ownedOverrides = new HashMap<>();
	/** Slots the player wants to get: the goal. */
	private Set<String> wanted = new LinkedHashSet<>();
	/** Collection log pages read from the game at least once. */
	private Set<String> syncedPages = new LinkedHashSet<>();
	/** Item currencies in the bank the last time it was open, by currency id. */
	private Map<String, Long> bankItems = new HashMap<>();
	/** Item currencies in the inventory, by currency id. */
	private Map<String, Long> inventoryItems = new HashMap<>();
	/** Item currencies stored in the Forestry kit (e.g. anima-infused bark), by currency id. */
	private Map<String, Long> forestryKitItems = new HashMap<>();
	/** Currencies read from game vars, by currency id. */
	private Map<String, Long> varBalances = new HashMap<>();
	/** Balances typed by the player; take priority over tracked values. */
	private Map<String, Long> manualBalances = new HashMap<>();
	/** Shop picked for items sold in more than one place (item name -> activity id). */
	private Map<String, String> preferredActivity = new HashMap<>();
	private Map<String, Long> extraGoals = new LinkedHashMap<>();

	public PlayerState()
	{
	}

	/** Owned slots after applying the player's corrections. */
	public Set<String> effectiveOwned()
	{
		Set<String> result = new LinkedHashSet<>(owned);
		for (Map.Entry<String, Boolean> override : ownedOverrides.entrySet())
		{
			if (override.getValue())
			{
				result.add(override.getKey());
			}
			else
			{
				result.remove(override.getKey());
			}
		}
		return result;
	}

	/**
	 * Records what the collection log says about a slot. The log is the source of truth, so any
	 * earlier correction by the player for that slot is dropped.
	 */
	public void setLogOwned(String item, boolean obtained)
	{
		if (obtained)
		{
			owned.add(item);
		}
		else
		{
			owned.remove(item);
		}
		ownedOverrides.remove(item);
	}

	/** The player marks a slot as owned or not; stored only when it differs from the log. */
	public void setOwnedByPlayer(String item, boolean ownedNow)
	{
		if (owned.contains(item) == ownedNow)
		{
			ownedOverrides.remove(item);
		}
		else
		{
			ownedOverrides.put(item, ownedNow);
		}
	}

	/**
	 * Current amount of a currency, or null when nothing is known about it.
	 */
	public Long balanceOf(Currency currency)
	{
		Long manual = manualBalances.get(currency.getId());
		if (manual != null)
		{
			return manual;
		}
		switch (currency.getSource())
		{
			case ITEM:
				Long bank = bankItems.get(currency.getId());
				Long inventory = inventoryItems.get(currency.getId());
				// older saved states have no kit map
				Long kit = forestryKitItems == null ? null : forestryKitItems.get(currency.getId());
				if (bank == null && inventory == null && kit == null)
				{
					return null;
				}
				return (bank == null ? 0 : bank) + (inventory == null ? 0 : inventory) + (kit == null ? 0 : kit);
			case VARP:
			case VARBIT:
			case VARBIT_COMPOSITE:
				return varBalances.get(currency.getId());
			default:
				return null;
		}
	}

	/** Value of a {@link Currency.Source#VARBIT_COMPOSITE} currency from its two varbits. */
	public static long compositeValue(Currency currency, int base, int extra)
	{
		return base + (long) currency.getCompositeFactor() * extra;
	}

	/** Whether the plugin can keep this currency up to date by itself. */
	public static boolean isTracked(Currency currency)
	{
		switch (currency.getSource())
		{
			case ITEM:
				return currency.getItemId() != null;
			case VARP:
			case VARBIT:
				return currency.getVarId() != null;
			case VARBIT_COMPOSITE:
				return currency.getVarIds() != null && currency.getVarIds().length == 2 && currency.getCompositeFactor() != null;
			default:
				return false;
		}
	}
}
