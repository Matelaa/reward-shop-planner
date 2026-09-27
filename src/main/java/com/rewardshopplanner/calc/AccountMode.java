package com.rewardshopplanner.calc;

public enum AccountMode
{
	MAIN,
	IRONMAN,
	ULTIMATE_IRONMAN;

	public boolean isIron()
	{
		return this != MAIN;
	}
}
