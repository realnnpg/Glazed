# CrystalTweaks

Quality-of-life safety tweaks for Crystal PvP. Prevents the small inventory and input mistakes that cost fights.

Requires `CrystalTweaksMixin` to be registered in `glazed.mixins.json`:
```json
"mixins": [ "CrystalTweaksMixin", ... ]
```

---

## Features

### Totem Slot Protection
Prevents totems from being accidentally removed from the offhand or your configured backup hotbar slot.

**Always active (when enabled):**
- Pressing F while the offhand already holds a totem is blocked — the totem cannot be ejected
- Number-key swapping a totem out of the backup hotbar slot is blocked

**After a totem pop (for `pop-lock-ticks`):**
- Direct clicks on the offhand slot and the backup slot are also blocked, preventing mis-clicks during the restock panic

| Setting | Default | Description |
|---|---|---|
| `backup-hotbar-slot` | `1` | Hotbar slot (1–9) to protect. Set to `0` to only protect the offhand. |
| `pop-lock-ticks` | `10` | How long to lock after a pop (10 ticks ≈ 500 ms) |

---

### Anti Drop
Blocks the drop key (`Q`) whenever no inventory or container screen is open. As soon as any screen is opened, `Q` works normally again.

Prevents accidentally dropping crystals, totems, or obsidian mid-fight by hitting `Q` out of reflex.

---

### Anti Interrupt
Requires a double-tap of the chat key (`T`) within a configurable time window to actually open chat. A single accidental press is silently swallowed.

Single press in the middle of a fight will never open chat — only an intentional double-tap will.

| Setting | Default | Description |
|---|---|---|
| `double-tap-window-ms` | `300` | Time window (ms) in which the second press counts as a double-tap |

---

### Cursor Guard
Disables left-click and right-click item pickup (drag & drop) in any open inventory screen. Items can only be moved via hotkey binds (number keys, `F`). Shift-click still works normally.

Prevents the cursor from accidentally picking up an item when pressing a hotkey bind inside the inventory, which would mess up totem restocking.

---

### Hotbar Lock
Locks all hotbar slots against changes. You can whitelist specific slots that are allowed to update freely (e.g. your totem slot managed by AutoInvTotem).

| Setting | Default | Description |
|---|---|---|
| `whitelist-slots` | `"1"` | Comma-separated slot numbers (1–9) that are exempt from the lock. Leave empty to lock all slots. Example: `"1,2"` |

---

### Glowstone Block
While holding Glowstone, cancels any right-click on a block that is **not** a Respawn Anchor. Glowstone can only be right-clicked into an anchor.

Prevents accidentally placing Glowstone on the floor instead of into an anchor.

---

### Anchor Max Fill
While holding Glowstone, blocks charging a Respawn Anchor that already has 1 or more charges. The first charge (0 → 1) is always allowed through.

Since you explode the anchor immediately after the first charge, additional charges are pure Glowstone waste. This also prevents accidentally overcharging when spamming right-click.

---

## Implementation Notes

All slot-click interception (`CursorGuard`, `HotbarLock`, `TotemSlotProtection`) runs through the `CrystalTweaksMixin` injection into `ClientPlayerInteractionManager#clickSlot` at `HEAD`. This fires **before** Minecraft applies any client-side inventory change and **before** the `ClickSlotC2SPacket` is constructed — zero visual desync, zero packet traces.

Block interaction interception (`GlowstoneBlock`, `AnchorMaxFill`) runs through the `CrystalTweaksMixin` injection into `ClientPlayerInteractionManager#interactBlock` at `HEAD`. This fires before the interaction is processed client-side and before `PlayerInteractBlockC2SPacket` is sent.

`AntiDrop` and `AntiInterrupt` use Meteor's `KeyEvent`, which fires before Minecraft processes any key bind — no action is triggered, no packet is ever sent.
