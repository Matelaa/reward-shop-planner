package com.rewardshopplanner.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.gson.Gson;
import com.rewardshopplanner.calc.AccountMode;
import com.rewardshopplanner.calc.Plan;
import com.rewardshopplanner.calc.PlannerCalculator;
import com.rewardshopplanner.RewardShopPlannerConfig;
import com.rewardshopplanner.calc.PlannerInput;
import com.rewardshopplanner.data.RewardData;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import org.junit.Assume;
import org.junit.Test;

/**
 * Renders the README / Plugin Hub images and demo GIF from the real panel with SIMULATED data.
 * Skipped unless RENDER_MARKETING=1 is set. Needs the icons from scripts/fetch-preview-icons.ps1.
 *
 * <pre>
 * powershell -ExecutionPolicy Bypass -File scripts/fetch-preview-icons.ps1
 * set RENDER_MARKETING=1 &amp;&amp; gradlew test --tests *MarketingRenderer
 * </pre>
 */
public class MarketingRenderer
{
	private static final int PANEL_WIDTH = 225;
	private static final int CONTENT_WIDTH = 209;
	private static final int FRAME_HEIGHT = 400;
	private static final int SCALE = 2;
	/** Name of the page whose dropdown is open, and how far the view is scrolled to show it. */
	private static String focused;
	private static int viewTop;
	private static final File OUT = new File("docs/marketing");

	private RewardData data;
	private final Set<String> owned = new LinkedHashSet<>(List.of(
		"Lumberjack hat", "Lumberjack top", "Lumberjack legs", "Lumberjack boots", "Log basket", "Twitcher's gloves",
		"Coal bag", "Decorative helm (red)", "Decorative sword (red)", "Decorative shield (red)",
		"Void knight top", "Void knight robe", "Void knight gloves"));
	private final Set<String> wanted = new LinkedHashSet<>();
	private final Set<String> sellBack = new LinkedHashSet<>();
	private final Map<String, String> preferred = new HashMap<>();
	private boolean gloves;
	// simulated bank: some logs ready, some still to cut
	private final Map<String, Long> materials = Map.of(
		"Oak logs", 1_200L, "Willow logs", 900L, "Teak logs", 740L, "Maple logs", 1_340L, "Mahogany logs", 510L,
		"Arctic pine logs", 120L, "Yew logs", 1_100L, "Magic logs", 260L, "Redwood logs", 0L, "Thread", 12L);
	private final Map<String, Long> balances = Map.ofEntries(
		Map.entry("anima_bark", 5_320L), Map.entry("pheasant_feather", 9L), Map.entry("golden_nugget", 150L),
		Map.entry("pc_points", 135L), Map.entry("cw_ticket", 74L), Map.entry("termites", 410L), Map.entry("tokkul", 120_000L),
		Map.entry("coins", 48_500L), Map.entry("ba_attacker", 1_240L), Map.entry("ba_collector", 860L),
		Map.entry("ba_healer", 1_510L), Map.entry("ba_defender", 420L));

	@Test
	public void render() throws Exception
	{
		Assume.assumeTrue("set RENDER_MARKETING=1 to render", "1".equals(System.getenv("RENDER_MARKETING")));
		data = RewardData.load(new Gson());
		OUT.mkdirs();
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				UIManager.setLookAndFeel(new FlatDarkLaf());
				renderAll();
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
	}

