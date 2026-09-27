package com.rewardshopplanner.ui;

import com.rewardshopplanner.RewardShopPlannerPlugin;
import com.rewardshopplanner.calc.AccountMode;
import com.rewardshopplanner.calc.ActivityPlan;
import com.rewardshopplanner.calc.Plan;
import com.rewardshopplanner.calc.PlannerCalculator;
import com.rewardshopplanner.data.Activity;
import com.rewardshopplanner.data.Currency;
import com.rewardshopplanner.data.Reward;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.QuantityFormatter;

/**
 * Side panel with two screens:
 * <ul>
 * <li>home: the goal as one progress bar per currency, then a searchable list of log pages;</li>
 * <li>page: that page's goal and its items as an icon grid, like the in-game collection log.</li>
 * </ul>
 * Details (prices, materials, shops) live in tooltips and right-click menus to keep the view calm.
 */
public class PlannerPanel extends PluginPanel
{
	private static final Color GOOD = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();
	private static final Color NEUTRAL_BAR = new Color(0x4a7ab5);
	private static final int GRID_COLUMNS = 5;
	/** HTML wrap width in CSS px (Swing renders it ~1.37x wider); content is 209px. */
	private static final int WRAP_WIDTH = 160;

	private final RewardShopPlannerPlugin plugin;
	private final ItemIcons icons;
	private final JPanel content = new JPanel();
	private final IconTextField search = new IconTextField();
	private PanelModel model;
	/** Page being viewed, or null for the home screen. */
	private String openActivity;
	private boolean extraGoalsOpen;
	private boolean goalListOpen;

