package net.enchanting_commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.enchantment.ItemEnchantments;

final class EnchantCommand {
    private EnchantCommand() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, String name) {
        if (dispatcher.getRoot().getChild(name) != null) {
            EnchantingCommands.LOGGER.warn("[Enchanting Commands] Command /{} is already registered; choose another enchantCommandString and restart.", name);
            return;
        }
        dispatcher.register(Commands.literal(name)
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(command -> {
                    reply(command.getSource(), "Usage: /" + name + " list | /" + name + " enchant <enchantment> <0-255>");
                    return 1;
                })
                .then(Commands.literal("list").executes(command -> {
                    var registry = command.getSource().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    String enchantments = registry.keySet().stream().map(id ->
                            id.getNamespace().equals("minecraft") ? id.getPath() : id.toString())
                            .sorted().collect(java.util.stream.Collectors.joining(", "));
                    reply(command.getSource(), "Available enchantments: " + enchantments);
                    return 1;
                }))
                .then(Commands.literal("enchant")
                        .then(Commands.argument("enchantment", ResourceArgument.resource(context, Registries.ENCHANTMENT))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 255))
                                        .executes(command -> {
                                            var source = command.getSource();
                                            var player = source.getPlayerOrException();
                                            var stack = player.getMainHandItem();
                                            if (stack.isEmpty()) {
                                                source.sendFailure(Component.literal("Hold an item in your main hand."));
                                                return 0;
                                            }
                                            var enchantment = ResourceArgument.getEnchantment(command, "enchantment");
                                            int level = IntegerArgumentType.getInteger(command, "level");
                                            var current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                                            boolean present = current.getLevel(enchantment) > 0;
                                            if (level == 0 && !present) {
                                                source.sendFailure(Component.literal("This item does not have that enchantment."));
                                                return 0;
                                            }
                                            var updated = new ItemEnchantments.Mutable(current);
                                            updated.set(enchantment, level);
                                            stack.set(DataComponents.ENCHANTMENTS, updated.toImmutable());
                                            player.containerMenu.broadcastChanges();
                                            String id = enchantment.unwrapKey().orElseThrow().identifier().toString();
                                            reply(source, level == 0 ? "Removed " + id + "." : "Set " + id + " to level " + level + ".");
                                            return 1;
                                        })))));
    }

    private static void reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
