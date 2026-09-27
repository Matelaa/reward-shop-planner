package com.rewardshopplanner.calc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Currency and material totals, both gross (paid up front) and net (after selling items back).
 */
public class CostSheet
{
	private final Map<String, Long> gross = new LinkedHashMap<>();
	private final Map<String, Long> net = new LinkedHashMap<>();
	private final Map<String, Long> materialsGross = new LinkedHashMap<>();
	private final Map<String, Long> materialsNet = new LinkedHashMap<>();

	void addCurrency(String currency, long grossAmount, long netAmount)
	{
		gross.merge(currency, grossAmount, Long::sum);
		net.merge(currency, netAmount, Long::sum);
	}

	/** A count to reach (wins, kills): several milestones need the highest one, not the sum. */
	void addTarget(String counter, long amount)
	{
		gross.merge(counter, amount, Math::max);
		net.merge(counter, amount, Math::max);
	}

	/** The net can't be below the balance needed to afford the purchases before reselling them. */
	void raiseNet(String currency, long atLeast)
	{
		net.merge(currency, atLeast, Math::max);
	}

	void addMaterial(String material, long grossAmount, long netAmount)
	{
		materialsGross.merge(material, grossAmount, Long::sum);
		materialsNet.merge(material, netAmount, Long::sum);
	}

	public long getGross(String currency)
	{
		return gross.getOrDefault(currency, 0L);
	}

	public long getNet(String currency)
	{
		return net.getOrDefault(currency, 0L);
	}

	public long getMaterialGross(String material)
	{
		return materialsGross.getOrDefault(material, 0L);
	}

	public long getMaterialNet(String material)
	{
		return materialsNet.getOrDefault(material, 0L);
	}

	public Map<String, Long> getGross()
	{
		return Collections.unmodifiableMap(gross);
	}

	public Map<String, Long> getNet()
	{
		return Collections.unmodifiableMap(net);
	}

	public Map<String, Long> getMaterialsGross()
	{
		return Collections.unmodifiableMap(materialsGross);
	}

	public Map<String, Long> getMaterialsNet()
	{
		return Collections.unmodifiableMap(materialsNet);
	}
}
