package com.rewardshopplanner.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.gson.Gson;
import com.rewardshopplanner.calc.AccountMode;
import com.rewardshopplanner.calc.Plan;
import com.rewardshopplanner.calc.PlannerCalculator;
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
	private static final File OUT = new File("docs/marketing");

	private RewardData data;
	private final Set<String> owned = new LinkedHashSet<>(List.of(
		"Lumberjack hat", "Lumberjack top", "Lumberjack legs", "Lumberjack boots", "Log basket", "Twitcher's gloves",
		"Coal bag", "Decorative helm (red)", "Decorative sword (red)", "Decorative shield (red)",
		"Void knight top", "Void knight robe", "Void knight gloves"));
	private final Set<String> wanted = new LinkedHashSet<>();
	private final Set<String> sellBack = new LinkedHashSet<>();
	private final Map<String, String> preferred = new HashMap<>();
	private final Map<String, Long> balances = Map.of(
		"anima_bark", 5_320L, "pheasant_feather", 9L, "golden_nugget", 150L, "pc_points", 135L, "cw_ticket", 74L,
		"termites", 410L);

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
		PlannerPanel panel = new PlannerPanel(null, MarketingRenderer::icon);
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

		// 3. open Forestry and pick items
		panel.setSearchText("");
		panel.open("forestry");
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

		// 4. an item sold in two shops
		panel.open("motherlode_mine");
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
		panel.open("colossal_wyrm");
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
		panel.open("forestry");
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
		add(frames, delays, withTooltip(snapshot(panel, funky), panel, "Funky shaped log"), 2800);
		// taller still so the tooltip fits below the item and the "back from sell-backs" line stays visible
		BufferedImage sellBackStill = withTooltip(snapshot(panel, funky, 540), panel, "Funky shaped log");

		// 4d. Castle Wars refunds in full: "sell back all" leaves only the dearest item's price
		panel.open("castle_wars");
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
		panel.open(null);
		panel.update(model());
		BufferedImage home = snapshot(panel, null);
		add(frames, delays, home, 2200);
		panel.setGoalListOpen(true);
		BufferedImage goalsStill = snapshot(panel, null);
		add(frames, delays, goalsStill, 2600);

		writeGif(frames, delays, new File(OUT, "demo.gif"));
		ImageIO.write(forestryStill, "png", new File(OUT, "feature-grid.png"));
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
			.accountMode(AccountMode.IRONMAN).sellBack(sellBack).build());
		return new PanelModel(data, plan, balances, owned, Set.of(),
			Set.of("Forestry", "Motherlode Mine", "Castle Wars", "Pest Control", "Temple Trekking", "Colossal Wyrm Agility"),
			wanted, preferred, Map.of(), Map.of(), AccountMode.IRONMAN, sellBack, false, false);
	}

	private static BufferedImage icon(int itemId, Runnable onLoaded)
	{
		try
		{
			File file = new File(".cache/preview-icons/" + itemId + ".png");
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
		return content;
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
		g.translate(8, 10);
		Graphics2D panelG = (Graphics2D) g.create();
		panelG.clipRect(0, 0, CONTENT_WIDTH, height - 10);
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
		int y = at.y + size.height > frame.getHeight() / SCALE - 20 ? at.y - target.getHeight() - 8 - size.height : at.y;

		Graphics2D g = frame.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.scale(SCALE, SCALE);
		g.translate(8, 10);
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
		g.translate(8, 10);
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
