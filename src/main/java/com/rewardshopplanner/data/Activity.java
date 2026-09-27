package com.rewardshopplanner.data;

import java.util.List;
import java.util.Map;
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
	private String notes;
}