	private void renderAll() throws Exception
	{
		PlannerPanel panel = new PlannerPanel(null, ICONS);
		List<BufferedImage> frames = new ArrayList<>();
		List<Integer> delays = new ArrayList<>();

		// 1. fresh start
		panel.update(model());
		add(frames, delays, snapshot(panel, null), 1800);

		// 2. search for the page
		for (String text : new String[]{"f", "fo", "for"})
		{
			panel.setSearchText(text);
			add(frames, delays, snapshot(panel, null), 350);
		}
		add(frames, delays, snapshot(panel, null), 700);

		// 2b. a clogger ticks a whole page: all its missing slots join the goal and it moves up to Tracking
		panel.setSearchText("");
		scrollTo("EVERYTHING MISSING");
		add(frames, delays, snapshot(panel, null), 1200);
		add(frames, delays, snapshot(panel, trackBoxCenter(panel, "Barbarian Assault")), 900);
		List<String> assault = data.getActivities().get("barbarian_assault").getClogItems().stream()
			.filter(i -> data.getReward(i) != null && !owned.contains(i))
			.collect(Collectors.toList());
		wanted.addAll(assault);
		panel.update(model());
		scrollTo("TRACKING");
		add(frames, delays, snapshot(panel, null), 2000);
		// the rest of the tour keeps Barbarian Assault out of the goal
		wanted.removeAll(assault);

		// 3. open Forestry and pick items
		panel.setSearchText("");
		focus(panel, "forestry");
		add(frames, delays, snapshot(panel, null), 1200);
		for (String item : new String[]{"Forestry hat", "Forestry top", "Forestry legs", "Forestry boots", "Funky shaped log", "Pheasant hat"})
		{
			wanted.add(item);
			panel.update(model());
			// after the update: the first pick adds the goal bars and moves the grid down
			add(frames, delays, snapshot(panel, slotCenter(panel, item)), 650);
		}
		add(frames, delays, snapshot(panel, null), 1400);
		BufferedImage forestryStill = snapshot(panel, null);
		BufferedImage materialsStill = withLabelTooltip(snapshot(panel, null, 470), panel, "Materials");

		// 4. an item sold in two shops
		focus(panel, "motherlode_mine");
		wanted.add("Gem bag");
		wanted.add("Prospector helmet");
		panel.update(model());
		Point helmet = slotCenter(panel, "Prospector helmet");
		add(frames, delays, snapshot(panel, helmet), 1200);
		BufferedImage menu = withMenu(snapshot(panel, helmet), helmet, new String[]{"Remove from goal", "I already have this", "Buy from:",
			"◉ Motherlode Mine (40 Golden nugget)", "○ Volcanic Mine (26,000 VM points)"}, 3, 2);
		add(frames, delays, menu, 1600);
		preferred.put("Prospector helmet", "motherlode_mine");
		panel.update(model());
		add(frames, delays, snapshot(panel, null), 1200);
		BufferedImage shopStill = menu;

		// 4b. a recoloured set: one click picks all six pieces
		owned.addAll(List.of("Graceful hood", "Graceful top", "Graceful legs", "Graceful gloves", "Graceful boots", "Graceful cape"));
		focus(panel, "colossal_wyrm");
		panel.update(model());
		add(frames, delays, snapshot(panel, null), 1000);
		for (String piece : List.of("Graceful hood (Varlamore)", "Graceful top (Varlamore)", "Graceful legs (Varlamore)",
			"Graceful gloves (Varlamore)", "Graceful boots (Varlamore)", "Graceful cape (Varlamore)"))
		{
			wanted.add(piece);
		}
		panel.update(model());
		Point hood = slotCenter(panel, "Graceful hood (Varlamore)");
		add(frames, delays, snapshot(panel, hood), 1400);
		BufferedImage setStill = withTooltip(snapshot(panel, hood), panel, "Graceful hood (Varlamore)");
		add(frames, delays, setStill, 2600);

		// 4c. sell-backs are opt-in: right-click the funky shaped log, sell it back (80%), the goal drops
		focus(panel, "forestry");
		wanted.add("Cape pouch");
		panel.update(model());
		Point funky = slotCenter(panel, "Funky shaped log");
		add(frames, delays, snapshot(panel, funky), 1000);
		add(frames, delays, withMenu(snapshot(panel, funky), funky,
			new String[]{"Remove from goal", "I already have this", "☐ Sell back after logging it"}, 2, -1), 1700);
		sellBack.add("Funky shaped log");
		panel.update(model());
		add(frames, delays, snapshot(panel, null), 1500);
		sellBack.add("Cape pouch");
		panel.update(model());
		// the sell-back line moved the grid down
		funky = slotCenter(panel, "Funky shaped log");
		add(frames, delays, withTooltip(snapshot(panel, funky), panel, "Funky shaped log"), 2800);
		// taller still so the tooltip fits below the item and the "back from sell-backs" line stays visible
		BufferedImage sellBackStill = withTooltip(snapshot(panel, funky, 540), panel, "Funky shaped log");

		// 4d. Castle Wars refunds in full: "sell back all" leaves only the dearest item's price
		focus(panel, "castle_wars");
		for (String item : data.getActivities().get("castle_wars").getClogItems())
		{
			if (!owned.contains(item) && data.getReward(item) != null)
			{
				wanted.add(item);
			}
		}
		panel.update(model());
		Point sellAll = linkCenter(panel, "sell back all");
		add(frames, delays, snapshot(panel, null), 1400);
		add(frames, delays, snapshot(panel, sellAll), 900);
		for (String item : data.getActivities().get("castle_wars").getClogItems())
		{
			if (wanted.contains(item))
			{
				sellBack.add(item);
			}
		}
		panel.update(model());
		add(frames, delays, snapshot(panel, null), 2600);
		BufferedImage castleWarsStill = snapshot(panel, null);
		// keep the home screen readable: only a couple of Castle Wars items stay in the goal
		wanted.removeAll(data.getActivities().get("castle_wars").getClogItems());

		// 5. back home: the combined goal
		wanted.add("Decorative helm (white)");
		wanted.add("Decorative armour (white platebody)");
		wanted.add("Void mage helm");
		focus(panel, null);
		panel.update(model());
		BufferedImage home = snapshot(panel, null);
		BufferedImage homeMaterials = withLabelTooltip(snapshot(panel, null, 470), panel, "Materials");
		add(frames, delays, home, 2200);
		panel.setGoalListOpen(true);
		BufferedImage goalsStill = snapshot(panel, null);
		add(frames, delays, goalsStill, 2600);
		panel.setGoalListOpen(false);
		// the page list: tracked pages first, ticked when every missing slot is in the goal (Barbarian Assault)
		// and half-ticked when only some are
		wanted.addAll(assault);
		panel.update(model());
		scrollTo("TRACKING");
		BufferedImage trackingStill = snapshot(panel, null);
		add(frames, delays, trackingStill, 2400);
		scrollTo(null);

		// 6. TzHaar: the obsidian armour and cape, sold back, without and with Karamja gloves (the tooltip sits below the grid)
		List<String> obsidian = List.of("Obsidian cape", "Obsidian helmet", "Obsidian platebody", "Obsidian platelegs");
		wanted.addAll(obsidian);
		sellBack.addAll(obsidian);
		focus(panel, "tzhaar");
		panel.update(model());
		BufferedImage bare = withTooltip(snapshot(panel, null, 372), panel, "Obsidian platebody");
		gloves = true;
		panel.update(model());
		BufferedImage withGloves = withTooltip(snapshot(panel, null, 372), panel, "Obsidian platebody");
		gloves = false;
		ImageIO.write(sideBySide(bare, "Without gloves", withGloves, "With Karamja gloves"), "png",
			new File(OUT, "feature-karamja-gloves.png"));

		// 7. for cloggers: tick a whole page, or track everything at once, and open pages right in the list
		Set<String> tour = new LinkedHashSet<>(wanted);
		wanted.clear();
		focus(panel, null);
		panel.update(model());
		scrollTo("EVERYTHING MISSING");
		BufferedImage tickPage = snapshot(panel, trackBoxCenter(panel, "Barbarian Assault"));
		for (com.rewardshopplanner.data.Activity activity : data.getActivities().values())
		{
			activity.getClogItems().stream().filter(i -> data.getReward(i) != null && !owned.contains(i)).forEach(wanted::add);
		}
		panel.update(model());
		scrollTo("TRACKING");
		BufferedImage trackedAll = snapshot(panel, null);
		focus(panel, "barbarian_assault");
		panel.update(model());
		BufferedImage dropdown = snapshot(panel, null);
		ImageIO.write(steps(new String[]{"Tick a page", "...or Track all", "Open it right there"}, tickPage, trackedAll, dropdown),
			"png", new File(OUT, "feature-for-cloggers.png"));
		wanted.clear();
		wanted.addAll(tour);
		scrollTo(null);

		writeGif(frames, delays, new File(OUT, "demo.gif"));
		ImageIO.write(forestryStill, "png", new File(OUT, "feature-grid.png"));
		ImageIO.write(trackingStill, "png", new File(OUT, "feature-tracking.png"));
		ImageIO.write(overlayShowcase(), "png", new File(OUT, "feature-overlay.png"));
		ImageIO.write(materialsStill, "png", new File(OUT, "feature-materials.png"));
		ImageIO.write(homeMaterials, "png", new File(OUT, "feature-materials-home.png"));
		ImageIO.write(shopStill, "png", new File(OUT, "feature-shops.png"));
		ImageIO.write(goalsStill, "png", new File(OUT, "feature-goals.png"));
		ImageIO.write(setStill, "png", new File(OUT, "feature-sets.png"));
		ImageIO.write(sellBackStill, "png", new File(OUT, "feature-sellbacks.png"));
		ImageIO.write(castleWarsStill, "png", new File(OUT, "feature-sellall.png"));
		ImageIO.write(hero(home, forestryStill), "png", new File(OUT, "hero.png"));
	}

