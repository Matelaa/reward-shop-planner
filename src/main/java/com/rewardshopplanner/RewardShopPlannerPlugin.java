package com.rewardshopplanner;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.rewardshopplanner.calc.AccountMode;
import com.rewardshopplanner.calc.Plan;
import com.rewardshopplanner.calc.PlannerCalculator;
import com.rewardshopplanner.calc.PlannerInput;
import com.rewardshopplanner.data.Activity;
import com.rewardshopplanner.data.Currency;
import com.rewardshopplanner.data.Material;
import com.rewardshopplanner.data.RewardData;
import com.rewardshopplanner.overlay.ProgressOverlay;
import com.rewardshopplanner.overlay.ProgressTracker;
import com.rewardshopplanner.tracking.ChompyKillParser;
import com.rewardshopplanner.tracking.CollectionLogMatcher;
import com.rewardshopplanner.tracking.BalanceTextParser;
import com.rewardshopplanner.tracking.PlayerState;
import com.rewardshopplanner.ui.PanelModel;
import com.rewardshopplanner.ui.PlannerPanel;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Reward Shop Planner",
	description = "Plans the points needed to finish the reward-shop slots of your collection log",
	tags = {"collection", "log", "clog", "minigame", "points", "reward", "shop", "planner", "ironman"}
)
public class RewardShopPlannerPlugin extends Plugin
{
	private static final String STATE_KEY = "state";
	private static final Pattern NEW_LOG_ITEM = Pattern.compile("New item added to your collection log: (.+)");
	private static final String CHOMPY_COUNTER = "chompy_kills";
	private static final String ANIMA_BARK = "anima_bark";
	private static final String PHEASANT_FEATHER = "pheasant_feather";
	/** "You've been awarded 9 Anima-infused bark." Matched loosely: only the part that matters. */
	private static final Pattern BARK_AWARD = Pattern.compile("been awarded ([\\d,]+) Anima-infused bark");
	/** The game's inline chat formatting markers, e.g. "@mes_hl_blu@" (highlighted blue). */
	private static final Pattern CHAT_MARKER = Pattern.compile("@[a-z_]+@");
	/** "You collect a pheasant egg and find a feather too." */
	private static final Pattern FEATHER_FOUND = Pattern.compile("find (?:a|\\d+) feathers? too");

	/** Interface text showing a balance: component, currency id, and how to find the number in it. */
	private static final class BalanceText
	{
		final int component;
		final String currency;
		final Pattern pattern;

		BalanceText(int component, String currency, Pattern pattern)
		{
			this.component = component;
			this.currency = currency;
			this.pattern = pattern;
		}
	}

	private static final String PIZAZZ = "Pizazz Points";

	/** Game messages that state a current balance: pattern (group 1 = number) and currency id. */
	private static final Object[][] CHAT_BALANCES = {
		// Mahogany Homes, after each contract: the total is the current point balance
		{BalanceTextParser.MAHOGANY_CONTRACT, "carpenter_points"},
	};

