package com.rewardshopplanner.tracking;

import com.rewardshopplanner.data.Activity;
import com.rewardshopplanner.data.RewardData;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Maps what the in-game collection log shows onto the item names used by the bundled data.
 */
public class CollectionLogMatcher
{
	/** One slot as drawn by the collection log interface. */
	@Value
	public static class Slot
	{
		int itemId;
		String name;
		boolean obtained;
	}

	private final RewardData data;

	public CollectionLogMatcher(RewardData data)
	{
		this.data = data;
	}

	public Activity activityForPage(String pageTitle)
	{
		for (Activity activity : data.getActivities().values())
		{
			if (activity.getClogPage().equalsIgnoreCase(pageTitle))
			{
				return activity;
			}
		}
		return null;
	}

	/**
	 * Resolves each slot of a page to a data item name, returning name -> obtained.
	 * Matching runs in passes so a weaker rule never takes an entry a stronger one would claim:
	 * item id, then item name (an item can have several ids, e.g. the Volcanic Mine prospector
	 * kit), then position when the page has the expected number of slots.
	 */
	public Map<String, Boolean> match(Activity activity, List<Slot> slots)
	{
		List<String> names = activity.getClogItems();
		int[] ids = activity.getClogItemIds();
		boolean[] used = new boolean[names.size()];
		int[] matched = new int[slots.size()];
		java.util.Arrays.fill(matched, -1);

		// 1. item id
		for (int i = 0; i < slots.size(); i++)
		{
			for (int j = 0; ids != null && j < ids.length; j++)
			{
				if (!used[j] && ids[j] != 0 && ids[j] == slots.get(i).getItemId())
				{
					used[j] = true;
					matched[i] = j;
					break;
				}
			}
		}
		// 2. exact name on this page
		for (int i = 0; i < slots.size(); i++)
		{
			String name = slots.get(i).getName();
			for (int j = 0; matched[i] < 0 && name != null && j < names.size(); j++)
			{
				if (!used[j] && names.get(j).equalsIgnoreCase(name))
				{
					used[j] = true;
					matched[i] = j;
				}
			}
		}
		// 3. position, when the page has the expected shape
		if (slots.size() == names.size())
		{
			for (int i = 0; i < slots.size(); i++)
			{
				if (matched[i] < 0 && !used[i])
				{
					used[i] = true;
					matched[i] = i;
				}
			}
		}

		Map<String, Boolean> result = new LinkedHashMap<>();
		for (int i = 0; i < slots.size(); i++)
		{
			if (matched[i] >= 0)
			{
				result.put(names.get(matched[i]), slots.get(i).isObtained());
			}
		}
		return result;
	}

	/**
	 * Resolves the item named in a "New item added to your collection log" message.
	 * In-game names drop the wiki's disambiguation ("Graceful hood" vs "Graceful hood (Varlamore)"),
	 * so ambiguous names return null and wait for the next page sync.
	 */
	public String resolveChatName(String itemName)
	{
		Set<String> candidates = new LinkedHashSet<>();
		for (Activity activity : data.getActivities().values())
		{
			for (String name : activity.getClogItems())
			{
				if (name.equalsIgnoreCase(itemName) || name.toLowerCase().startsWith(itemName.toLowerCase() + " ("))
				{
					candidates.add(name);
				}
			}
		}
		return candidates.size() == 1 ? candidates.iterator().next() : null;
	}
}
