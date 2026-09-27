# Reward Shop Planner

![Reward Shop Planner](docs/marketing/hero.png)

Plan the reward-shop slots of your collection log. Pick the items you want, and the planner tells
you how many points, tokens or items you still need to earn, using the balances it reads straight
from the game.

![Demo](docs/marketing/demo.gif)

## Features

- **28 minigames and activities**: Castle Wars, Pest Control, Mage Training Arena, Barbarian Assault,
  Forestry, Mastering Mixology, Guardians of the Rift, Tithe Farm, Trouble Brewing and more.
- **Your log, synced**: open a page in the collection log and the planner knows what you already have.
- **Balances read from the game**: bank and inventory items, the Forestry kit, reward shop screens and
  game messages. No typing numbers in.
- **One goal for everything**: every item you pick adds up into one bar per currency, with extra goals
  for things outside the log (sawmill vouchers, anyone?).
- **Knows the rules**: upgrades that trade items in (Lumberjack into Forestry, Void into Elite Void),
  recoloured sets bought once, items you can sell back after logging them, and your account type.
- **You choose the shop**: items sold in two places, like the prospector kit, wait for you to pick.

| Your goals | Pick items like in the log | Choose where to buy |
|---|---|---|
| ![Goals](docs/marketing/feature-goals.png) | ![Item grid](docs/marketing/feature-grid.png) | ![Shops](docs/marketing/feature-shops.png) |

| Sell-backs counted for you | Sets bought as one |
|---|---|
| ![Sell-backs](docs/marketing/feature-sellbacks.png) | ![Sets](docs/marketing/feature-sets.png) |

Items you can sell back to their shop once the slot is logged (the funky shaped log, cape pouch, log basket,
Mixology gear...) are counted at what you really spend, depending on your account type. Recoloured outfits
like the Varlamore graceful are one purchase for the whole set, and the planner adds the base set they trade in.

## How to use

1. Open the **Reward Shop Planner** panel from the sidebar.
2. Open a collection log page in game so the planner can see what you own.
3. Click a page in the panel, then click the items you want. A gold frame means "in my goal".
4. Watch **My goals** on the home screen. Hover a bar for details, right-click an item for more options.

Balances update as you play: open your bank once, and visit a minigame's reward shop to refresh its points.

## Settings

| Setting | What it does |
|---|---|
| Account type | Auto-detected. Decides which items can be sold back to their shop. |
| Count sell-backs | Show what you need after selling items back once their slot is logged. |
| Hide completed pages | Hide pages where you own everything that can be bought. |

## Development

```
./gradlew test                 # unit tests
./gradlew run                  # RuneLite in developer mode with the plugin loaded
powershell -ExecutionPolicy Bypass -File scripts/generate-data.ps1   # refresh data from the OSRS Wiki
```

Requires JDK 11. Item and price data come from the [OSRS Wiki](https://oldschool.runescape.wiki) and are
bundled with the plugin; see `scripts/sources.json` for the hand-curated parts.

Screenshots in `docs/marketing` are rendered from the real panel with sample data
(`MarketingRenderer`, run with `RENDER_MARKETING=1` after `scripts/fetch-preview-icons.ps1`).

## License

BSD 2-Clause, see `LICENSE`.