	private PanelModel model()
	{
		Plan plan = new PlannerCalculator(data).plan(PlannerInput.builder()
			.owned(owned).wanted(wanted).balances(balances).preferredActivity(preferred)
			.accountMode(AccountMode.IRONMAN).sellBack(sellBack).karamjaGloves(gloves).build());
		return new PanelModel(data, plan, balances, materials, owned, Set.of(),
			data.getActivities().values().stream().map(a -> a.getClogPage()).collect(java.util.stream.Collectors.toSet()),
			wanted, preferred, Map.of(), Map.of(), AccountMode.IRONMAN, sellBack, gloves, !gloves, false, false);
	}

	private static BufferedImage icon(int itemId, Runnable onLoaded)
	{
		return cachedIcon(itemId + ".png");
	}

	/** Icons fetched by scripts/fetch-preview-icons.ps1: items by id, spell sprites as sprite-<id>.png. */
	private static final ItemIcons ICONS = new ItemIcons()
	{
		@Override
		public BufferedImage get(int itemId, Runnable onLoaded)
		{
			return icon(itemId, onLoaded);
		}

		@Override
		public BufferedImage sprite(int spriteId, Runnable onLoaded)
		{
			return cachedIcon("sprite-" + spriteId + ".png");
		}
	};

	private static BufferedImage cachedIcon(String name)
	{
		try
		{
			File file = new File(".cache/preview-icons/" + name);
			return file.exists() ? ImageIO.read(file) : null;
		}
		catch (Exception e)
		{
			return null;
		}
	}

