package com.rewardshopplanner.tracking;

import com.rewardshopplanner.calc.AccountMode;
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
	/** Materials (logs, bars...) in the bank the last time it was open, by name; noted ones included. */
	private Map<String, Long> bankMaterials = new HashMap<>();
	/** Materials in the inventory, by name; noted ones included. */
	private Map<String, Long> inventoryMaterials = new HashMap<>();
	/** Logs in the log basket (shared with the Forestry basket), by name. */
	private Map<String, Long> logBasketMaterials = new HashMap<>();
	/** Currencies read from game vars, by currency id. */
	private Map<String, Long> varBalances = new HashMap<>();
	/**
	 * Balances typed by the player, or read from game text that no var holds (shop screens, chat).
	 * They take priority, until a tracked reading (item, var) for the same currency changes.
	 */
	private Map<String, Long> manualBalances = new HashMap<>();
	/** Shop picked for items sold in more than one place (item name -> activity id). */
	private Map<String, String> preferredActivity = new HashMap<>();
	private Map<String, Long> extraGoals = new LinkedHashMap<>();
	/** Items the player will sell back to their shop once logged; nothing is sold back by default. */
	private Set<String> sellBack = new LinkedHashSet<>();
	/** Whether the Karamja gloves reward was claimed, as last read from the game. */
	private boolean karamjaGlovesClaimed;
	/** The player's own answer to "do you wear Karamja gloves at TzHaar"; null follows the diary. */
	private Boolean karamjaGlovesChoice;
	/** Account type as last read from the game, so it stays right while logged out. */
	private AccountMode accountMode = AccountMode.MAIN;

	public PlayerState()
	{
	}

	public Set<String> getSellBack()
	{
		// older saved states have no sell-back set
		if (sellBack == null)
		{
			sellBack = new LinkedHashSet<>();
		}
		return sellBack;
	}

	public AccountMode getAccountMode()
	{
		// older saved states have no account type
		return accountMode == null ? AccountMode.MAIN : accountMode;
	}

	/** Records the account type read from the game; true when it changed. */
	public boolean setAccountMode(AccountMode mode)
	{
		if (getAccountMode() == mode)
		{
			return false;
		}
		accountMode = mode;
		return true;
	}

	public void setKaramjaGlovesClaimed(boolean claimed)
	{
		karamjaGlovesClaimed = claimed;
	}

	public void setKaramjaGlovesChoice(Boolean choice)
	{
		karamjaGlovesChoice = choice;
	}

	/** TzHaar prices use Karamja gloves: the player's choice, else whether the diary gloves were claimed. */
	public boolean wearsKaramjaGloves()
	{
		return karamjaGlovesChoice != null ? karamjaGlovesChoice : karamjaGlovesClaimed;
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

	public Map<String, Long> getBankMaterials()
	{
		// older saved states have no material maps
		if (bankMaterials == null)
		{
			bankMaterials = new HashMap<>();
		}
		return bankMaterials;
	}

	public Map<String, Long> getInventoryMaterials()
	{
		if (inventoryMaterials == null)
		{
			inventoryMaterials = new HashMap<>();
		}
		return inventoryMaterials;
	}

	public Map<String, Long> getLogBasketMaterials()
	{
		if (logBasketMaterials == null)
		{
			logBasketMaterials = new HashMap<>();
		}
		return logBasketMaterials;
	}

	/** Amount of a material in the bank, inventory and log basket, or null before any was seen. */
	public Long materialOf(String name)
	{
		Long bank = getBankMaterials().get(name);
		Long inventory = getInventoryMaterials().get(name);
		Long basket = getLogBasketMaterials().get(name);
		if (bank == null && inventory == null && basket == null)
		{
			return null;
		}
		return (bank == null ? 0 : bank) + (inventory == null ? 0 : inventory) + (basket == null ? 0 : basket);
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
