package com.rewardshopplanner.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChompyKillParserTest
{
	@Test
	public void readsTheChatTotal()
	{
		assertEquals(Long.valueOf(1001), ChompyKillParser.parseTotal("You've scratched up a total of 1001 chompy bird kills so far!"));
		assertEquals(Long.valueOf(4000), ChompyKillParser.parseTotal("You've scratched up a total of 4,000 chompy bird kills so far!"));
	}

	@Test
	public void readsTheDialogTotal()
	{
		// dialog text after removing colour tags and line breaks
		assertEquals(Long.valueOf(1001), ChompyKillParser.parseTotal("You've killed a total of 1001 chompy birds so far! ~ You're an Ogre Expert! ~"));
	}

	@Test
	public void recognisesEachKill()
	{
		assertTrue(ChompyKillParser.isKill("You scratch a notch on your bow for the chompy bird kill."));
		assertFalse(ChompyKillParser.isKill("~ You're an Ogre Expert! ~"));
		assertNull(ChompyKillParser.parseTotal("You scratch a notch on your bow for the chompy bird kill."));
	}
}
