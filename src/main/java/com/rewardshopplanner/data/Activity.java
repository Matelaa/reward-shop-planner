package com.rewardshopplanner.data;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * A minigame or other content with its own collection log page.
 */
@Getter
public class Activity
{
	private String id;
	private String name;
	private String clogTab;
	private String clogPage;
	/** Currency ids spent on this page's purchasable slots. */
	private List<String> currencies;
	/** Every item on the collection log page, purchasable or not. */
	private List<String> clogItems;
	/** Item id of each entry in {@link #clogItems}, same order; 0 when unknown. */
	private int[] clogItemIds;
	/** Cost of buying every purchasable slot on the page, as generated (for reference). */
	private Map<String, Integer> totalCost;
	/** Map regions where the on-screen progress shows; empty when it shows after earning instead. */
	private int[] regions;
	/** Lowest plane that counts in those regions (1 keeps Rooftop Agility to the rooftops). */
	private int minPlane;
	/** Finer places than whole regions: polygons walked in game, maybe for some currencies only. */
	private List<Area> areas;
	private String notes;

	/** Whether the activity has a place at all; without one its progress shows after earning instead. */
	public boolean hasPlace()
	{
		return (regions != null && regions.length > 0) || (areas != null && !areas.isEmpty());
	}

	/**
	 * The currencies to show for a player on this tile: null when not at the activity, empty for
	 * all of them, or the ones of the area the player is in.
	 */
	public Set<String> currenciesAt(int regionId, int x, int y, int plane)
	{
		if (regions != null && plane >= minPlane)
		{
			for (int region : regions)
			{
				if (region == regionId)
				{
					return Collections.emptySet();
				}
			}
		}
		Set<String> shown = null;
		for (Area area : areas == null ? Collections.<Area>emptyList() : areas)
		{
			if (!area.contains(regionId, x, y, plane))
			{
				continue;
			}
			if (area.getCurrencies() == null || area.getCurrencies().isEmpty())
			{
				return Collections.emptySet();
			}
			shown = shown == null ? new LinkedHashSet<>() : shown;
			shown.addAll(area.getCurrencies());
		}
		return shown;
	}
}