	public PlannerPanel(RewardShopPlannerPlugin plugin, ItemIcons icons)
	{
		this.plugin = plugin;
		this.icons = icons;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 8, 10, 8));
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		add(content, BorderLayout.NORTH);

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PANEL_WIDTH - 16, 28));
		search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		search.setAlignmentX(Component.LEFT_ALIGNMENT);
		search.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				rebuild();
			}
		});
		search.addClearListener(this::rebuild);
	}

	public void update(PanelModel model)
	{
		this.model = model;
		rebuild();
	}

	/** Used by the screenshot renderer in tests. */
	void setSearchText(String text)
	{
		search.setText(text);
		rebuild();
	}

	/** Used by the screenshot renderer in tests. */
	void setGoalListOpen(boolean open)
	{
		goalListOpen = open;
		rebuild();
	}

	/** Opens a page, or the home screen when null. */
	public void open(String activityId)
	{
		openActivity = activityId;
		rebuild();
		if (getScrollPane() != null)
		{
			getScrollPane().getVerticalScrollBar().setValue(0);
		}
	}

	private void rebuild()
	{
		content.removeAll();
		if (model != null)
		{
			Activity activity = openActivity == null ? null : model.getData().getActivities().get(openActivity);
			if (activity == null)
			{
				buildHome();
			}
			else
			{
				buildPage(activity);
			}
		}
		content.revalidate();
		content.repaint();
	}

	// ------------------------------------------------------------------
	// Home
	// ------------------------------------------------------------------

	private void buildHome()
	{
		Plan plan = model.getPlan();

		JPanel header = row();
		header.add(label("My goals", FontManager.getRunescapeBoldFont(), Color.WHITE), BorderLayout.WEST);
		JLabel extra = link(extraGoalsOpen ? "done" : "+ extra", () ->
		{
			extraGoalsOpen = !extraGoalsOpen;
			rebuild();
		});
		extra.setToolTipText("Add a currency target outside the log, e.g. sawmill vouchers");
		header.add(extra, BorderLayout.EAST);
		content.add(header);
		int selling = (int) model.getSellBack().stream()
			.filter(i -> model.getWanted().contains(i) && !model.getOwned().contains(i))
			.count();
		JLabel mode = label(modeName() + (selling > 0 ? " · selling back " + selling : "") + (model.isKaramjaGloves() ? " · Karamja gloves" : ""),
			FontManager.getRunescapeSmallFont(), MUTED);
		mode.setToolTipText("<html>Your account type is read from the game; a few items can't be sold back by every type.<br>"
			+ "Nothing is sold back unless you choose it: right-click an item, \"Sell back after logging it\".<br>"
			+ "Karamja gloves (TzHaar prices) are ticked on the TzHaar page.</html>");
		content.add(mode);
		content.add(Box.createVerticalStrut(8));

		Map<String, Long> needed = plan.getTotal().getNet();
		if (needed.values().stream().noneMatch(v -> v > 0))
		{
			content.add(wrapped("Open a page below and click the items you want.", MUTED));
		}
		else
		{
			addCurrencyBars(needed, true);
			if (!plan.getUndecided().isEmpty())
			{
				content.add(wrapped(plan.getUndecided().size() + " item(s) need a shop: right-click the ones marked ?", ACCENT));
			}
		}
		addGoalItems();
		if (extraGoalsOpen)
		{
			addExtraGoalsEditor();
		}

		content.add(Box.createVerticalStrut(14));
		content.add(search);
		content.add(Box.createVerticalStrut(8));

		String filter = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		List<Activity> pages = model.getData().getActivities().values().stream()
			.filter(a -> filter.isEmpty() || a.getName().toLowerCase(Locale.ROOT).contains(filter))
			.sorted(Comparator.comparing(Activity::getName))
			.collect(Collectors.toList());
		List<Activity> inProgress = pages.stream().filter(a -> !wantedMissing(a).isEmpty()).collect(Collectors.toList());
		List<Activity> others = pages.stream()
			.filter(a -> wantedMissing(a).isEmpty())
			.filter(a -> !model.isHideCompleted() || !plan.getActivity(a.getId()).isComplete())
			.collect(Collectors.toList());

		if (!inProgress.isEmpty())
		{
			content.add(sectionTitle("In progress"));
			inProgress.forEach(a -> content.add(pageRow(a)));
			content.add(Box.createVerticalStrut(10));
		}
		if (!others.isEmpty())
		{
			content.add(sectionTitle(inProgress.isEmpty() ? "Pages" : "All pages"));
			others.forEach(a -> content.add(pageRow(a)));
		}
		if (pages.isEmpty())
		{
			content.add(wrapped("No page matches \"" + search.getText() + "\".", MUTED));
		}
	}

	/** "N items in goal" with an expandable list to remove them one by one, and "clear all". */
	private void addGoalItems()
	{
		Map<String, String> goalItems = new LinkedHashMap<>(); // item -> page name
		for (Activity activity : model.getData().getActivities().values())
		{
			for (String item : wantedMissing(activity))
			{
				goalItems.putIfAbsent(item, activity.getName());
			}
		}
		if (goalItems.isEmpty())
		{
			return;
		}

		JPanel summary = row();
		summary.setBorder(new EmptyBorder(2, 0, 2, 0));
		summary.add(link((goalListOpen ? "▾ " : "▸ ") + goalItems.size() + " item(s) in goal", () ->
		{
			goalListOpen = !goalListOpen;
			rebuild();
		}), BorderLayout.WEST);
		summary.add(link("clear all", () ->
		{
			int answer = JOptionPane.showConfirmDialog(this, "Remove all " + goalItems.size() + " items from your goal?",
				"Reward Shop Planner", JOptionPane.YES_NO_OPTION);
			if (answer == JOptionPane.YES_OPTION)
			{
				plugin.setWanted(new ArrayList<>(model.getWanted()), false);
			}
		}), BorderLayout.EAST);
		content.add(summary);

		if (!goalListOpen)
		{
			return;
		}
		content.add(Box.createVerticalStrut(4));
		for (Map.Entry<String, String> entry : goalItems.entrySet())
		{
			String item = entry.getKey();
			JPanel row = row();
			row.setBorder(new EmptyBorder(2, 6, 2, 0));
			JLabel name = label(item, FontManager.getRunescapeSmallFont(), Color.WHITE);
			name.setToolTipText(entry.getValue());
			row.add(name, BorderLayout.CENTER);
			JLabel remove = link("✕", () -> plugin.setWanted(List.of(item), false));
			remove.setToolTipText("Remove from goal");
			row.add(remove, BorderLayout.EAST);
			content.add(row);
		}
	}

	private JComponent pageRow(Activity activity)
	{
		ActivityPlan activityPlan = model.getPlan().getActivity(activity.getId());
		boolean complete = activityPlan.isComplete();
		boolean synced = model.getSyncedPages().contains(activity.getClogPage());

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JPanel top = row();
		top.add(label(activity.getName(), FontManager.getRunescapeSmallFont(), complete ? GOOD : Color.WHITE), BorderLayout.WEST);
		top.add(label(activityPlan.getSlotsOwned() + "/" + activityPlan.getSlotsTotal(), FontManager.getRunescapeSmallFont(), MUTED),
			BorderLayout.EAST);
		card.add(top);
		card.add(Box.createVerticalStrut(4));
		double fraction = activityPlan.getSlotsTotal() == 0 ? 0 : (double) activityPlan.getSlotsOwned() / activityPlan.getSlotsTotal();
		card.add(new ProgressBar(fraction, complete ? GOOD : NEUTRAL_BAR, 3));

		card.setToolTipText(synced ? null : "Open this page in the collection log to sync what you own");
		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		card.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				open(activity.getId());
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				card.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.setBorder(new EmptyBorder(0, 0, 3, 0));
		wrapper.add(card, BorderLayout.CENTER);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
		return wrapper;
	}

	private void addExtraGoalsEditor()
	{
		content.add(Box.createVerticalStrut(8));
		for (Map.Entry<String, Long> goal : model.getExtraGoals().entrySet())
		{
			JPanel row = row();
			row.add(label(fmt(goal.getValue()) + " " + currencyName(goal.getKey()), FontManager.getRunescapeSmallFont(), MUTED), BorderLayout.WEST);
			row.add(link("remove", () -> plugin.setExtraGoal(goal.getKey(), null)), BorderLayout.EAST);
			content.add(row);
		}

		JComboBox<Currency> currencyBox = new JComboBox<>(model.getData().getCurrencies().values().stream()
			.filter(c -> c.getSource() != Currency.Source.COUNTER)
			.toArray(Currency[]::new));
		currencyBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				super.getListCellRendererComponent(list, value, index, selected, focus);
				setText(value == null ? "" : ((Currency) value).getName());
				return this;
			}
		});
		currencyBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		currencyBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		JTextField amount = new JTextField();
		amount.setToolTipText("Amount, e.g. 5000 or 5k");
		JButton add = new JButton("Add");
		add.setFont(FontManager.getRunescapeSmallFont());
		add.addActionListener(e ->
		{
			Currency currency = (Currency) currencyBox.getSelectedItem();
			Long value = parseAmount(amount.getText());
			if (currency != null && value != null)
			{
				plugin.setExtraGoal(currency.getId(), model.getExtraGoals().getOrDefault(currency.getId(), 0L) + value);
			}
		});

		content.add(Box.createVerticalStrut(4));
		content.add(currencyBox);
		content.add(Box.createVerticalStrut(4));
		JPanel row = row();
		row.add(amount, BorderLayout.CENTER);
		row.add(add, BorderLayout.EAST);
		content.add(row);
	}

	// ------------------------------------------------------------------
	// Page
	// ------------------------------------------------------------------

	private void buildPage(Activity activity)
	{
		ActivityPlan activityPlan = model.getPlan().getActivity(activity.getId());

		JPanel header = row();
		JLabel back = link("←", () -> open(null));
		back.setFont(FontManager.getRunescapeBoldFont());
		back.setToolTipText("Back to all pages");
		back.setBorder(new EmptyBorder(0, 0, 0, 8));
		header.add(back, BorderLayout.WEST);
		header.add(label(activity.getName(), FontManager.getRunescapeBoldFont(), Color.WHITE), BorderLayout.CENTER);
		header.add(label(activityPlan.getSlotsOwned() + "/" + activityPlan.getSlotsTotal(), FontManager.getRunescapeSmallFont(), MUTED),
			BorderLayout.EAST);
		content.add(header);
		if (!model.getSyncedPages().contains(activity.getClogPage()))
		{
			content.add(Box.createVerticalStrut(4));
			content.add(wrapped("Open this page in the collection log to sync it.", ACCENT));
		}
		content.add(Box.createVerticalStrut(12));

		Map<String, Long> cost = activityPlan.getCost().getNet();
		if (wantedMissing(activity).isEmpty())
		{
			content.add(wrapped("Click the items you want to see what they cost.", MUTED));
		}
		else
		{
			addCurrencyBars(cost, false);
			if (!activityPlan.getUndecided().isEmpty())
			{
				content.add(wrapped("Right-click the items marked ? to pick a shop.", ACCENT));
			}
			JComponent sellBacks = sellBackLine(activityPlan);
			if (sellBacks != null)
			{
				content.add(sellBacks);
			}
			JComponent details = detailsLine(activityPlan);
			if (details != null)
			{
				content.add(details);
			}
		}

		if (hasGlovePrices(activity))
		{
			content.add(Box.createVerticalStrut(6));
			content.add(glovesRow());
		}

		content.add(Box.createVerticalStrut(12));
		List<String> items = activity.getClogItems().stream()
			.filter(i -> model.getData().getReward(i) != null)
			.collect(Collectors.toList());
		List<String> missing = items.stream().filter(i -> !model.getOwned().contains(i)).collect(Collectors.toList());

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actions.setOpaque(false);
		actions.setAlignmentX(Component.LEFT_ALIGNMENT);
		actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		List<String> resellable = missing.stream().filter(i -> canSellBack(i, model.getData().getReward(i))).collect(Collectors.toList());
		if (!resellable.isEmpty())
		{
			boolean allSold = model.getSellBack().containsAll(resellable);
			JLabel sell = link(allSold ? "keep all" : "sell back all", () -> plugin.setSellBack(resellable, !allSold));
			sell.setToolTipText(allSold ? "Keep every item on this page instead of selling it back"
				: "Sell every item this shop buys back once its slot is logged (" + resellable.size() + " items)");
			actions.add(sell);
		}
		actions.add(link("all", () -> plugin.setWanted(missing, true)));
		actions.add(link("none", () -> plugin.setWanted(items, false)));
		content.add(actions);
		content.add(Box.createVerticalStrut(4));

		JPanel grid = new JPanel(new GridLayout(0, GRID_COLUMNS, 3, 3));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (String item : items)
		{
			grid.add(itemSlot(item, model.getData().getReward(item)));
		}
		int rows = (items.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows * (ItemSlot.HEIGHT + 3)));
		content.add(grid);

		content.add(Box.createVerticalStrut(8));
		content.add(wrapped("Click to add or remove from your goal. Right-click for more.", MUTED));
	}

	private boolean hasGlovePrices(Activity activity)
	{
		return activity.getClogItems().stream()
			.map(i -> model.getData().getReward(i))
			.anyMatch(r -> r != null && r.getOffers().stream().anyMatch(o -> o.getKaramjaGlovesCost() != null));
	}

	/** "I wear Karamja gloves" checkbox; starts from the diary, with an "auto" link once changed. */
	private JComponent glovesRow()
	{
		JPanel row = row();
		JCheckBox box = new JCheckBox("I wear Karamja gloves", model.isKaramjaGloves());
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setForeground(Color.WHITE);
		box.setOpaque(false);
		box.setFocusPainted(false);
		box.setBorder(new EmptyBorder(0, 0, 0, 0));
		box.setToolTipText("<html>Karamja gloves make TzHaar items about 13% cheaper<br>and more than double what the shops pay back.</html>");
		box.addActionListener(e -> plugin.setKaramjaGloves(box.isSelected()));
		row.add(box, BorderLayout.WEST);
		if (model.isKaramjaGlovesAuto())
		{
			JLabel auto = label("from diary", FontManager.getRunescapeSmallFont(), MUTED);
			auto.setToolTipText(model.isKaramjaGlovesClaimed()
				? "You claimed the gloves from the Karamja easy diary"
				: "You haven't claimed the gloves from the Karamja easy diary");
			row.add(auto, BorderLayout.EAST);
		}
		else
		{
			JLabel auto = link("auto", () -> plugin.setKaramjaGloves(null));
			auto.setToolTipText("Go back to following the Karamja easy diary ("
				+ (model.isKaramjaGlovesClaimed() ? "gloves claimed" : "gloves not claimed") + ")");
			row.add(auto, BorderLayout.EAST);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		return row;
	}

	private JComponent itemSlot(String item, Reward reward)
	{
		boolean owned = model.getOwned().contains(item);
		boolean wanted = model.getWanted().contains(item);
		boolean needsChoice = wanted && !owned && reward.getOffers().size() > 1 && !hasShopChoice(item, reward);

		ItemSlot[] holder = new ItemSlot[1];
		ItemSlot slot = new ItemSlot(
			reward.getItemId() == null ? null : icons.get(reward.getItemId(), () ->
			{
				if (holder[0] != null)
				{
					holder[0].repaint();
				}
			}),
			item, owned, wanted, needsChoice, model.getSellBack().contains(item) && canSellBack(item, reward));
		holder[0] = slot;
		slot.setToolTipText(itemTooltip(item, reward, owned, wanted));
		slot.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					itemMenu(item, reward, owned, wanted).show(slot, e.getX(), e.getY());
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					itemMenu(item, reward, owned, wanted).show(slot, e.getX(), e.getY());
				}
				else if (e.getButton() == MouseEvent.BUTTON1 && !owned)
				{
					plugin.setWanted(goalUnit(item, reward), !wanted);
				}
			}
		});
		return slot;
	}

	private JPopupMenu itemMenu(String item, Reward reward, boolean owned, boolean wanted)
	{
		JPopupMenu menu = new JPopupMenu();
		if (!owned)
		{
			JMenuItem goal = new JMenuItem(wanted ? "Remove from goal" : "Add to goal");
			goal.addActionListener(e -> plugin.setWanted(goalUnit(item, reward), !wanted));
			menu.add(goal);
		}
		JMenuItem own = new JMenuItem(owned ? "I don't have this" : "I already have this");
		own.addActionListener(e -> plugin.setOwned(item, !owned));
		menu.add(own);
		if (!owned && canSellBack(item, reward))
		{
			JCheckBoxMenuItem sell = new JCheckBoxMenuItem("Sell back after logging it", model.getSellBack().contains(item));
			sell.addActionListener(e -> plugin.setSellBack(List.of(item), sell.isSelected()));
			menu.add(sell);
		}

		if (!owned && reward.getOffers().size() > 1)
		{
			menu.addSeparator();
			JMenuItem title = new JMenuItem("Buy from:");
			title.setEnabled(false);
			menu.add(title);
			ButtonGroup group = new ButtonGroup();
			String preferred = model.getPreferredActivity().get(item);
			for (Reward.Offer offer : reward.getOffers())
			{
				String activityId = offer.getActivities().get(0);
				JRadioButtonMenuItem choice = new JRadioButtonMenuItem(activityName(activityId) + " (" + formatCost(price(offer)) + ")",
					offer.getActivities().contains(preferred));
				choice.addActionListener(e -> plugin.setPreferredActivity(item, activityId));
				group.add(choice);
				menu.add(choice);
			}
		}
		return menu;
	}

	/** One muted line whose tooltip lists materials and traded-in items, when there are any. */
	private JComponent detailsLine(ActivityPlan activityPlan)
	{
		List<String> lines = new ArrayList<>();
		Map<String, Long> materials = activityPlan.getCost().getMaterialsNet();
		materials.forEach((name, amount) ->
		{
			if (amount > 0)
			{
				lines.add(fmt(amount) + " " + name);
			}
		});
		if (!activityPlan.getPrerequisites().isEmpty())
		{
			lines.add("Buys first: " + String.join(", ", activityPlan.getPrerequisites()));
		}
		if (lines.isEmpty())
		{
			return null;
		}
		JLabel details = label("ⓘ Also needs materials", FontManager.getRunescapeSmallFont(), MUTED);
		details.setToolTipText("<html>" + String.join("<br>", lines) + "</html>");
		details.setBorder(new EmptyBorder(4, 0, 0, 0));
		return details;
	}

	// ------------------------------------------------------------------
	// Currency bars
	// ------------------------------------------------------------------

	/**
	 * One bar per currency: "have / need". On the home screen the colour says whether the goal is
	 * met; on a page the balance is shared with other goals, so the bar stays neutral.
	 */
	private void addCurrencyBars(Map<String, Long> needed, boolean accountWide)
	{
		for (Currency currency : model.getData().getCurrencies().values())
		{
			long need = needed.getOrDefault(currency.getId(), 0L);
			if (need <= 0)
			{
				continue;
			}
			Long have = model.getBalances().get(currency.getId());
			long current = have == null ? 0 : have;
			boolean done = have != null && current >= need;
			Color color = !accountWide ? NEUTRAL_BAR : done ? GOOD : ACCENT;

			JPanel box = new JPanel();
			box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
			box.setOpaque(false);
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			box.setBorder(new EmptyBorder(0, 0, 8, 0));

			JPanel top = row();
			top.add(label(currency.getName(), FontManager.getRunescapeSmallFont(), Color.WHITE), BorderLayout.WEST);
			top.add(label((have == null ? "?" : fmtShort(current)) + " / " + fmtShort(need), FontManager.getRunescapeSmallFont(),
				accountWide && done ? GOOD : MUTED), BorderLayout.EAST);
			box.add(top);
			box.add(Box.createVerticalStrut(3));
			box.add(new ProgressBar(need == 0 ? 0 : (double) current / need, color, 6));

			String tip = "<html>Have " + (have == null ? "unknown" : fmt(current)) + "<br>Goal " + fmt(need)
				+ "<br>Left " + fmt(Math.max(0, need - current))
				+ (have == null ? "<br>" + howToUpdate(currency) : "")
				+ (accountWide ? "" : "<br>Your balance is shared with your other goals")
				+ "<br><i>Right-click to set the amount yourself</i></html>";
			box.setToolTipText(tip);
			box.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					maybeShow(e);
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					maybeShow(e);
				}

				private void maybeShow(MouseEvent e)
				{
					if (e.isPopupTrigger())
					{
						balanceMenu(currency).show(box, e.getX(), e.getY());
					}
				}
			});
			content.add(box);
		}
	}

	private JPopupMenu balanceMenu(Currency currency)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem set = new JMenuItem("Set amount...");
		set.addActionListener(e ->
		{
			Object input = JOptionPane.showInputDialog(this, currency.getName() + " you have:", "Reward Shop Planner",
				JOptionPane.PLAIN_MESSAGE, null, null, model.getBalances().getOrDefault(currency.getId(), 0L));
			Long value = input == null ? null : parseAmount(input.toString());
			if (value != null)
			{
				plugin.setManualBalance(currency.getId(), value);
			}
		});
		menu.add(set);
		if (model.getManualBalances().containsKey(currency.getId()))
		{
			JMenuItem clear = new JMenuItem("Forget typed amount");
			clear.addActionListener(e -> plugin.setManualBalance(currency.getId(), null));
			menu.add(clear);
		}
		return menu;
	}

	private static String howToUpdate(Currency currency)
	{
		if (currency.getNotes() != null && (currency.getSource() == Currency.Source.INTERFACE || currency.getSource() == Currency.Source.COUNTER))
		{
			return currency.getNotes();
		}
		return currency.getSource() == Currency.Source.ITEM ? "Open your bank to count it." : "Log in to read it.";
	}

	// ------------------------------------------------------------------
	// Text
	// ------------------------------------------------------------------

	private String itemTooltip(String item, Reward reward, boolean owned, boolean wanted)
	{
		List<String> lines = new ArrayList<>();
		lines.add("<b>" + escape(item) + "</b>");
		if (owned)
		{
			lines.add("You have this" + (model.getOwnedEdited().contains(item) ? " (marked by you)" : ""));
		}
		else
		{
			lines.add(priceText(reward));
			lines.add(wanted ? "In your goal" : "Click to add to your goal");
		}
		if (!reward.getConsumes().isEmpty())
		{
			lines.add("Trades in: " + escape(String.join(", ", reward.getConsumes())));
		}
		if (!reward.getMaterials().isEmpty())
		{
			lines.add("Materials: " + escape(materialsText(reward.getMaterials())));
		}
		if (reward.getRequirements() != null && !reward.getRequirements().isEmpty())
		{
			lines.add("Requires: " + escape(String.join(", ", reward.getRequirements())));
		}
		if (reward.getSet() != null)
		{
			List<String> pieces = setPieces(reward);
			lines.add("Bought as a set: " + pieces.size() + " pieces, one price. Clicking one picks them all.");
		}
		String sellBack = sellBackText(item, reward);
		if (sellBack != null)
		{
			lines.add(sellBack);
		}
		return "<html>" + String.join("<br>", lines) + "</html>";
	}

	/** "500 each of 9 kinds of logs" when many materials share an amount, else the full list. */
	private static String materialsText(Map<String, Integer> materials)
	{
		Set<Integer> amounts = new java.util.HashSet<>(materials.values());
		boolean allLogs = materials.keySet().stream().allMatch(name -> name.endsWith(" logs"));
		if (materials.size() >= 4 && amounts.size() == 1 && allLogs)
		{
			return fmt(amounts.iterator().next()) + " each of " + materials.size() + " kinds of logs";
		}
		return materials.entrySet().stream()
			.map(e -> fmt(e.getValue()) + " " + e.getKey())
			.collect(Collectors.joining(", "));
	}

	/** What a click adds or removes: the item, or its whole set when it is bought as one. */
	private List<String> goalUnit(String item, Reward reward)
	{
		return reward.getSet() == null ? List.of(item) : setPieces(reward);
	}

	private List<String> setPieces(Reward reward)
	{
		return model.getData().getRewards().values().stream()
			.filter(r -> reward.getSet().equals(r.getSet()))
			.map(Reward::getName)
			.collect(Collectors.toList());
	}

	/** The offer the player buys from: the only one, or the one from the shop they picked. */
	private Reward.Offer chosenOffer(String item, Reward reward)
	{
		if (reward.getOffers().size() == 1)
		{
			return reward.getOffers().get(0);
		}
		String preferred = model.getPreferredActivity().get(item);
		return reward.getOffers().stream().filter(o -> o.getActivities().contains(preferred)).findFirst().orElse(null);
	}

	/** Explains an item's sell-back with real numbers, for this account type. Null when it has none. */
	private String sellBackText(String item, Reward reward)
	{
		Reward.Refund refund = reward.getRefund();
		if (refund != null && ((refund.isNotForUim() && model.getAccountMode() == AccountMode.ULTIMATE_IRONMAN)
			|| (refund.isOnlyIron() && !model.getAccountMode().isIron())))
		{
			return "Can be sold back to the shop, but not by " + modeName().toLowerCase(Locale.ROOT) + " accounts.";
		}
		if (!canSellBack(item, reward))
		{
			return null;
		}
		Reward.Offer offer = chosenOffer(item, reward);
		if (offer == null)
		{
			return "Can be sold back to the shop after you log it.";
		}
		Map<String, Long> back = PlannerCalculator.sellBackValue(reward, offer, model.getAccountMode(), model.isKaramjaGloves());
		Map<String, Long> spent = new LinkedHashMap<>();
		price(offer).forEach((currency, amount) -> spent.put(currency, amount - back.getOrDefault(currency, 0L)));
		if (model.getSellBack().contains(item))
		{
			return "↩ Selling back for " + formatAmounts(back) + " after you log it: you really spend " + formatAmounts(spent) + ".";
		}
		return "Shop buys it back for " + formatAmounts(back) + ". Right-click to sell it back after logging it.";
	}

	/** Whether the item can be sold back to its shop by this account (any shop, while none is picked). */
	private boolean canSellBack(String item, Reward reward)
	{
		if (reward == null)
		{
			return false;
		}
		Reward.Offer chosen = chosenOffer(item, reward);
		List<Reward.Offer> offers = chosen != null ? List.of(chosen) : reward.getOffers();
		return offers.stream().anyMatch(o -> !PlannerCalculator.sellBackValue(reward, o, model.getAccountMode(), model.isKaramjaGloves()).isEmpty());
	}

	/** What the player pays at this shop, with Karamja gloves when they wear them. */
	private Map<String, Integer> price(Reward.Offer offer)
	{
		return offer.costFor(model.isKaramjaGloves());
	}

	/** "↩ N currency back from sell-backs" for a page, when its goal includes any. */
	private JComponent sellBackLine(ActivityPlan activityPlan)
	{
		Map<String, Long> back = new LinkedHashMap<>();
		activityPlan.getCost().getGross().forEach((currency, gross) ->
		{
			long returned = gross - activityPlan.getCost().getNet(currency);
			if (returned > 0)
			{
				back.put(currency, returned);
			}
		});
		if (back.isEmpty())
		{
			return null;
		}
		JLabel label = wrapped("↩ " + formatAmounts(back) + " back from sell-backs", GOOD);
		label.setToolTipText("<html>You pay the full price first, then sell the items marked ↩ back to the shop<br>"
			+ "once their slot is logged. The bars above already count the money back,<br>"
			+ "and never go below what you must hold to buy them one at a time.</html>");
		label.setBorder(new EmptyBorder(2, 0, 2, 0));
		return label;
	}

	private String formatAmounts(Map<String, Long> amounts)
	{
		return amounts.entrySet().stream()
			.map(e -> fmt(e.getValue()) + " " + currencyName(e.getKey()))
			.collect(Collectors.joining(" + "));
	}

	private String modeName()
	{
		switch (model.getAccountMode())
		{
			case IRONMAN:
				return "Ironman";
			case ULTIMATE_IRONMAN:
				return "Ultimate ironman";
			default:
				return "Main";
		}
	}

	private String priceText(Reward reward)
	{
		switch (reward.getType())
		{
			case MILESTONE:
				return reward.getMilestone() == null ? "Milestone"
					: "At " + fmt(reward.getMilestone().getAmount()) + " " + currencyName(reward.getMilestone().getCounter());
			case RANDOM:
				return "Random reward";
			default:
				if (reward.getOffers().size() > 1)
				{
					return reward.getOffers().stream()
						.map(o -> formatCost(price(o)) + " (" + activityName(o.getActivities().get(0)) + ")")
						.collect(Collectors.joining(" or "));
				}
				if (reward.getOffers().isEmpty())
				{
					return "";
				}
				Reward.Offer offer = reward.getOffers().get(0);
				return formatCost(price(offer)) + (reward.getSet() != null ? " for the whole set" : "")
					+ (model.isKaramjaGloves() && offer.getKaramjaGlovesCost() != null ? " (with Karamja gloves)" : "");
		}
	}

	private boolean hasShopChoice(String item, Reward reward)
	{
		String preferred = model.getPreferredActivity().get(item);
		return preferred != null && reward.getOffers().stream().anyMatch(o -> o.getActivities().contains(preferred));
	}

	/** Wanted slots of a page that are not owned yet. */
	private List<String> wantedMissing(Activity activity)
	{
		return activity.getClogItems().stream()
			.filter(i -> model.getWanted().contains(i) && !model.getOwned().contains(i))
			.collect(Collectors.toList());
	}

	private String formatCost(Map<String, Integer> cost)
	{
		if (cost == null)
		{
			return "";
		}
		return cost.entrySet().stream()
			.map(e -> fmt(e.getValue()) + " " + currencyName(e.getKey()))
			.collect(Collectors.joining(" + "));
	}

	private String currencyName(String id)
	{
		Currency currency = model.getData().getCurrencies().get(id);
		return currency == null ? id : currency.getName();
	}

	private String activityName(String id)
	{
		Activity activity = model.getData().getActivities().get(id);
		return activity == null ? id : activity.getName();
	}

	private static String fmt(long value)
	{
		return QuantityFormatter.formatNumber(value);
	}

	/** Full number below 100k, otherwise 123.4K / 1.23M so a have/need pair fits the panel. */
	private static String fmtShort(long value)
	{
		if (value < 100_000)
		{
			return fmt(value);
		}
		if (value < 1_000_000)
		{
			return String.format(Locale.US, "%.1fK", value / 1_000.0);
		}
		return String.format(Locale.US, "%.2fM", value / 1_000_000.0);
	}

	private static Long parseAmount(String text)
	{
		String trimmed = text == null ? "" : text.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}
		try
		{
			return QuantityFormatter.parseQuantity(trimmed);
		}
		catch (ParseException e)
		{
			return null;
		}
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	// ------------------------------------------------------------------
	// Components
	// ------------------------------------------------------------------

	private static JPanel row()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private static JLabel label(String text, Font font, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel wrapped(String text, Color color)
	{
		return label("<html><div style='width:" + WRAP_WIDTH + "px'>" + escape(text) + "</div></html>",
			FontManager.getRunescapeSmallFont(), color);
	}

	private static JComponent sectionTitle(String title)
	{
		JLabel label = label(title.toUpperCase(Locale.ROOT), FontManager.getRunescapeSmallFont(), MUTED);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return label;
	}

	/** Small clickable text, orange on hover. */
	private static JLabel link(String text, Runnable action)
	{
		JLabel link = label(text, FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				link.setForeground(ACCENT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		return link;
	}
}
