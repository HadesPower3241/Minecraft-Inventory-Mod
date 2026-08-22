package dev.invhud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * The "move it where you want it" screen.
 *
 * <p>1.21.9 replaced the old {@code mouseClicked(double, double, int)} style
 * callbacks with {@code Click} / {@code KeyInput} records. Rather than depend
 * on those still-churning types, dragging and nudging are driven by polling
 * GLFW directly each frame — the mouse position comes from the render
 * parameters, which are already in scaled GUI coordinates. Only
 * {@code mouseScrolled} is overridden, whose signature is stable.
 */
public class HudEditScreen extends Screen {

    private static final int SNAP_DISTANCE = 6;
    private static final int[] NUDGE_KEYS = {
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN
    };

    private final HudConfig cfg = InventoryHudClient.config();

    private boolean dragging;
    private boolean mouseWasDown;
    private float grabOffsetX;
    private float grabOffsetY;
    private final boolean[] nudgeWasDown = new boolean[NUDGE_KEYS.length];

    private ScaleSlider scaleSlider;

    public HudEditScreen() {
        super(Text.literal("Inventory HUD Editor"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        int sliderY = this.height - 128;
        scaleSlider = new ScaleSlider(cx - 155, sliderY, 150, 20);
        addDrawableChild(scaleSlider);
        addDrawableChild(new AlphaSlider(cx + 5, sliderY, 150, 20));

        int bw = 100;
        int gap = 4;
        int left = cx - (bw * 3 + gap * 2) / 2;

        int row1 = this.height - 104;
        int row2 = this.height - 82;
        int row3 = this.height - 60;

        addToggle(left, row1, bw, "Main Grid", cfg.showMain, v -> cfg.showMain = v);
        addToggle(left + bw + gap, row1, bw, "Hotbar", cfg.showHotbar, v -> cfg.showHotbar = v);
        addToggle(left + (bw + gap) * 2, row1, bw, "Armor", cfg.showArmor, v -> cfg.showArmor = v);

        addToggle(left, row2, bw, "Counts", cfg.showCounts, v -> cfg.showCounts = v);
        addToggle(left + bw + gap, row2, bw, "Slot BG", cfg.slotBackground, v -> cfg.slotBackground = v);
        addToggle(left + (bw + gap) * 2, row2, bw, "Border", cfg.drawBorder, v -> cfg.drawBorder = v);

        addToggle(left, row3, bw, "Highlight", cfg.highlightSelected, v -> cfg.highlightSelected = v);
        addToggle(left + bw + gap, row3, bw, "Hide w/ GUIs", cfg.hideWhenScreenOpen, v -> cfg.hideWhenScreenOpen = v);
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> {
            cfg.resetToDefaults();
            this.clearChildren();
            this.init();
        }).dimensions(left + (bw + gap) * 2, row3, bw, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
                .dimensions(cx - 50, this.height - 32, 100, 20).build());
    }

    private void addToggle(int x, int y, int width, String label, boolean initial, BoolSetter setter) {
        final boolean[] state = {initial};
        addDrawableChild(ButtonWidget.builder(labelFor(label, initial), b -> {
            state[0] = !state[0];
            setter.set(state[0]);
            clampPosition();
            b.setMessage(labelFor(label, state[0]));
        }).dimensions(x, y, width, 20).build());
    }

    private static Text labelFor(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    // ------------------------------------------------------------- render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        pollDrag(mouseX, mouseY);
        pollNudge();

        if (this.client != null && this.client.player != null) {
            HudRenderer.render(context, this.client, cfg, true);
        }

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Inventory HUD Editor"), this.width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Drag the panel \u2022 Scroll to resize \u2022 Arrow keys nudge \u2022 Hold Shift to ignore snapping"),
                this.width / 2, 26, 0xA0A0A0);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("x " + (int) HudRenderer.originX(cfg, this.width)
                        + "  y " + (int) HudRenderer.originY(cfg, this.height)
                        + "  scale " + String.format("%.2f", cfg.scale)),
                this.width / 2, 38, 0x808080);
    }

    // -------------------------------------------------------------- input

    private long handle() {
        return this.client.getWindow().getHandle();
    }

    private boolean keyDown(int key) {
        return GLFW.glfwGetKey(handle(), key) == GLFW.GLFW_PRESS;
    }

    private boolean shiftDown() {
        return keyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || keyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private boolean ctrlDown() {
        return keyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || keyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private void pollDrag(int mouseX, int mouseY) {
        if (this.client == null) return;
        boolean down = GLFW.glfwGetMouseButton(handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (down && !mouseWasDown && isOverPanel(mouseX, mouseY) && !isOverWidget(mouseX, mouseY)) {
            dragging = true;
            grabOffsetX = mouseX - HudRenderer.originX(cfg, this.width);
            grabOffsetY = mouseY - HudRenderer.originY(cfg, this.height);
        }
        if (dragging && down) {
            setOrigin(mouseX - grabOffsetX, mouseY - grabOffsetY, !shiftDown());
        }
        if (!down && dragging) {
            dragging = false;
            cfg.save();
        }
        mouseWasDown = down;
    }

    private void pollNudge() {
        int step = ctrlDown() ? 10 : 1;
        for (int i = 0; i < NUDGE_KEYS.length; i++) {
            boolean down = keyDown(NUDGE_KEYS[i]);
            if (down && !nudgeWasDown[i]) {
                float ox = HudRenderer.originX(cfg, this.width);
                float oy = HudRenderer.originY(cfg, this.height);
                switch (NUDGE_KEYS[i]) {
                    case GLFW.GLFW_KEY_LEFT -> setOrigin(ox - step, oy, false);
                    case GLFW.GLFW_KEY_RIGHT -> setOrigin(ox + step, oy, false);
                    case GLFW.GLFW_KEY_UP -> setOrigin(ox, oy - step, false);
                    case GLFW.GLFW_KEY_DOWN -> setOrigin(ox, oy + step, false);
                    default -> {
                    }
                }
                cfg.save();
            }
            nudgeWasDown[i] = down;
        }
    }

    private boolean isOverPanel(double mouseX, double mouseY) {
        float ox = HudRenderer.originX(cfg, this.width);
        float oy = HudRenderer.originY(cfg, this.height);
        return mouseX >= ox && mouseX <= ox + HudRenderer.scaledWidth(cfg)
                && mouseY >= oy && mouseY <= oy + HudRenderer.scaledHeight(cfg);
    }

    private boolean isOverWidget(double mouseX, double mouseY) {
        for (Element element : this.children()) {
            if (element.isMouseOver(mouseX, mouseY)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        cfg.scale = MathHelper.clamp(cfg.scale + (float) verticalAmount * 0.05f, 0.4f, 2.5f);
        if (scaleSlider != null) scaleSlider.syncFromConfig();
        clampPosition();
        return true;
    }

    @Override
    public void close() {
        cfg.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------ helpers

    private void setOrigin(float px, float py, boolean snap) {
        float w = HudRenderer.scaledWidth(cfg);
        float h = HudRenderer.scaledHeight(cfg);
        float freeX = Math.max(0f, this.width - w);
        float freeY = Math.max(0f, this.height - h);

        if (snap) {
            px = snapTo(px, 0f, (this.width - w) / 2f, this.width - w);
            py = snapTo(py, 0f, (this.height - h) / 2f, this.height - h);
        }

        cfg.posX = freeX <= 0f ? 0f : MathHelper.clamp(px / freeX, 0f, 1f);
        cfg.posY = freeY <= 0f ? 0f : MathHelper.clamp(py / freeY, 0f, 1f);
    }

    private static float snapTo(float value, float... targets) {
        for (float target : targets) {
            if (Math.abs(value - target) <= SNAP_DISTANCE) return target;
        }
        return value;
    }

    private void clampPosition() {
        setOrigin(HudRenderer.originX(cfg, this.width), HudRenderer.originY(cfg, this.height), false);
    }

    @FunctionalInterface
    private interface BoolSetter {
        void set(boolean value);
    }

    // ------------------------------------------------------------ sliders

    private class ScaleSlider extends SliderWidget {
        ScaleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.empty(), (cfg.scale - 0.4f) / 2.1f);
            updateMessage();
        }

        void syncFromConfig() {
            this.value = (cfg.scale - 0.4f) / 2.1f;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Scale: " + String.format("%.2f", cfg.scale)));
        }

        @Override
        protected void applyValue() {
            cfg.scale = MathHelper.clamp(0.4f + (float) this.value * 2.1f, 0.4f, 2.5f);
            clampPosition();
        }
    }

    private class AlphaSlider extends SliderWidget {
        AlphaSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.empty(), cfg.backgroundAlpha / 255.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Background: " + Math.round(cfg.backgroundAlpha / 255.0 * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            cfg.backgroundAlpha = MathHelper.clamp((int) Math.round(this.value * 255), 0, 255);
        }
    }
}