	/**
	 * Balances the game only shows on screen, not in a var. VarPlayers 261 and 263 (IF1/IF3), which
	 * briefly hold Pest Control and Pizazz points, are shared interface scratch vars and are not used.
	 */
	private static final BalanceText[] BALANCE_TEXTS = {
		// Last Man Standing: reward shop footer
		new BalanceText(InterfaceID.BrRewardShop.BOTTOM, "lms_points", BalanceTextParser.after("Points")),
		new BalanceText(InterfaceID.BrRewardShop.BOTTOM, "lms_wins", BalanceTextParser.after("Wins")),
		// Pest Control: reward shop footer and the lander overlay
		new BalanceText(InterfaceID.PestRewardshop.BOTTOM, "pc_points", BalanceTextParser.after("Points")),
		new BalanceText(InterfaceID.PestLanderOverlay.PEST_LANDER_OVER_POINTS, "pc_points", BalanceTextParser.first()),
		// Mage Training Arena: lobby overlay, each room's counter, the shop's "Room: have/price" footer
		new BalanceText(InterfaceID.MagictrainingMain.MAGICTRAIN_TELE_POINTS, "mta_tele", BalanceTextParser.first()),
		new BalanceText(InterfaceID.MagictrainingMain.MAGICTRAIN_ALCH_POINTS, "mta_alch", BalanceTextParser.first()),
		new BalanceText(InterfaceID.MagictrainingMain.MAGICTRAIN_ENCH_POINTS, "mta_ench", BalanceTextParser.first()),
		new BalanceText(InterfaceID.MagictrainingMain.MAGICTRAIN_GRAVE_POINTS, "mta_grave", BalanceTextParser.first()),
		new BalanceText(InterfaceID.MagictrainingTele.MAGICTRAIN_TELE_PTS, "mta_tele", BalanceTextParser.first()),
		new BalanceText(InterfaceID.MagictrainingAlchem.MAGICTRAIN_ALCHEM_PTS, "mta_alch", BalanceTextParser.first()),
		new BalanceText(InterfaceID.MagictrainingEncha.MAGICTRAIN_ENCHA_PTS, "mta_ench", BalanceTextParser.first()),
		new BalanceText(InterfaceID.MagictrainingGrave.MAGICTRAIN_GRAVE_PTS, "mta_grave", BalanceTextParser.first()),
		new BalanceText(InterfaceID.MagictrainingShop.COSTS, "mta_tele", BalanceTextParser.after("Telekinetic")),
		new BalanceText(InterfaceID.MagictrainingShop.COSTS, "mta_alch", BalanceTextParser.after("Alchemist")),
		new BalanceText(InterfaceID.MagictrainingShop.COSTS, "mta_ench", BalanceTextParser.after("Enchantment")),
		new BalanceText(InterfaceID.MagictrainingShop.COSTS, "mta_grave", BalanceTextParser.after("Graveyard")),
		// Volcanic Mine: Petrified Pete's shop ("Points: 0"). Varbit 5934 (FOSSIL_MINE_TEMP_PTS)
		// only holds the points of the game in progress, not the balance.
		new BalanceText(InterfaceID.FossilVolcanicShop.POINTS, "vm_points", BalanceTextParser.after("Points")),
		// Mahogany Homes: reward shop footer ("Carpenter Points: 1,525")
		new BalanceText(InterfaceID.ConstructionContractShop.BOTTOM, "carpenter_points", BalanceTextParser.after("Points")),
		// Mage Training Arena: Progress Hat dialogue ("You have: 0 Telekinetic, ... Pizazz Points.")
		new BalanceText(InterfaceID.ChatLeft.TEXT, "mta_tele", BalanceTextParser.before("Telekinetic", PIZAZZ)),
		new BalanceText(InterfaceID.ChatLeft.TEXT, "mta_alch", BalanceTextParser.before("Alchemist", PIZAZZ)),
		new BalanceText(InterfaceID.ChatLeft.TEXT, "mta_ench", BalanceTextParser.before("Enchantment", PIZAZZ)),
		new BalanceText(InterfaceID.ChatLeft.TEXT, "mta_grave", BalanceTextParser.before("Graveyard", PIZAZZ)),
	};
	/** Dialog boxes whose text is checked for the chompy kill total. */
	private static final int[][] DIALOG_TEXTS = {
		{InterfaceID.OBJECTBOX, InterfaceID.Objectbox.TEXT},
		{InterfaceID.OBJECTBOX_DOUBLE, InterfaceID.ObjectboxDouble.TEXT},
		{InterfaceID.MESSAGEBOX, InterfaceID.Messagebox.TEXT},
	};
	/**
	 * Client script the collection log runs once per obtained slot (args: [?, itemId, quantity])
	 * when it transmits the whole log, e.g. when RuneProfile/WikiSync or the log's own search
	 * enumerate it. No gameval constant exists for it.
	 */
	private static final int COLLECTION_ITEM_TRANSMIT = 4100;
	/** The transmit streams over several ticks; apply the harvest this long after the last item. */
	private static final int HARVEST_SETTLE_TICKS = 3;
	/**
	 * A transmit this big is the whole log rather than a stray call, so every page counts as synced.
	 * Kept low: a new account may only have a handful of slots.
	 */
	private static final int FULL_LOG_MIN_ITEMS = 10;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private RewardShopPlannerConfig config;

	@Inject
	private Gson gson;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	private RewardData data;
	private PlannerCalculator calculator;
	private CollectionLogMatcher matcher;
	private PlannerPanel panel;
	private NavigationButton navButton;

	private PlayerState state = new PlayerState();

	private final Map<Integer, Currency> itemCurrencies = new HashMap<>();
	private final Map<Integer, Currency> varpCurrencies = new HashMap<>();
	/** Material names by (unnoted) item id. */
	private final Map<Integer, String> materialsById = new HashMap<>();
	private final Map<Integer, Currency> varbitCurrencies = new HashMap<>();
	/** Either varbit of a two-varbit currency -> that currency (Barbarian Assault honour points). */
	private final Map<Integer, Currency> compositeCurrencies = new HashMap<>();
	/** Item id -> data item names on the covered log pages (one id can sit on two pages). */
	private final Map<Integer, List<String>> logItemsById = new HashMap<>();
	/** Obtained item ids collected from {@link #COLLECTION_ITEM_TRANSMIT}; client thread only. */
	private final Set<Integer> transmitHarvest = new HashSet<>();
	private int harvestApplyTick = -1;
	/** On-screen progress: which pages to show, and the overlay drawing them. */
	private ProgressTracker progress;
	private ProgressOverlay progressOverlay;
	/** The inventory was read this session, so its item counts are real rather than last session's. */
	private boolean inventorySeen;
	/** Bark from "You've been awarded" messages not yet matched with the inventory (client thread). */
	private long barkAwarded;
	private long barkInInventoryBefore;
	private int barkAwardTick;
	/** Last tick the game sent the Forestry kit's contents (it does while the kit's window is open). */
	private int kitReadTick = -1;

