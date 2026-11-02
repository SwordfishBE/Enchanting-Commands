package net.enchanting_commands;

import java.util.Optional;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class EnchantingCommandsConfigScreen {
    private EnchantingCommandsConfigScreen() {}

    static Screen create(Screen parent) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null && !minecraft.hasSingleplayerServer()) {
            return notice(parent, Component.literal("Multiplayer uses the server configuration. Edit the server file and run the server's reload command."));
        }
        final String initial;
        try {
            initial = EnchantingCommands.commandForEditing();
        } catch (java.io.IOException | RuntimeException exception) {
            return notice(parent, Component.literal("[Enchanting Commands] Cannot open config. Existing file preserved. " + exception.getMessage()));
        }
        String[] draft = {initial};
        var builder = ConfigBuilder.create().setParentScreen(parent)
                .setTitle(Component.literal("Enchanting Commands Config"));
        builder.getOrCreateCategory(Component.literal("General")).addEntry(builder.entryBuilder()
                .startStrField(Component.literal("Command name"), initial)
                .setDefaultValue("ec")
                .setTooltip(Component.literal("Without /. Lowercase letters, digits, underscores and hyphens."),
                        Component.literal("Applies immediately in singleplayer unless another command conflicts."))
                .setErrorSupplier(value -> EnchantingCommands.validCommandName(value) ? Optional.empty()
                        : Optional.of(Component.literal("Use lowercase letters, digits, underscores or hyphens without spaces or /.")))
                .setSaveConsumer(value -> draft[0] = value).build());
        builder.setSavingRunnable(() -> {
            var server = minecraft.getSingleplayerServer();
            if (minecraft.getConnection() != null && server == null) return;
            Runnable save = () -> {
                var message = EnchantingCommands.saveEditedCommand(draft[0], server);
                // Enqueue after Cloth Config has closed its screen.
                minecraft.execute(() -> minecraft.setScreenAndShow(notice(parent, message)));
            };
            if (server != null) server.execute(save);
            else {
                var message = EnchantingCommands.saveEditedCommand(draft[0], null);
                minecraft.schedule(() -> minecraft.setScreenAndShow(notice(parent, message)));
            }
        });
        return builder.build();
    }

    private static Screen notice(Screen parent, Component message) {
        return new AlertScreen(() -> Minecraft.getInstance().setScreenAndShow(parent),
                Component.literal("Enchanting Commands"), message);
    }
}
