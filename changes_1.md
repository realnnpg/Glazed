# 🛠️ HoleTunnelStairsESP & Glazed Addon - Changelog

## ✨ New Major Modules & Commands

- **Auto Shop Order (`AutoShopOrder.java`)**
  - **Consolidation:** Replaces legacy modules `AutoBlazeRodOrder`, `AutoShulkerOrder`, `AutoTotemOrder`, `AutoShulkerShellOrder`, and `AutoShellOrder`.
  - **Logic:** Features a robust state-machine for navigating `/shop`, bulk buying, and identifying the most profitable `/orders`.
  - **Features:** "AUTO" pricing (Shop + $1), custom price suffixes (K, M, B), player blacklist, and an anti-stuck system.
- **Pearl Landing Predictor (`PearlLandingPredictor.java`)**
  - **Function:** Simulates Ender Pearl trajectories to predict and highlight landing points.
  - **Features:** Player filtering, live updates for new chunks, and distinct rendering for unknown owners.
  - **Visuals:** Now correctly renders both the landing destination box and the player’s name.
- **AH Sell Command (`.sell_hotbar`)**
  - **Function:** Command-based version of `AHSell.java`. Automatically lists all hotbar items via `/ah sell <price>`.
- **AH Item Command (`.ahitem`)**
  - **Function:** Instantly searches the Auction House for the item in hand.
  - **Smart Search:** Automatically appends enchantments (e.g., `sharpness 5`) and "stack" parameters to the search query.

---

## 🔄 Module Improvements & Refactors

### 🟦 HoleTunnelStairsESP (Major Overhaul)

The `CoveredHole.java` module has been fully integrated into `HoleTunnelStairsESP` to centralize logic and reduce overhead.

- **Variable Tunnel Width:** New `minTunnelWidth`/`maxTunnelWidth` settings (default 1-3) for detecting 1x1 to 3x3 tunnels.
- **Covered Hole Detection:**
  - Identifies 1x1 and 1x3 holes covered by solid blocks.
  - **Smart Filtering:** `only-player-covered` logic distinguishes player-made covers from natural terrain generation.
  - **Dedicated Rendering:** Custom colors for covered holes to avoid visual confusion.
- **Dynamic Updates:** Added `undergroundUpdateThreshold` and packet listeners for Y < 0 updates on servers with dynamic chunk loading.
- **Optimization:** Hash-based deduplication (`tunnelHashes`) for O(1) lookups, preventing redundant rendering across chunk borders.

---

## 🐛 Bug Fixes

- **HoleTunnelStairsESP:**
  - **Double Rendering:** Fixed via canonical start positions and hash-based deduplication.
  - **Inconsistent Cross-sections:** Fixed ceiling height detection using the `refHeight` parameter.
  - **1x3 Covered Holes:** Corrected detection logic to verify all three top blocks instead of just the start block.
- **Pearl Landing Predictor:** Fixed a bug where the landing box was not rendering (previously only the name label was visible).

---

## 🚀 Performance Improvements

- **Scan Efficiency:** `HoleTunnelStairsESP` now uses a **2-pass instead of 3-pass** scan, combining length measurement and end-position recording.
- **Redundancy Check:** Implementation of `CANONICAL_TUNNEL_DIRS` (East/South only) to prevent scanning the same tunnel from opposite directions.
- **Thread Safety:** Integrated `ThreadLocal BitSet` for chunk processing, removing synchronization bottlenecks.
- **Caching:** Added `solidBlockCache` and `blockStateCache` to minimize expensive world-access calls.