	// ------------------------------------------------------------------
	// Panel snapshots
	// ------------------------------------------------------------------

	private static JComponent content(PlannerPanel panel)
	{
		JComponent content = (JComponent) panel.getComponent(0);
		content.setSize(CONTENT_WIDTH, 10);
		content.setSize(CONTENT_WIDTH, content.getPreferredSize().height);
		layout(content);
		// "scroll" so the open page's row is at the top, like a player who just opened it
		viewTop = 0;
		if (focused != null)
		{
			javax.swing.JLabel row = findLabelContaining(content, focused);
			if (row != null)
			{
				viewTop = Math.max(0, SwingUtilities.convertPoint(row, 0, 0, content).y - 8);
			}
		}
		return content;
	}

	/** Scrolls to a label, e.g. a section title, without opening anything. */
	private static void scrollTo(String text)
	{
		focused = text;
	}

	/** Centre of the "track this page" checkbox on a page's row, in content coordinates. */
	private static Point trackBoxCenter(PlannerPanel panel, String pageName)
	{
		JComponent content = content(panel);
		javax.swing.JLabel name = findLabelContaining(content, "▸ " + pageName);
		if (name == null)
		{
			return null;
		}
		for (Component sibling : name.getParent().getComponents())
		{
			if (sibling instanceof javax.swing.JCheckBox)
			{
				return SwingUtilities.convertPoint(sibling, sibling.getWidth() / 2 - 2, sibling.getHeight() / 2, content);
			}
		}
		return null;
	}

	/** Opens one page's dropdown (null closes them) and keeps the view scrolled to it. */
	private void focus(PlannerPanel panel, String activityId)
	{
		focused = activityId == null ? null : "▾ " + data.getActivities().get(activityId).getName();
		panel.open(activityId);
	}

	/** The panel as it looks in the client (225 x FRAME_HEIGHT), scaled up, with an optional click marker. */
	private static BufferedImage snapshot(PlannerPanel panel, Point click)
	{
		return snapshot(panel, click, FRAME_HEIGHT);
	}

