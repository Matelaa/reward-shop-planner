package com.rewardshopplanner.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * A collection log slot that can be planned: bought, crafted, rolled for or unlocked by a milestone.
 */
@Getter
public class Reward
{
	public enum Type
	{
		/** Bought outright with the offer's currencies. */
		SHOP,
		/** Bought by trading in the items listed in {@link #consumes}. */
		UPGRADE,
		/** Made by the player from a currency-like material (e.g. pheasant tail feathers). */
		CRAFT,
		/** Rolled for with points; no fixed cost. */
		RANDOM,
		/** Unlocked by reaching a count; see {@link #milestone}. */
		MILESTONE
	}

	@Getter
	public static class Offer
	{
		/** Activities whose store sells the item at this price. */
		private List<String> activities;
		private String store;
		private Map<String, Integer> cost;
		/** What this store pays when the item is sold back to it, by currency; absent when it doesn't buy it. */
		private Map<String, Integer> buyBack;
		/** TzHaar stores: the price while wearing Karamja gloves (about 13% cheaper). */
		private Map<String, Integer> karamjaGlovesCost;
		/** TzHaar stores: what they pay back while wearing Karamja gloves (about 2.3x more). */
		private Map<String, Integer> karamjaGlovesBuyBack;

		/** The price the player pays, with or without Karamja gloves. */
		public Map<String, Integer> costFor(boolean karamjaGloves)
		{
			return karamjaGloves && karamjaGlovesCost != null ? karamjaGlovesCost : cost;
		}

		/** What the store pays back, with or without Karamja gloves; null when it doesn't buy it. */
		public Map<String, Integer> buyBackFor(boolean karamjaGloves)
		{
			return karamjaGloves && karamjaGlovesBuyBack != null ? karamjaGlovesBuyBack : buyBack;
		}
	}

	@Getter
	public static class Refund
	{
		/** Fraction of the cost (currencies and materials) returned when sold back. */
		private Double rate;
		/** Currencies returned when sold back, instead of a rate. */
		private Map<String, Integer> fixed;
		/** Ultimate ironmen cannot sell the item back. */
		private boolean notForUim;
		/** Only ironmen can sell the item back. */
		private boolean onlyIron;
	}

	@Getter
	public static class Milestone
	{
		private String counter;
		private int amount;
	}

	private String name;
	private Integer itemId;
	private List<String> clogPages;
	private Type type;
	private List<Offer> offers;
	/** Items traded in when buying this one. */
	private List<String> consumes;
	/** Non-currency materials (logs, bars...) spent on the purchase. */
	private Map<String, Integer> materials;
	private List<String> requirements;
	private Refund refund;
	/** Items sharing a set are bought together for a single cost. */
	private String set;
	private Milestone milestone;
	private String notes;

	public List<Offer> getOffers()
	{
		return offers == null ? Collections.emptyList() : offers;
	}

	public List<String> getConsumes()
	{
		return consumes == null ? Collections.emptyList() : consumes;
	}

	public Map<String, Integer> getMaterials()
	{
		return materials == null ? Collections.emptyMap() : materials;
	}

	public List<String> getClogPages()
	{
		return clogPages == null ? Collections.emptyList() : clogPages;
	}

	/** Whether the slot has a fixed price the planner can add up. */
	public boolean isCostable()
	{
		return type != Type.RANDOM && type != Type.MILESTONE && !getOffers().isEmpty();
	}
}
