package net.enchanting_commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.io.IOException;
import java.nio.file.Path;
import net.enchanting_commands.mixin.CommandNodeAccessor;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class CommandConfiguration {
    private final Path path;
    private String activeName;

    CommandConfiguration(Path path, String initialName) {
        this.path = path;
        this.activeName = initialName;
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        bind(dispatcher, context, activeName);
    }

    private LiteralCommandNode<CommandSourceStack> bind(CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context, String name) {
        return EnchantCommand.register(dispatcher, context, name,
                source -> reload(source, dispatcher, context, name));
    }

    private int reload(CommandSourceStack source, CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context, String currentName) {
        final String requestedName;
        try {
            requestedName = ModConfig.read(path);
        } catch (IOException | RuntimeException exception) {
            source.sendFailure(Component.literal("[Enchanting Commands] Config reload failed. Keeping /"
                    + currentName + ". " + exception.getMessage()));
            return 0;
        }
        if (!requestedName.equals(currentName)) {
            // Check before touching the working root: never merge into another mod's command.
            if (dispatcher.getRoot().getChild(requestedName) != null) {
                source.sendFailure(Component.literal("[Enchanting Commands] Config reload failed: /"
                        + requestedName + " is already registered. Keeping /" + currentName + "."));
                return 0;
            }
            var oldRoot = dispatcher.getRoot().getChild(currentName);
            var replacement = bind(dispatcher, context, requestedName);
            if (replacement == null) return 0;
            var indexes = (CommandNodeAccessor) dispatcher.getRoot();
            indexes.enchantingCommands$getChildren().remove(currentName, oldRoot);
            indexes.enchantingCommands$getLiterals().remove(currentName, oldRoot);
            activeName = requestedName;
            // Refresh online clients so the new root immediately appears in Tab completion.
            var server = source.getServer();
            for (var player : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(player);
            }
        }
        source.sendSuccess(() -> Component.literal("[Enchanting Commands] Config reloaded. Active command: /"
                + requestedName + "."), false);
        return 1;
    }
}