	private static BufferedImage snapshot(PlannerPanel panel, Point click, int height)
	{
		JComponent content = content(panel);
		BufferedImage image = new BufferedImage(PANEL_WIDTH * SCALE, height * SCALE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.scale(SCALE, SCALE);
		g.setColor(ColorScheme.DARK_GRAY_COLOR);
		g.fillRect(0, 0, PANEL_WIDTH, height);
		g.translate(8, 10 - viewTop);
		Graphics2D panelG = (Graphics2D) g.create();
		panelG.clipRect(0, viewTop, CONTENT_WIDTH, height - 10);
		content.paint(panelG);
		panelG.dispose();
		if (click != null)
		{
			g.setColor(new Color(255, 152, 31, 200));
			g.setStroke(new BasicStroke(2.5f));
			g.drawOval(click.x - 12, click.y - 12, 24, 24);
			g.setColor(new Color(255, 152, 31, 80));
			g.fillOval(click.x - 12, click.y - 12, 24, 24);
		}
		g.dispose();
		return image;
	}

	/** Centre of an item's slot, in content coordinates. */
	private static Point slotCenter(PlannerPanel panel, String item)
	{
		JComponent content = content(panel);
		for (ItemSlot slot : slots(content))
		{
			String tip = slot.getToolTipText();
			if (tip != null && tip.contains("<b>" + item + "</b>"))
			{
				Point p = SwingUtilities.convertPoint(slot, slot.getWidth() / 2, slot.getHeight() / 2, content);
				return p;
			}
		}
		return null;
	}

	/** Paints the tooltip of the first label whose text contains {@code part}, just below it. */
	private static BufferedImage withLabelTooltip(BufferedImage frame, PlannerPanel panel, String part)
	{
		JComponent content = content(panel);
		javax.swing.JLabel target = findLabelContaining(content, part);
		if (target == null || target.getToolTipText() == null)
		{
			return frame;
		}
		javax.swing.JToolTip tip = target.createToolTip();
		tip.setTipText(target.getToolTipText());
		java.awt.Dimension size = tip.getPreferredSize();
		tip.setSize(size);
		layout(tip);
		Point at = SwingUtilities.convertPoint(target, 12, target.getHeight() + 2, content);
		int x = Math.max(0, Math.min(at.x, CONTENT_WIDTH - size.width));
		Graphics2D g = frame.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.scale(SCALE, SCALE);
		g.translate(8, 10 - viewTop);
		g.setColor(new Color(0, 0, 0, 110));
		g.fillRect(x + 3, at.y + 3, size.width, size.height);
		g.translate(x, at.y);
		tip.paint(g);
		g.dispose();
		return frame;
	}

	private static javax.swing.JLabel findLabelContaining(Container c, String part)
	{
		for (Component child : c.getComponents())
		{
			if (child instanceof javax.swing.JLabel && ((javax.swing.JLabel) child).getText() != null
				&& ((javax.swing.JLabel) child).getText().contains(part))
			{
				return (javax.swing.JLabel) child;
			}
			if (child instanceof Container)
			{
				javax.swing.JLabel found = findLabelContaining((Container) child, part);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Centre of a text link (e.g. "sell back all"), in content coordinates. */
	private static Point linkCenter(PlannerPanel panel, String text)
	{
		JComponent content = content(panel);
		javax.swing.JLabel label = findLabel(content, text);
		return label == null ? null : SwingUtilities.convertPoint(label, label.getWidth() / 2, label.getHeight() / 2, content);
	}

	private static javax.swing.JLabel findLabel(Container c, String text)
	{
		for (Component child : c.getComponents())
		{
			if (child instanceof javax.swing.JLabel && text.equals(((javax.swing.JLabel) child).getText()))
			{
				return (javax.swing.JLabel) child;
			}
			if (child instanceof Container)
			{
				javax.swing.JLabel found = findLabel((Container) child, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static List<ItemSlot> slots(Container c)
	{
		List<ItemSlot> out = new ArrayList<>();
		for (Component child : c.getComponents())
		{
			if (child instanceof ItemSlot)
			{
				out.add((ItemSlot) child);
			}
			else if (child instanceof Container)
			{
				out.addAll(slots((Container) child));
			}
		}
		return out;
	}

	/** Paints the slot's real tooltip (a Swing JToolTip with the panel's text) below or above the slot. */
	private static BufferedImage withTooltip(BufferedImage frame, PlannerPanel panel, String item)
	{
		JComponent content = content(panel);
		ItemSlot target = null;
		for (ItemSlot slot : slots(content))
		{
			if (slot.getToolTipText() != null && slot.getToolTipText().contains("<b>" + item + "</b>"))
			{
				target = slot;
			}
		}
		if (target == null)
		{
			return frame;
		}
		javax.swing.JToolTip tip = target.createToolTip();
		tip.setTipText(target.getToolTipText());
		java.awt.Dimension size = tip.getPreferredSize();
		int maxWidth = CONTENT_WIDTH;
		if (size.width > maxWidth)
		{
			// wrap long tooltips to the panel width
			tip.setTipText(target.getToolTipText().replace("<html>", "<html><div style='width:125px'>"));
			size = tip.getPreferredSize();
		}
		tip.setSize(size);
		layout(tip);
		Point at = SwingUtilities.convertPoint(target, 0, target.getHeight() + 4, content);
		int x = Math.max(0, Math.min(at.x, CONTENT_WIDTH - size.width));
		int y = at.y - viewTop + size.height > frame.getHeight() / SCALE - 20 ? at.y - target.getHeight() - 8 - size.height : at.y;

		Graphics2D g = frame.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.scale(SCALE, SCALE);
		g.translate(8, 10 - viewTop);
		g.setColor(new Color(0, 0, 0, 110));
		g.fillRect(x + 3, y + 3, size.width, size.height);
		g.translate(x, y);
		tip.paint(g);
		g.dispose();
		return frame;
	}

	/** Draws a right-click menu next to a slot, as the client shows it: one highlighted line, one greyed title. */
	private static BufferedImage withMenu(BufferedImage frame, Point at, String[] lines, int highlighted, int title)
	{
		Graphics2D g = frame.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.scale(SCALE, SCALE);
		g.translate(8, 10 - viewTop);
		Font font = FontManager.getRunescapeSmallFont();
		g.setFont(font);
		int lineHeight = 16;
		int width = 0;
		for (String line : lines)
		{
			width = Math.max(width, g.getFontMetrics().stringWidth(line));
		}
		width += 16;
		int height = lines.length * lineHeight + 8;
		int x = Math.max(0, Math.min(at.x - 20, CONTENT_WIDTH - width));
		int y = at.y + 10;
		g.setColor(new Color(0, 0, 0, 120));
		g.fillRoundRect(x + 3, y + 3, width, height, 6, 6);
		g.setColor(new Color(0x3c3f41));
		g.fillRoundRect(x, y, width, height, 6, 6);
		g.setColor(new Color(0x5a5d60));
		g.drawRoundRect(x, y, width, height, 6, 6);
		for (int i = 0; i < lines.length; i++)
		{
			int ly = y + 4 + (i + 1) * lineHeight - 4;
			if (i == highlighted)
			{
				g.setColor(new Color(0x4b6eaf));
				g.fillRect(x + 2, ly - 12, width - 4, lineHeight);
			}
			if (i == title)
			{
				g.setColor(new Color(0x5a5d60));
				g.drawLine(x + 6, ly - 14, x + width - 6, ly - 14);
			}
			g.setColor(i == title ? new Color(0x9a9a9a) : Color.WHITE);
			g.drawString(lines[i], x + 8, ly);
		}
		g.dispose();
		return frame;
	}

	// ------------------------------------------------------------------
	// Hero image
	// ------------------------------------------------------------------

	/**
	 * The on-screen progress in its three styles, drawn by the real overlay code: Trouble Brewing
	 * (one currency) and the Mage Training Arena (four) with simulated balances.
	 */
	private BufferedImage overlayShowcase() throws Exception
	{
		Set<String> goal = new LinkedHashSet<>(List.of("Purple tricorn hat", "Lucky shot flag"));
		data.getActivities().get("mta").getClogItems().stream().filter(i -> data.getReward(i) != null).forEach(goal::add);
		Map<String, Long> points = Map.of("pieces_of_eight", 2_240L, "mta_tele", 1_900L, "mta_alch", 3_075L, "mta_ench", 12_400L, "mta_grave", 980L);
		Plan plan = new PlannerCalculator(data).plan(PlannerInput.builder().wanted(goal).balances(points).build());
		PanelModel sample = new PanelModel(data, plan, points, Map.of(), Set.of(), Set.of(), Set.of(), goal,
			Map.of(), Map.of(), Map.of(), AccountMode.MAIN, Set.of(), false, true, false, false);
		List<com.rewardshopplanner.overlay.PageProgress> brewing =
			List.of(com.rewardshopplanner.overlay.PageProgress.of(sample, data.getActivities().get("trouble_brewing")));
		List<com.rewardshopplanner.overlay.PageProgress> arena =
			List.of(com.rewardshopplanner.overlay.PageProgress.of(sample, data.getActivities().get("mta")));

		int w = 640;
		int h = 230;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setPaint(new GradientPaint(0, 0, new Color(0x4a5a3a), w, h, new Color(0x2e3a26)));
		g.fillRect(0, 0, w, h);
		g.setFont(FontManager.getRunescapeSmallFont());
		g.setColor(Color.WHITE);
		g.drawString("Bars (default)", 10, 14);
		g.drawString("Text", 225, 14);
		g.drawString("Icons (hover for the numbers)", 440, 14);
		// each style stacks its two boxes by their real height (icons make the rows taller)
		int below = drawOverlay(g, RewardShopPlannerConfig.OverlayStyle.BARS, brewing, 10, 22);
		drawOverlay(g, RewardShopPlannerConfig.OverlayStyle.BARS, arena, 10, 22 + below + 8);
		below = drawOverlay(g, RewardShopPlannerConfig.OverlayStyle.TEXT, brewing, 225, 22);
		drawOverlay(g, RewardShopPlannerConfig.OverlayStyle.TEXT, arena, 225, 22 + below + 8);
		int x = 440;
		for (com.rewardshopplanner.overlay.PageProgress.Bar bar : arena.get(0).getBars())
		{
			drawInfoBox(g, arena.get(0), bar, x, 22);
			x += 37;
		}
		drawInfoBox(g, brewing.get(0), brewing.get(0).getBars().get(0), 440, 62);
		g.dispose();

		BufferedImage big = new BufferedImage(w * SCALE, h * SCALE, BufferedImage.TYPE_INT_RGB);
		Graphics2D bg = big.createGraphics();
		bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		bg.drawImage(image, 0, 0, w * SCALE, h * SCALE, null);
		bg.dispose();
		return big;
	}

	/** Draws the overlay at (x, y) and returns its height. */
	private int drawOverlay(Graphics2D g, RewardShopPlannerConfig.OverlayStyle style,
		List<com.rewardshopplanner.overlay.PageProgress> pages, int x, int y)
	{
		RewardShopPlannerConfig config = new RewardShopPlannerConfig()
		{
			@Override
			public OverlayStyle overlayStyle()
			{
				return style;
			}
		};
		com.rewardshopplanner.overlay.ProgressOverlay overlay = new com.rewardshopplanner.overlay.ProgressOverlay(null, config, ICONS, data, () -> pages);
		// RuneLite sizes the background from the previous frame, so draw once off screen first
		BufferedImage scratch = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = scratch.createGraphics();
		sg.setFont(FontManager.getRunescapeSmallFont());
		overlay.render(sg);
		sg.dispose();
		Graphics2D og = (Graphics2D) g.create();
		og.translate(x, y);
		og.setFont(FontManager.getRunescapeSmallFont());
		java.awt.Dimension size = overlay.render(og);
		og.dispose();
		return size == null ? 0 : size.height;
	}

	private void drawInfoBox(Graphics2D g, com.rewardshopplanner.overlay.PageProgress page,
		com.rewardshopplanner.overlay.PageProgress.Bar bar, int x, int y)
	{
		BufferedImage iconImage = ICONS.currency(data.getCurrencies().get(bar.getCurrencyId()), () -> { });
		com.rewardshopplanner.overlay.ProgressInfoBox box = new com.rewardshopplanner.overlay.ProgressInfoBox(() -> iconImage, null, "preview");
		box.setPage(page);
		box.setBar(bar);
		net.runelite.client.ui.overlay.components.InfoBoxComponent component = new net.runelite.client.ui.overlay.components.InfoBoxComponent();
		component.setImage(box.getImage());
		component.setText(box.getText());
		component.setColor(box.getTextColor());
		component.setPreferredSize(new java.awt.Dimension(35, 35));
		component.setPreferredLocation(new Point(x, y));
		g.setFont(FontManager.getRunescapeSmallFont());
		component.render(g);
	}

	/** Panel snapshots in a row, each with a caption above, for a feature told in steps. */
	private static BufferedImage steps(String[] titles, BufferedImage... panels)
	{
		int gap = 24;
		int top = 52;
		int w = gap;
		int h = 0;
		for (BufferedImage panel : panels)
		{
			w += panel.getWidth() + gap;
			h = Math.max(h, panel.getHeight());
		}
		h += top + gap;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setPaint(new GradientPaint(0, 0, new Color(0x1b1b1b), w, h, new Color(0x2b2118)));
		g.fillRect(0, 0, w, h);
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(26f));
		int x = gap;
		for (int i = 0; i < panels.length; i++)
		{
			g.setColor(ColorScheme.BRAND_ORANGE);
			g.drawString(titles[i], x, 38);
			g.drawImage(panels[i], x, top, null);
			x += panels[i].getWidth() + gap;
		}
		g.dispose();
		return image;
	}

	/** Two panel snapshots next to each other, each with a caption above. */
	private static BufferedImage sideBySide(BufferedImage left, String leftTitle, BufferedImage right, String rightTitle)
	{
		int gap = 24;
		int top = 52;
		int w = left.getWidth() + right.getWidth() + gap * 3;
		int h = Math.max(left.getHeight(), right.getHeight()) + top + gap;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setPaint(new GradientPaint(0, 0, new Color(0x1b1b1b), w, h, new Color(0x2b2118)));
		g.fillRect(0, 0, w, h);
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(26f));
		g.setColor(new Color(0xb5b5b5));
		g.drawString(leftTitle, gap, 38);
		g.setColor(ColorScheme.BRAND_ORANGE);
		g.drawString(rightTitle, gap * 2 + left.getWidth(), 38);
		g.drawImage(left, gap, top, null);
		g.drawImage(right, gap * 2 + left.getWidth(), top, null);
		g.dispose();
		return image;
	}

	private static BufferedImage hero(BufferedImage home, BufferedImage page)
	{
		int w = 1280;
		int h = 640;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setPaint(new GradientPaint(0, 0, new Color(0x1b1b1b), w, h, new Color(0x2b2118)));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(255, 152, 31, 28));
		g.fillOval(-200, 380, 700, 500);

		try
		{
			BufferedImage logo = ImageIO.read(new File(OUT, "logo.png"));
			g.drawImage(logo, 54, 58, 132, 132, null);
		}
		catch (Exception e)
		{
			// the logo is optional
		}
		g.setColor(ColorScheme.BRAND_ORANGE);
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(54f));
		g.drawString("Reward Shop", 204, 118);
		g.drawString("Planner", 204, 176);
		g.setColor(new Color(0xdddddd));
		g.setFont(new Font("SansSerif", Font.PLAIN, 24));
		g.drawString("Plan the reward-shop slots", 66, 262);
		g.drawString("of your collection log.", 66, 294);
		g.setFont(new Font("SansSerif", Font.PLAIN, 19));
		g.setColor(new Color(0xb5b5b5));
		String[] points = {
			"28 minigames and activities",
			"Balances read straight from the game",
			"Pick items, see what's left to earn",
			"Progress on screen while you play",
			"Ironman aware: sell-backs, shops, sets",
		};
		for (int i = 0; i < points.length; i++)
		{
			g.setColor(ColorScheme.BRAND_ORANGE);
			g.fillOval(68, 352 + i * 40, 9, 9);
			g.setColor(new Color(0xc8c8c8));
			g.drawString(points[i], 90, 362 + i * 40);
		}

		drawPanel(g, page, 540, 48, 0.62);
		drawPanel(g, home, 870, 74, 0.62);
		g.dispose();
		return image;
	}

	private static void drawPanel(Graphics2D g, BufferedImage panel, int x, int y, double scale)
	{
		int w = (int) (panel.getWidth() * scale);
		int h = Math.min((int) (panel.getHeight() * scale), 540);
		for (int i = 12; i > 0; i -= 3)
		{
			g.setColor(new Color(0, 0, 0, 18));
			g.fill(new RoundRectangle2D.Double(x - i + 6, y - i + 10, w + 2 * i, h + 2 * i, 18, 18));
		}
		Graphics2D c = (Graphics2D) g.create();
		c.setClip(new RoundRectangle2D.Double(x, y, w, h, 14, 14));
		c.drawImage(panel, x, y, w, (int) (panel.getHeight() * scale), null);
		c.dispose();
		g.setColor(new Color(0x444444));
		g.draw(new RoundRectangle2D.Double(x, y, w, h, 14, 14));
	}

	// ------------------------------------------------------------------
	// GIF
	// ------------------------------------------------------------------

	private static void add(List<BufferedImage> frames, List<Integer> delays, BufferedImage frame, int millis)
	{
		frames.add(frame);
		delays.add(millis);
	}

	private static void writeGif(List<BufferedImage> frames, List<Integer> delays, File file) throws Exception
	{
		ImageWriter writer = ImageIO.getImageWritersBySuffix("gif").next();
		file.delete();
		try (ImageOutputStream out = ImageIO.createImageOutputStream(file))
		{
			writer.setOutput(out);
			writer.prepareWriteSequence(null);
			for (int i = 0; i < frames.size(); i++)
			{
				BufferedImage frame = frames.get(i);
				ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(frame);
				IIOMetadata metadata = writer.getDefaultImageMetadata(type, writer.getDefaultWriteParam());
				String format = metadata.getNativeMetadataFormatName();
				IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);

				IIOMetadataNode control = child(root, "GraphicControlExtension");
				control.setAttribute("disposalMethod", "none");
				control.setAttribute("userInputFlag", "FALSE");
				control.setAttribute("transparentColorFlag", "FALSE");
				control.setAttribute("delayTime", Integer.toString(delays.get(i) / 10));
				control.setAttribute("transparentColorIndex", "0");

				if (i == 0)
				{
					IIOMetadataNode extensions = child(root, "ApplicationExtensions");
					IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
					loop.setAttribute("applicationID", "NETSCAPE");
					loop.setAttribute("authenticationCode", "2.0");
					loop.setUserObject(new byte[]{1, 0, 0});
					extensions.appendChild(loop);
				}
				metadata.setFromTree(format, root);
				writer.writeToSequence(new IIOImage(frame, null, metadata), writer.getDefaultWriteParam());
			}
			writer.endWriteSequence();
		}
		writer.dispose();
	}

	private static IIOMetadataNode child(IIOMetadataNode root, String name)
	{
		for (int i = 0; i < root.getLength(); i++)
		{
			if (root.item(i).getNodeName().equalsIgnoreCase(name))
			{
				return (IIOMetadataNode) root.item(i);
			}
		}
		IIOMetadataNode node = new IIOMetadataNode(name);
		root.appendChild(node);
		return node;
	}

	private static void layout(Container c)
	{
		c.doLayout();
		for (Component child : c.getComponents())
		{
			if (child instanceof Container)
			{
				layout((Container) child);
			}
		}
	}
}
