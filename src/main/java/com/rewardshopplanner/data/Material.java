package com.rewardshopplanner.data;

import lombok.Getter;

/**
 * An item spent alongside the currencies (logs, bars...), counted in the bank and inventory.
 */
@Getter
public class Material
{
	private String name;
	private int itemId;
}