	@Override
	protected void startUp() throws Exception
	{
		data = RewardData.load(gson);
		calculator = new PlannerCalculator(data);
		matcher = new CollectionLogMatcher(data);
		for (Currency currency : data.getCurrencies().values())
		{
			if (!PlayerState.isTracked(currency))
			{
				continue;
			}
			switch (currency.getSource())
			{
				case ITEM:
					itemCurrencies.put(currency.getItemId(), currency);
					break;
				case VARP:
					varpCurrencies.put(currency.getVarId(), currency);
					break;
				case VARBIT:
					varbitCurrencies.put(currency.getVarId(), currency);
					break;
				case VARBIT_COMPOSITE:
					for (int varbit : currency.getVarIds())
					{
						compositeCurrencies.put(varbit, currency);
					}
					break;
				default:
			}
		}
		for (Material material : data.getMaterials().values())
		{
			materialsById.put(material.getItemId(), material.getName());
		}
		for (Activity activity : data.getActivities().values())
		{
			int[] ids = activity.getClogItemIds();
			for (int i = 0; ids != null && i < ids.length; i++)
			{
				if (ids[i] > 0)
				{
					logItemsById.computeIfAbsent(ids[i], k -> new ArrayList<>()).add(activity.getClogItems().get(i));
				}
			}
		}

		GameIcons icons = new GameIcons(itemManager, spriteManager);
		panel = new PlannerPanel(this, icons);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Reward Shop Planner")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		progress = new ProgressTracker(this, config, configManager, icons, infoBoxManager, data);
		progressOverlay = new ProgressOverlay(this, config, icons, data, progress::shown);
		overlayManager.add(progressOverlay);

		loadState();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::readGameValues);
		}
		refresh();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(progressOverlay);
		progressOverlay = null;
		progress.shutDown();
		progress = null;
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		itemCurrencies.clear();
		materialsById.clear();
		varpCurrencies.clear();
		varbitCurrencies.clear();
		compositeCurrencies.clear();
		logItemsById.clear();
		transmitHarvest.clear();
		harvestApplyTick = -1;
		state = new PlayerState();
	}

	@Provides
	RewardShopPlannerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RewardShopPlannerConfig.class);
	}

	// ------------------------------------------------------------------
	// Game events (client thread)
	// ------------------------------------------------------------------

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadState();
		refresh();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::readGameValues);
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			progress.onLogout();
			inventorySeen = false;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.IRONMAN)
		{
			if (detectAccountMode())
			{
				saveState();
			}
			refresh();
			return;
		}
		if (event.getVarbitId() == VarbitID.ATJUN_EASY_REWARD)
		{
			if (readKaramjaGloves())
			{
				saveState();
			}
			refresh();
			return;
		}
		Currency composite = event.getVarbitId() != -1 ? compositeCurrencies.get(event.getVarbitId()) : null;
		if (composite != null)
		{
			if (updateVarBalance(composite, readComposite(composite)))
			{
				saveState();
				refresh();
			}
			return;
		}
		Currency currency = event.getVarbitId() != -1
			? varbitCurrencies.get(event.getVarbitId())
			: varpCurrencies.get(event.getVarpId());
		if (currency != null && updateVarBalance(currency, event.getValue()))
		{
			saveState();
			refresh();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		Map<String, Long> target;
		synchronized (this)
		{
			boolean materialsChanged = false;
			if (event.getContainerId() == InventoryID.FORESTRY_SHOP_LOG_STORAGE)
			{
				// the log basket (and Forestry basket) share this storage; it only holds logs
				if (!countMaterials(event.getItemContainer(), state.getLogBasketMaterials()))
				{
					return;
				}
				saveState();
				refresh();
				return;
			}
			else if (event.getContainerId() == InventoryID.BANK)
			{
				target = state.getBankItems();
				materialsChanged = countMaterials(event.getItemContainer(), state.getBankMaterials());
			}
			else if (event.getContainerId() == InventoryID.INV)
			{
				target = state.getInventoryItems();
				inventorySeen = true;
				materialsChanged = countMaterials(event.getItemContainer(), state.getInventoryMaterials());
			}
			else if (event.getContainerId() == InventoryID.FORESTRY_KIT)
			{
				target = state.getForestryKitItems();
				kitReadTick = client.getTickCount();
			}
			else
			{
				return;
			}

			ItemContainer container = event.getItemContainer();
			boolean changed = materialsChanged;
			for (Map.Entry<Integer, Currency> entry : itemCurrencies.entrySet())
			{
				long count = container.count(entry.getKey());
				Long previous = target.put(entry.getValue().getId(), count);
				if (!Objects.equals(previous, count))
				{
					changed = true;
					// a fresh reading from the game replaces an amount the player typed
					state.getManualBalances().remove(entry.getValue().getId());
				}
			}
			if (!changed)
			{
				return;
			}
		}
		saveState();
		refresh();
	}

	/**
	 * Hands the on-screen progress this tick's item currencies: the inventory, and the bank plus
	 * Forestry kit, so it can tell a real gain from a withdrawal. Waits for the first inventory
	 * reading of the session; before that the counts are last session's.
	 */
	private void feedItemCounts()
	{
		if (!inventorySeen)
		{
			return;
		}
		Map<String, Long> inventory = new HashMap<>();
		Map<String, Long> stored = new HashMap<>();
		synchronized (this)
		{
			for (Currency currency : itemCurrencies.values())
			{
				inventory.put(currency.getId(), state.getInventoryItems().getOrDefault(currency.getId(), 0L));
				long bank = state.getBankItems().getOrDefault(currency.getId(), 0L);
				long kit = state.getForestryKitItems() == null ? 0 : state.getForestryKitItems().getOrDefault(currency.getId(), 0L);
				stored.put(currency.getId(), bank + kit);
			}
		}
		progress.onItemCounts(inventory, stored);
	}

	/** An item currency picked up next to the player is the player's own drop coming back. */
	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		Currency currency = itemCurrencies.get(event.getItem().getId());
		Player player = client.getLocalPlayer();
		if (currency == null || player == null || event.getTile().getWorldLocation().distanceTo(player.getWorldLocation()) > 1)
		{
			return;
		}
		progress.onPickup(currency.getId(), event.getItem().getQuantity());
	}

	/** Every number read from the game passes here, so the on-screen progress sees what was earned. */
	private void noteGameValue(String currencyId, Long before, long now)
	{
		ProgressTracker tracker = progress;
		if (tracker != null)
		{
			tracker.onGameValue(currencyId, before, now);
		}
	}

	/** Counts every material in a container, noted ones included; true when a count changed. */
	private boolean countMaterials(ItemContainer container, Map<String, Long> target)
	{
		Map<String, Long> counts = new HashMap<>();
		for (String name : materialsById.values())
		{
			counts.put(name, 0L);
		}
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			String name = materialsById.get(itemManager.canonicalize(item.getId()));
			if (name != null)
			{
				counts.merge(name, (long) item.getQuantity(), Long::sum);
			}
		}
		if (counts.equals(target))
		{
			return false;
		}
		target.clear();
		target.putAll(counts);
		return true;
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST && !viewingAnotherPlayersLog())
		{
			// the page's widgets are finished on the next client cycle
			clientThread.invokeLater(this::syncCollectionLogPage);
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != COLLECTION_ITEM_TRANSMIT || event.getScriptEvent() == null || viewingAnotherPlayersLog())
		{
			return;
		}
		Object[] args = event.getScriptEvent().getArguments();
		if (args == null || args.length < 2 || !(args[1] instanceof Integer))
		{
			return;
		}
		int itemId = (Integer) args[1];
		if (itemId > 0)
		{
			transmitHarvest.add(itemId);
			harvestApplyTick = client.getTickCount() + HARVEST_SETTLE_TICKS;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		progress.onGameTick(client);
		settleBarkAward();
		feedItemCounts();
		readInterfaceBalances();
		if (harvestApplyTick < 0 || client.getTickCount() < harvestApplyTick)
		{
			return;
		}
		harvestApplyTick = -1;
		int marked = 0;
		int pagesSynced = 0;
		synchronized (this)
		{
			for (int itemId : transmitHarvest)
			{
				List<String> names = logItemsById.get(itemId);
				if (names == null)
				{
					// another version of a logged item (e.g. the Volcanic Mine prospector kit): match by name
					String resolved = matcher.resolveChatName(itemName(itemId));
					names = resolved == null ? List.of() : List.of(resolved);
				}
				for (String name : names)
				{
					// the log is the source of truth: it also drops a "I don't have this" correction
					if (!state.getOwned().contains(name) || state.getOwnedOverrides().containsKey(name))
					{
						state.setLogOwned(name, true);
						marked++;
					}
				}
			}
			// The transmit lists the whole log at once (e.g. the log's Search), so every page is
			// now known, including pages where nothing was obtained yet.
			if (transmitHarvest.size() >= FULL_LOG_MIN_ITEMS)
			{
				for (Activity activity : data.getActivities().values())
				{
					if (state.getSyncedPages().add(activity.getClogPage()))
					{
						pagesSynced++;
					}
				}
			}
		}
		log.debug("Reward Shop Planner: collection log transmit had {} obtained items, {} newly marked owned, {} pages synced",
			transmitHarvest.size(), marked, pagesSynced);
		transmitHarvest.clear();
		if (marked > 0 || pagesSynced > 0)
		{
			saveState();
			refresh();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = cleanChat(event.getMessage());
		// checked before the type filter: the award isn't necessarily a plain game message
		if (trackForestryAward(message))
		{
			return;
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		if (trackChompyKills(message) || trackChatBalance(message))
		{
			return;
		}
		Matcher m = NEW_LOG_ITEM.matcher(message);
		if (event.getType() != ChatMessageType.GAMEMESSAGE || !m.matches())
		{
			return;
		}
		String item = matcher.resolveChatName(m.group(1).trim());
		if (item == null)
		{
			return;
		}
		synchronized (this)
		{
			if (state.getOwned().contains(item))
			{
				return;
			}
			state.setLogOwned(item, true);
		}
		saveState();
		refresh();
	}

	/**
	 * "You've been awarded 12 Anima-infused bark." is a real gain. With the Forestry kit in the
	 * inventory the bark goes straight into the kit, which the game doesn't report, so whatever
	 * doesn't reach the inventory by the next tick is added to the kit's count.
	 */
	/**
	 * Chat text without formatting: the usual &lt;col&gt; tags, and the game's newer inline markers
	 * like "@mes_hl_blu@" in "You've been awarded @mes_hl_blu@11 Anima-infused bark".
	 */
	static String cleanChat(String message)
	{
		return CHAT_MARKER.matcher(Text.removeTags(message)).replaceAll("");
	}

	private boolean trackForestryAward(String message)
	{
		if (FEATHER_FOUND.matcher(message).find())
		{
			// the feathers go to the inventory, where they are counted; this only marks the gain
			progress.earned(PHEASANT_FEATHER);
			return true;
		}
		Matcher m = BARK_AWARD.matcher(message);
		if (!m.find())
		{
			return false;
		}
		long amount = Long.parseLong(m.group(1).replace(",", ""));
		progress.earned(ANIMA_BARK);
		synchronized (this)
		{
			if (barkAwarded == 0)
			{
				barkInInventoryBefore = state.getInventoryItems().getOrDefault(ANIMA_BARK, 0L);
			}
			barkAwarded += amount;
			barkAwardTick = client.getTickCount();
		}
		return true;
	}

	/** One tick after a bark award: the part that didn't reach the inventory went into the kit. */
	private void settleBarkAward()
	{
		long toKit;
		synchronized (this)
		{
			if (barkAwarded == 0 || client.getTickCount() <= barkAwardTick)
			{
				return;
			}
			long reachedInventory = Math.max(0, state.getInventoryItems().getOrDefault(ANIMA_BARK, 0L) - barkInInventoryBefore);
			toKit = barkAwarded - reachedInventory;
			barkAwarded = 0;
			// with the kit's window open the game sends its real contents, which already include the award
			if (toKit <= 0 || kitReadTick >= barkAwardTick)
			{
				return;
			}
			state.addToForestryKit(ANIMA_BARK, toKit);
		}
		saveState();
		refresh();
	}

	/**
	 * True while the collection log shown is another player's (the log book in someone else's
	 * house); nothing from it may be recorded as the player's own.
	 */
	private boolean viewingAnotherPlayersLog()
	{
		return client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
	}

	/** Balances stated in chat (see {@link #CHAT_BALANCES}). */
	private boolean trackChatBalance(String message)
	{
		for (Object[] entry : CHAT_BALANCES)
		{
			Long value = BalanceTextParser.read(message, (Pattern) entry[0]);
			if (value != null)
			{
				setGameBalance((String) entry[1], value);
				return true;
			}
		}
		return false;
	}

	/**
	 * Stores a balance the game showed as text or in chat (kept with typed balances, since no var
	 * holds it). Saved in the normal config batch: it can change every few seconds while playing.
	 */
	private void setGameBalance(String currencyId, long value)
	{
		synchronized (this)
		{
			Long before = state.getManualBalances().put(currencyId, value);
			noteGameValue(currencyId, before, value);
			if (Long.valueOf(value).equals(before))
			{
				return;
			}
		}
		saveState();
		refresh();
	}

	/**
	 * Chompy kills: "Check kills" on an ogre bow gives the total; each kill adds a notch message.
	 * Stored as the counter's balance so the player can also correct it by hand.
	 */
	private boolean trackChompyKills(String message)
	{
		Long total = ChompyKillParser.parseTotal(message);
		if (total == null && ChompyKillParser.isKill(message))
		{
			synchronized (this)
			{
				Long known = state.getManualBalances().get(CHOMPY_COUNTER);
				if (known == null)
				{
					return true; // the total is unknown until the bow is checked
				}
				total = known + 1;
				progress.earned(CHOMPY_COUNTER);
			}
		}
		if (total == null)
		{
			return false;
		}
		setGameBalance(CHOMPY_COUNTER, total);
		return true;
	}

	/**
	 * Reads balances shown as text by open interfaces (see {@link #BALANCE_TEXTS}). Runs every
	 * tick while one is open, so purchases and new points are picked up too.
	 */
	private void readInterfaceBalances()
	{
		boolean changed = false;
		Map<Integer, String> texts = new HashMap<>();
		for (BalanceText source : BALANCE_TEXTS)
		{
			String text = texts.computeIfAbsent(source.component, this::visibleText);
			if (text == null)
			{
				continue;
			}
			Long value = BalanceTextParser.read(text, source.pattern);
			synchronized (this)
			{
				changed |= putIfChanged(source.currency, value);
			}
		}
		if (changed)
		{
			saveState();
			refresh();
		}
	}

	/** All text under a component, or null when it is not open. */
	private String visibleText(int component)
	{
		Widget widget = client.getWidget(component);
		if (widget == null || widget.isHidden())
		{
			return null;
		}
		StringBuilder text = new StringBuilder();
		collectText(widget, text, 0);
		return text.toString();
	}

	private boolean putIfChanged(String currencyId, Long value)
	{
		if (value == null)
		{
			return false;
		}
		Long before = state.getManualBalances().put(currencyId, value);
		noteGameValue(currencyId, before, value);
		return !value.equals(before);
	}

	private static void collectText(Widget widget, StringBuilder out, int depth)
	{
		if (widget == null || depth > 4)
		{
			return;
		}
		if (widget.getText() != null)
		{
			out.append(Text.removeTags(widget.getText().replace("<br>", " "))).append(' ');
		}
		for (Widget[] children : new Widget[][]{widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget child : children)
				{
					collectText(child, out, depth + 1);
				}
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		for (int[] dialog : DIALOG_TEXTS)
		{
			if (event.getGroupId() == dialog[0])
			{
				// the text is filled in after the interface loads
				int textComponent = dialog[1];
				clientThread.invokeLater(() ->
				{
					Widget text = client.getWidget(textComponent);
					if (text != null && text.getText() != null)
					{
						trackChompyKills(Text.removeTags(text.getText().replace("<br>", " ")));
					}
				});
				return;
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (RewardShopPlannerConfig.GROUP.equals(event.getGroup()))
		{
			refresh();
		}
	}

	private void readGameValues()
	{
		boolean changed = detectAccountMode();
		changed |= readKaramjaGloves();
		for (Currency currency : varpCurrencies.values())
		{
			changed |= updateVarBalance(currency, client.getVarpValue(currency.getVarId()));
		}
		for (Currency currency : varbitCurrencies.values())
		{
			changed |= updateVarBalance(currency, client.getVarbitValue(currency.getVarId()));
		}
		for (Currency currency : new HashSet<>(compositeCurrencies.values()))
		{
			changed |= updateVarBalance(currency, readComposite(currency));
		}
		if (changed)
		{
			saveState();
		}
		refresh();
	}

	/** Reads the account type from the game and remembers it; true when it changed. */
	private synchronized boolean detectAccountMode()
	{
		// 0 normal, 1 ironman, 2 ultimate, 3 hardcore, 4 group, 5 hardcore group, 6 unranked group
		switch (client.getVarbitValue(VarbitID.IRONMAN))
		{
			case 0:
				return state.setAccountMode(AccountMode.MAIN);
			case 2:
				return state.setAccountMode(AccountMode.ULTIMATE_IRONMAN);
			default:
				return state.setAccountMode(AccountMode.IRONMAN);
		}
	}

	/** Remembers whether the Karamja gloves were claimed; true when that changed. */
	private synchronized boolean readKaramjaGloves()
	{
		boolean claimed = client.getVarbitValue(VarbitID.ATJUN_EASY_REWARD) == 1;
		if (state.isKaramjaGlovesClaimed() == claimed)
		{
			return false;
		}
		state.setKaramjaGlovesClaimed(claimed);
		return true;
	}

	private synchronized boolean updateVarBalance(Currency currency, long value)
	{
		Long previous = state.getVarBalances().put(currency.getId(), value);
		noteGameValue(currency.getId(), previous, value);
		if (Objects.equals(previous, value))
		{
			return false;
		}
		// a fresh reading from the game replaces an amount the player typed
		state.getManualBalances().remove(currency.getId());
		return true;
	}

	private long readComposite(Currency currency)
	{
		int[] varbits = currency.getVarIds();
		return PlayerState.compositeValue(currency, client.getVarbitValue(varbits[0]), client.getVarbitValue(varbits[1]));
	}

	private void syncCollectionLogPage()
	{
		String title = readPageTitle();
		if (title == null)
		{
			log.debug("Reward Shop Planner: collection log page drawn but no title found");
			return;
		}
		Activity activity = matcher.activityForPage(title);
		if (activity == null)
		{
			return; // a page without reward-shop slots
		}

		List<CollectionLogMatcher.Slot> slots = readPageSlots(InterfaceID.Collection.ITEMS_CONTENTS);
		if (slots.isEmpty())
		{
			slots = readPageSlots(InterfaceID.Collection.ITEMS);
		}
		if (slots.isEmpty())
		{
			log.debug("Reward Shop Planner: page {} has no readable item slots", title);
			return;
		}

		Map<String, Boolean> result = matcher.match(activity, slots);
		long obtained = result.values().stream().filter(b -> b).count();
		log.debug("Reward Shop Planner: synced page {}: {} slots read, {} matched of {}, {} obtained",
			title, slots.size(), result.size(), activity.getClogItems().size(), obtained);
		if (log.isDebugEnabled())
		{
			for (CollectionLogMatcher.Slot slot : slots)
			{
				log.debug("Reward Shop Planner:   slot {} '{}' obtained={}", slot.getItemId(), slot.getName(), slot.isObtained());
			}
		}
		synchronized (this)
		{
			for (Map.Entry<String, Boolean> slot : result.entrySet())
			{
				state.setLogOwned(slot.getKey(), slot.getValue());
			}
			state.getSyncedPages().add(activity.getClogPage());
		}
		saveState();
		refresh();
	}

	/** Name of the open collection log page, from the first line of the header. */
	private String readPageTitle()
	{
		for (int component : new int[]{InterfaceID.Collection.HEADER_TEXT, InterfaceID.Collection.HEADER})
		{
			Widget header = client.getWidget(component);
			if (header == null)
			{
				continue;
			}
			Widget[] lines = header.getDynamicChildren();
			if (lines == null || lines.length == 0)
			{
				lines = header.getChildren();
			}
			if (lines != null && lines.length > 0 && lines[0] != null)
			{
				String title = Text.removeTags(lines[0].getText()).trim();
				if (!title.isEmpty())
				{
					return title;
				}
			}
			String own = header.getText() == null ? "" : Text.removeTags(header.getText()).trim();
			if (!own.isEmpty())
			{
				return own;
			}
		}
		return null;
	}

	/** The game's name for an item id (client thread). */
	private String itemName(int itemId)
	{
		String name = itemManager.getItemComposition(itemId).getName();
		return name == null ? "" : Text.removeTags(name).trim();
	}

	/** Item slots drawn in a collection log container; faded (opacity > 0) slots are not obtained. */
	private List<CollectionLogMatcher.Slot> readPageSlots(int component)
	{
		List<CollectionLogMatcher.Slot> slots = new ArrayList<>();
		Widget container = client.getWidget(component);
		Widget[] items = container == null ? null : container.getDynamicChildren();
		if (items == null)
		{
			return slots;
		}
		for (Widget item : items)
		{
			if (item != null && item.getItemId() > 0)
			{
				String name = item.getName() == null ? "" : Text.removeTags(item.getName()).trim();
				if (name.isEmpty())
				{
					name = itemName(item.getItemId());
				}
				slots.add(new CollectionLogMatcher.Slot(item.getItemId(), name, item.getOpacity() == 0));
			}
		}
		return slots;
	}

	// ------------------------------------------------------------------
	// Panel actions (Swing thread)
	// ------------------------------------------------------------------

	/** Adds or removes slots from the goal. */
	public void setWanted(Collection<String> items, boolean wanted)
	{
		synchronized (this)
		{
			if (wanted)
			{
				state.getWanted().addAll(items);
			}
			else
			{
				state.getWanted().removeAll(items);
			}
		}
		savePlayerChange();
		refresh();
	}

	/** The player corrects whether a slot is owned. */
	public void setOwned(String item, boolean owned)
	{
		synchronized (this)
		{
			state.setOwnedByPlayer(item, owned);
		}
		savePlayerChange();
		refresh();
	}

	public void setPreferredActivity(String item, String activityId)
	{
		synchronized (this)
		{
			if (activityId == null)
			{
				state.getPreferredActivity().remove(item);
			}
			else
			{
				state.getPreferredActivity().put(item, activityId);
			}
		}
		savePlayerChange();
		refresh();
	}

	/** Marks items to sell back to their shop once logged, or to keep. */
	public void setSellBack(Collection<String> items, boolean sellBack)
	{
		synchronized (this)
		{
			if (sellBack)
			{
				state.getSellBack().addAll(items);
			}
			else
			{
				state.getSellBack().removeAll(items);
			}
		}
		savePlayerChange();
		refresh();
	}

	/** Whether the player wears Karamja gloves at TzHaar; null goes back to the diary. */
	public void setKaramjaGloves(Boolean wears)
	{
		synchronized (this)
		{
			state.setKaramjaGlovesChoice(wears);
		}
		savePlayerChange();
		refresh();
	}

	public void setExtraGoal(String currencyId, Long amount)
	{
		synchronized (this)
		{
			if (amount == null || amount <= 0)
			{
				state.getExtraGoals().remove(currencyId);
			}
			else
			{
				state.getExtraGoals().put(currencyId, amount);
			}
		}
		savePlayerChange();
		refresh();
	}

	public void setManualBalance(String currencyId, Long amount)
	{
		synchronized (this)
		{
			if (amount == null)
			{
				state.getManualBalances().remove(currencyId);
			}
			else
			{
				state.getManualBalances().put(currencyId, amount);
			}
		}
		savePlayerChange();
		refresh();
	}

	// ------------------------------------------------------------------
	// State and refresh
	// ------------------------------------------------------------------

	private synchronized void loadState()
	{
		String json = configManager.getRSProfileConfiguration(RewardShopPlannerConfig.GROUP, STATE_KEY);
		PlayerState loaded = null;
		if (json != null)
		{
			try
			{
				loaded = gson.fromJson(json, PlayerState.class);
			}
			catch (RuntimeException e)
			{
				log.warn("Could not read saved Reward Shop Planner state, starting fresh", e);
			}
		}
		state = loaded != null ? loaded : new PlayerState();
		log.debug("Reward Shop Planner: loaded state for profile {} ({} wanted, {} owned, {} corrections)",
			configManager.getRSProfileKey(), state.getWanted().size(), state.getOwned().size(), state.getOwnedOverrides().size());
	}

	private void saveState()
	{
		if (configManager.getRSProfileKey() == null)
		{
			log.debug("Reward Shop Planner: not saving, no RuneScape profile yet");
			return;
		}
		String json;
		int wanted;
		synchronized (this)
		{
			json = gson.toJson(state);
			wanted = state.getWanted().size();
		}
		configManager.setRSProfileConfiguration(RewardShopPlannerConfig.GROUP, STATE_KEY, json);
		log.debug("Reward Shop Planner: saved state ({} wanted) to profile {}", wanted, configManager.getRSProfileKey());
	}

	/**
	 * Saves a change the player made in the panel and writes the config to disk right away:
	 * RuneLite otherwise writes config in batches, and a client closed soon after would lose it.
	 */
	private void savePlayerChange()
	{
		saveState();
		executor.execute(configManager::sendConfig);
	}

	private void refresh()
	{
		PlannerPanel target = panel;
		if (target == null || calculator == null)
		{
			return;
		}

		PanelModel model;
		synchronized (this)
		{
			Map<String, Long> balances = new LinkedHashMap<>();
			for (Currency currency : data.getCurrencies().values())
			{
				Long balance = state.balanceOf(currency);
				if (balance != null)
				{
					balances.put(currency.getId(), balance);
				}
			}

			Set<String> owned = state.effectiveOwned();
			AccountMode mode = state.getAccountMode();
			boolean gloves = state.wearsKaramjaGloves();
			Set<String> sellBack = new HashSet<>(state.getSellBack());
			PlannerInput input = PlannerInput.builder()
				.owned(owned)
				.wanted(new HashSet<>(state.getWanted()))
				.balances(balances)
				.extraGoals(new LinkedHashMap<>(state.getExtraGoals()))
				.preferredActivity(new HashMap<>(state.getPreferredActivity()))
				.accountMode(mode)
				.sellBack(sellBack)
				.karamjaGloves(gloves)
				.build();
			Plan plan = calculator.plan(input);

			Map<String, Long> materials = new HashMap<>();
			for (String name : data.getMaterials().keySet())
			{
				Long have = state.materialOf(name);
				if (have != null)
				{
					materials.put(name, have);
				}
			}

			model = new PanelModel(data, plan, balances, materials,
				owned,
				new HashSet<>(state.getOwnedOverrides().keySet()),
				new LinkedHashSet<>(state.getSyncedPages()),
				new HashSet<>(state.getWanted()),
				new HashMap<>(state.getPreferredActivity()),
				new LinkedHashMap<>(state.getExtraGoals()),
				new HashMap<>(state.getManualBalances()),
				mode,
				sellBack,
				gloves,
				state.getKaramjaGlovesChoice() == null,
				state.isKaramjaGlovesClaimed(),
				config.hideCompleted());
		}
		ProgressTracker tracker = progress;
		if (tracker != null)
		{
			tracker.update(model);
		}
		SwingUtilities.invokeLater(() -> target.update(model));
	}
}
