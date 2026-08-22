package dev.invhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class InventoryHudClient implements ClientModInitializer {

    public static final String MOD_ID = "invhud";

    private static HudConfig config;

    private static KeyBinding editKey;
    private static KeyBinding toggleKey;

    public static HudConfig config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        config = HudConfig.load();

        // 1.21.6+ keybind categories are objects, not plain strings.
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

        editKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.invhud.edit", GLFW.GLFW_KEY_RIGHT_BRACKET, category));
        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.invhud.toggle", GLFW.GLFW_KEY_LEFT_BRACKET, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                config.enabled = !config.enabled;
                config.save();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Inventory HUD ")
                            .append(Text.literal(config.enabled ? "enabled" : "disabled")
                                    .formatted(config.enabled ? Formatting.GREEN : Formatting.RED)), true);
                }
            }
            while (editKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new HudEditScreen());
                }
            }
        });

        // HudRenderCallback is deprecated as of the 1.21.6 HUD rework.
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "inventory_hud"), (context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();

            if (!config.enabled) return;
            if (client.player == null || client.world == null) return;
            if (config.respectF1 && client.options.hudHidden) return;
            if (client.currentScreen instanceof HudEditScreen) return;
            if (config.hideWhenScreenOpen && client.currentScreen != null) return;

            HudRenderer.render(context, client, config, false);
        });
    }
}
