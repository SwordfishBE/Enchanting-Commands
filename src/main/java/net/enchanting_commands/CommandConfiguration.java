package net.enchanting_commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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
        var binding = new Binding(name);
        var node = EnchantCommand.register(dispatcher, context, name,
                source -> reload(source, dispatcher, context, binding));
        if (node != null) binding.original = OwnedTree.capture(node);
        return node;
    }

    private int reload(CommandSourceStack source, CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context, Binding binding) {
        String currentName = binding.name;
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
            // Other registration callbacks can merge commands into our root after bind().
            // Compare identities and executors recursively before removing any part of it.
            if (binding.original == null || !binding.original.matches(oldRoot)) {
                source.sendFailure(Component.literal("[Enchanting Commands] Config reload failed: /"
                        + currentName + " was extended or replaced by another registration. Keeping /"
                        + currentName + ". Restart the server to apply the new name safely."));
                return 0;
            }
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

    private static final class Binding {
        private final String name;
        private OwnedTree original;

        private Binding(String name) {
            this.name = name;
        }
    }

    /** Immutable ownership snapshot; Brigadier nodes themselves remain mutable. */
    private record OwnedTree(CommandNode<?> node, Object executor, Map<String, OwnedTree> children) {
        static OwnedTree capture(CommandNode<?> node) {
            var children = new LinkedHashMap<String, OwnedTree>();
            for (var child : node.getChildren()) children.put(child.getName(), capture(child));
            return new OwnedTree(node, node.getCommand(), Map.copyOf(children));
        }

        boolean matches(CommandNode<?> current) {
            if (current != node || current.getCommand() != executor
                    || current.getChildren().size() != children.size()) return false;
            for (var child : children.entrySet()) {
                if (!child.getValue().matches(current.getChild(child.getKey()))) return false;
            }
            return true;
        }
    }
}
