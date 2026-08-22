package dev.invhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All user-tweakable state. Serialized to
 * .minecraft/config/inventory-hud.json (or the equivalent Lunar Client
 * game directory) as plain JSON.
 *
 * Position is stored as a 0..1 fraction of the free space on screen rather
 * than raw pixels, so the panel keeps its relative placement when the window
 * is resized or the GUI scale changes.
 */
public class HudConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "inventory-hud.json";

    /** Master on/off switch. */
    public boolean enabled = true;

    /** Horizontal anchor, 0 = flush left, 1 = flush right. */
    public float posX = 0.02f;

    /** Vertical anchor, 0 = flush top, 1 = flush bottom. */
    public float posY = 0.35f;

    /** Panel scale multiplier. */
    public float scale = 1.0f;

    /** Background alpha, 0 (invisible) .. 255 (opaque). */
    public int backgroundAlpha = 130;

    /** Background RGB, without alpha. */
    public int backgroundColor = 0x000000;

    /** Draw a 1px outline around the panel. */
    public boolean drawBorder = true;

    /** Draw a faint square behind every slot. */
    public boolean slotBackground = true;

    /** Draw the 3x9 main inventory (the part you normally can't see). */
    public boolean showMain = true;

    /** Also mirror the hotbar row. */
    public boolean showHotbar = false;

    /** Also show the four armor slots and the offhand. */
    public boolean showArmor = true;

    /** Draw stack counts and durability bars. */
    public boolean showCounts = true;

    /** Highlight the slot matching the currently selected hotbar item. */
    public boolean highlightSelected = true;

    /** Hide the HUD whenever any screen (chest, inventory, chat, ...) is open. */
    public boolean hideWhenScreenOpen = true;

    /** Hide the HUD while F1 (hide GUI) is active. Almost always desirable. */
    public boolean respectF1 = true;

    // ---------------------------------------------------------------- io

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static HudConfig load() {
        Path file = path();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                HudConfig loaded = GSON.fromJson(reader, HudConfig.class);
                if (loaded != null) {
                    loaded.clampValues();
                    return loaded;
                }
            } catch (Exception e) {
                System.err.println("[Inventory HUD] Could not read config, using defaults: " + e);
            }
        }
        HudConfig fresh = new HudConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        clampValues();
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            System.err.println("[Inventory HUD] Could not save config: " + e);
        }
    }

    public void resetToDefaults() {
        HudConfig d = new HudConfig();
        this.enabled = d.enabled;
        this.posX = d.posX;
        this.posY = d.posY;
        this.scale = d.scale;
        this.backgroundAlpha = d.backgroundAlpha;
        this.backgroundColor = d.backgroundColor;
        this.drawBorder = d.drawBorder;
        this.slotBackground = d.slotBackground;
        this.showMain = d.showMain;
        this.showHotbar = d.showHotbar;
        this.showArmor = d.showArmor;
        this.showCounts = d.showCounts;
        this.highlightSelected = d.highlightSelected;
        this.hideWhenScreenOpen = d.hideWhenScreenOpen;
        this.respectF1 = d.respectF1;
    }

    private void clampValues() {
        if (Float.isNaN(posX)) posX = 0f;
        if (Float.isNaN(posY)) posY = 0f;
        posX = Math.max(0f, Math.min(1f, posX));
        posY = Math.max(0f, Math.min(1f, posY));
        scale = Math.max(0.4f, Math.min(2.5f, scale));
        backgroundAlpha = Math.max(0, Math.min(255, backgroundAlpha));
        backgroundColor &= 0xFFFFFF;
        // The panel would be empty otherwise; force at least one section on.
        if (!showMain && !showHotbar && !showArmor) showMain = true;
    }

    /** Packed ARGB used for the panel background. */
    public int backgroundArgb() {
        return (backgroundAlpha << 24) | (backgroundColor & 0xFFFFFF);
    }
}
