package net.enchanting_commands;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;

public final class EnchantingCommandsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> new AlertScreen(() -> Minecraft.getInstance().setScreenAndShow(parent),
                    Component.literal("Enchanting Commands"),
                    Component.literal("Install Cloth Config to edit the configuration here. You can also edit config/enchanting_commands.json."));
        }
        if (!hasCompatibleClothConfig()) {
            return parent -> new AlertScreen(() -> Minecraft.getInstance().setScreenAndShow(parent),
                    Component.literal("Enchanting Commands"),
                    Component.literal("The configuration menu is prepared for Minecraft 26.4, but requires Cloth Config for 26.4. The installed older version cannot render this screen safely. Until then, edit config/enchanting_commands.json and use the reload command."));
        }
        return EnchantingCommandsConfigScreen::create;
    }

    static boolean hasCompatibleClothConfig() {
        var cloth = FabricLoader.getInstance().getModContainer("cloth-config");
        if (cloth.isEmpty()) return false;
        try {
            return net.fabricmc.loader.api.metadata.version.VersionPredicate.parse(">=26.4-")
                    .test(cloth.get().getMetadata().getVersion());
        } catch (net.fabricmc.loader.api.VersionParsingException exception) {
            return false;
        }
    }
}
