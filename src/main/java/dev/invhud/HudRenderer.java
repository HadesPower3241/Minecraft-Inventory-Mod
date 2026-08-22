package dev.invhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Draws the panel. No cached state: every frame the slots are read straight
 * out of the live client-side inventory, so the HUD is never stale.
 *
 * <p>1.21.11 notes: {@code DrawContext#getMatrices()} is a JOML
 * {@link org.joml.Matrix3x2fStack} (2D, {@code pushMatrix}/{@code popMatrix}),
 * {@code drawItemInSlot} is now {@code drawStackOverlay}, {@code drawBorder}
 * is gone (we build one out of fills), and armor lives on the entity's
 * equipment rather than in {@code PlayerInventory}.
 */
public final class HudRenderer {

    public static final int CELL = 18;
    public static final int PAD = 3;
    public static final int SECTION_GAP = 3;
    public static final int COLUMNS = 9;

    private static final EquipmentSlot[] ARMOR_ORDER = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private HudRenderer() {
    }

    // ------------------------------------------------------------ geometry

    public static int panelWidth(HudConfig cfg) {
        return PAD * 2 + COLUMNS * CELL;
    }

    public static int panelHeight(HudConfig cfg) {
        int h = PAD * 2;
        boolean previous = false;
        if (cfg.showArmor) {
            h += CELL;
            previous = true;
        }
        if (cfg.showMain) {
            if (previous) h += SECTION_GAP;
            h += CELL * 3;
            previous = true;
        }
        if (cfg.showHotbar) {
            if (previous) h += SECTION_GAP;
            h += CELL;
        }
        return h;
    }

    public static float scaledWidth(HudConfig cfg) {
        return panelWidth(cfg) * cfg.scale;
    }

    public static float scaledHeight(HudConfig cfg) {
        return panelHeight(cfg) * cfg.scale;
    }

    public static float originX(HudConfig cfg, int screenWidth) {
        float free = Math.max(0f, screenWidth - scaledWidth(cfg));
        return Math.round(cfg.posX * free);
    }

    public static float originY(HudConfig cfg, int screenHeight) {
        float free = Math.max(0f, screenHeight - scaledHeight(cfg));
        return Math.round(cfg.posY * free);
    }

    // ------------------------------------------------------------- drawing

    public static void render(DrawContext context, MinecraftClient client, HudConfig cfg, boolean editing) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Slots 0..35: 0-8 hotbar, 9-35 the main grid.
        var stacks = player.getInventory().getMainStacks();
        int selected = player.getInventory().getSelectedSlot();

        int width = panelWidth(cfg);
        int height = panelHeight(cfg);
        float ox = originX(cfg, client.getWindow().getScaledWidth());
        float oy = originY(cfg, client.getWindow().getScaledHeight());

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(ox, oy);
        matrices.scale(cfg.scale, cfg.scale);

        if (cfg.backgroundAlpha > 0) {
            context.fill(0, 0, width, height, cfg.backgroundArgb());
        }
        if (cfg.drawBorder || editing) {
            outline(context, 0, 0, width, height, editing ? 0xFF55FF55 : 0x60FFFFFF);
        }

        int y = PAD;

        // --- armor + offhand ---------------------------------------------
        if (cfg.showArmor) {
            for (int i = 0; i < ARMOR_ORDER.length; i++) {
                drawSlot(context, client, cfg, player.getEquippedStack(ARMOR_ORDER[i]), PAD + i * CELL, y, false);
            }
            drawSlot(context, client, cfg, player.getEquippedStack(EquipmentSlot.OFFHAND),
                    PAD + (COLUMNS - 1) * CELL, y, false);
            y += CELL + SECTION_GAP;
        }

        // --- main 3x9 grid -------------------------------------------------
        if (cfg.showMain) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < COLUMNS; col++) {
                    int slot = 9 + row * COLUMNS + col;
                    drawSlot(context, client, cfg, stacks.get(slot), PAD + col * CELL, y + row * CELL, false);
                }
            }
            y += CELL * 3 + SECTION_GAP;
        }

        // --- hotbar --------------------------------------------------------
        if (cfg.showHotbar) {
            for (int col = 0; col < COLUMNS; col++) {
                boolean isSelected = cfg.highlightSelected && selected == col;
                drawSlot(context, client, cfg, stacks.get(col), PAD + col * CELL, y, isSelected);
            }
        }

        matrices.popMatrix();
    }

    private static void drawSlot(DrawContext context, MinecraftClient client, HudConfig cfg,
                                 ItemStack stack, int cellX, int cellY, boolean selected) {
        int x = cellX + 1;
        int y = cellY + 1;

        if (cfg.slotBackground) {
            context.fill(x - 1, y - 1, x + 17, y + 17, 0x22FFFFFF);
        }
        if (selected) {
            outline(context, x - 1, y - 1, 18, 18, 0xFFFFFFFF);
        }
        if (stack == null || stack.isEmpty()) {
            return;
        }

        context.drawItem(stack, x, y);
        if (cfg.showCounts) {
            // Stack size, durability bar and cooldown overlay.
            context.drawStackOverlay(client.textRenderer, stack, x, y);
        }
    }

    /** DrawContext#drawBorder was removed in 1.21.9, so build one from fills. */
    private static void outline(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
