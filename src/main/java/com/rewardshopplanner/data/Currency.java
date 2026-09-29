package com.rewardshopplanner.data;

import lombok.Getter;

/**
 * A reward currency: points, tokens or an item spent at a reward shop.
 */
@Getter
public class Currency
{
	public enum Source
	{
		/** Item held in the bank/inventory, identified by {@link #itemId}. */
		ITEM,
		/** VarPlayer identified by {@link #varId}. */
		VARP,
		/** Varbit identified by {@link #varId}. */
		VARBIT,
		/** Value spread over two varbits ({@link #varIds}): base + {@link #compositeFactor} x extra. */
		VARBIT_COMPOSITE,
		/** Storage not mapped yet; the balance has to come from elsewhere. */
		UNKNOWN,
		/** A count that is reached, not spent (wins, kills); used by milestone slots. */
		COUNTER,
		/** Read from a game interface when the player opens it (see {@link #notes}). */
		INTERFACE
	}

	private String id;
	private String name;
	private Source source;
	private Integer itemId;
	private Integer varId;
	private int[] varIds;
	private Integer compositeFactor;
	private String gameval;
	private String notes;
	/** For currencies that aren't items: an item whose icon stands for it (Mox paste for mox resin). */
	private Integer iconItemId;
	/** Or a game sprite, like a spell icon (Telekinetic Grab for telekinetic pizazz). */
	private Integer iconSpriteId;

	/** The item whose icon shows the currency: the currency itself, or its stand-in; null for a sprite or none. */
	public Integer getIconItem()
	{
		return itemId != null ? itemId : iconItemId;
	}
}
