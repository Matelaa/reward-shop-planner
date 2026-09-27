package com.rewardshopplanner.tracking;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads chompy bird kill counts from game text (tags already removed).
 */
public final class ChompyKillParser
{
	/**
	 * "Check kills" on an ogre bow: a dialog box says "You've killed a total of N chompy birds"
	 * and the chat says "You've scratched up a total of N chompy bird kills".
	 */
	private static final Pattern TOTAL = Pattern.compile(
		"You've (?:killed a total of ([\\d,]+) chompy birds|scratched up a total of ([\\d,]+) chompy bird kills)");
	/** Chat message for each kill. */
	private static final String NOTCH = "You scratch a notch on your bow for the chompy bird kill.";

	private ChompyKillParser()
	{
	}

	/** The total from a "Check kills" message, or null when the text is not one. */
	public static Long parseTotal(String text)
	{
		Matcher m = TOTAL.matcher(text);
		if (!m.find())
		{
			return null;
		}
		String number = m.group(1) != null ? m.group(1) : m.group(2);
		return Long.parseLong(number.replace(",", ""));
	}

	public static boolean isKill(String text)
	{
		return NOTCH.equals(text.trim());
	}
}
