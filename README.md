# Inventory HUD — Minecraft 1.21.11 (Fabric)

A client-side mod that draws your entire inventory as a movable on-screen panel
that updates in real time.

- Full 3×9 main inventory, plus optional hotbar row, armor slots and offhand
- Drag-to-move editor with edge/center snapping, scaling, background opacity
- Stack counts, durability bars, selected-slot highlight
- Settings saved to `config/inventory-hud.json`
- Client-only. Nothing is sent anywhere; the panel reads the client's own copy of your inventory

## Controls

| Key | Action |
| --- | --- |
| `]` | Open the HUD editor |
| `[` | Toggle the HUD on/off |

Rebindable under **Options → Controls → Inventory HUD**.
In the editor: drag the panel, scroll to resize, arrow keys to nudge (Ctrl+arrow = 10px),
hold Shift while dragging to ignore snapping.

## Getting a jar

The jar is not included here — it has to be compiled against Minecraft 1.21.11,
which Gradle downloads at build time. Pick whichever route suits you.

### A. GitHub Actions (no tools to install)

A workflow is already included at `.github/workflows/build.yml`.

1. Create a new repository on GitHub and upload this folder to it.
2. Open the **Actions** tab — the `build` workflow runs on push.
3. When it finishes (~2 min), open the run and download the `inventory-hud-jar` artifact.
4. Unzip it; inside is `inventory-hud-1.21.11-1.0.0.jar`.

### B. Locally

Needs JDK 21 (not 17 — 1.21.11 requires 21).

```bash
gradle wrapper      # once, generates gradlew
./gradlew build
```

Output: `build/libs/inventory-hud-1.21.11-1.0.0.jar`.

### C. IntelliJ IDEA

Open the folder, let it import the Gradle project, run the `build` task from the
Gradle tool window. Same output path.

## Installing into Lunar Client

Lunar supports third-party Fabric mods on 1.16.5+, and 1.21.11 is a supported version.

1. Open the Lunar Client Launcher, open the version selector, pick **1.21.11**.
2. Enable the **Fabric** add-on.
3. Press the ⚙ button (bottom right) → **Mods** tab.
4. Drag the jar into the window and launch.

Fabric API ships with Lunar's Fabric add-on. On plain Fabric, put this jar *and*
Fabric API in `.minecraft/mods`.

## Versions pinned in `gradle.properties`

```
minecraft_version=1.21.11
yarn_mappings=1.21.11+build.4
loader_version=0.17.3
fabric_version=0.141.6+1.21.11
```

If Loom complains that a mappings or API build doesn't exist, bump the build
number — those get republished. Loom itself is pinned to `1.14-SNAPSHOT`, which
is the line that supports 1.21.11.

## What changed from the 1.20.1 version

Mojang rewrote much of the GUI stack between 1.20.1 and 1.21.11, so this is a real
port rather than a version bump:

- `HudRenderCallback` is deprecated; the HUD is now registered through
  `HudElementRegistry.addLast(...)`.
- `DrawContext.getMatrices()` returns a JOML `Matrix3x2fStack` — 2D, with
  `pushMatrix()` / `popMatrix()` instead of `push()` / `pop()`.
- `drawItemInSlot` → `drawStackOverlay`; `drawBorder` is gone (rebuilt from `fill`).
- `PlayerInventory.armor` / `.offHand` are gone. Armor comes from
  `player.getEquippedStack(EquipmentSlot.HEAD)`, the 36 main slots from
  `getMainStacks()`, and the held index from `getSelectedSlot()`.
- Keybind categories are objects now: `KeyBinding.Category.create(Identifier)`.
- Screen input callbacks take `Click` / `KeyInput` records. Those types are still
  moving around, so the editor polls GLFW directly for dragging and arrow-key
  nudging instead, and only overrides `mouseScrolled`, whose signature is stable.

## Fair warning

Every API name above was checked against the published Yarn 1.21.11 mappings and
the Fabric API 1.21.11 branch, but the code was never compiled — I had no access
to the Minecraft/Fabric Maven repos where I wrote it. Treat the first build as the
real test. If something fails to resolve, it will be a mapping name and javac will
point straight at the line; send me the error and I'll fix it.
