package com.rewardshopplanner.tracking;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads balances that game interfaces show as text (tags already removed). A pattern's first
 * group is the number; build them with {@link #after}, {@link #before} and {@link #first}.
 */
public final class BalanceTextParser
{
	private static final String NUMBER = "(\\d[\\d,]*)";

	/** Mahogany Homes, after each contract; the total is the current Carpenter point balance. */
	public static final Pattern MAHOGANY_CONTRACT =
		Pattern.compile("You have completed [\\d,]+ contracts with a total of " + NUMBER + " points");

	private BalanceTextParser()
	{
	}

	/** "Points: 1,245" or "Telekinetic: 0/30". */
	public static Pattern after(String label)
	{
		return Pattern.compile(Pattern.quote(label) + ":\\s*" + NUMBER);
	}

	/** "123 Enchantment", only when the text also contains {@code context} (to avoid stray matches). */
	public static Pattern before(String label, String context)
	{
		return Pattern.compile(NUMBER + "\\s+" + Pattern.quote(label) + "(?=.*" + Pattern.quote(context) + ")");
	}

	/** The first number in the text, for components that show only the value. */
	public static Pattern first()
	{
		return Pattern.compile(NUMBER);
	}

	/** The number the pattern finds, or null. */
	public static Long read(String text, Pattern pattern)
	{
		Matcher m = pattern.matcher(text);
		return m.find() ? Long.valueOf(m.group(1).replace(",", "")) : null;
	}
}
