package net.enchanting_commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

public class EnchantCommandTests {
    private CommandSourceStack source(GameTestHelper h, ItemStack item) {
        var player = h.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        return h.getLevel().getServer().createCommandSourceStack().withEntity(player)
                .withLevel(h.getLevel()).withPermission(LevelBasedPermissionSet.ADMIN).withSuppressedOutput();
    }

    private int run(CommandSourceStack source, String command) throws CommandSyntaxException {
        return source.getServer().getCommands().getDispatcher().execute(command, source);
    }

    private void rejected(GameTestHelper h, CommandSourceStack source, String command) {
        try {
            run(source, command);
            h.fail("Command should be rejected: " + command);
        } catch (CommandSyntaxException expected) { }
    }

    @GameTest
    public void setReplaceRemoveAndPreserve(GameTestHelper h) throws Exception {
        var item = new ItemStack(Items.DIAMOND_SWORD);
        var source = source(h, item);
        var registry = source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var sharpness = registry.getOrThrow(Enchantments.SHARPNESS);
        var smite = registry.getOrThrow(Enchantments.SMITE);
        run(source, "ec enchant sharpness 255");
        run(source, "ec enchant smite 10");
        h.assertTrue(item.getEnchantments().getLevel(sharpness) == 255, "Allow level 255");
        h.assertTrue(item.getEnchantments().getLevel(smite) == 10, "Allow incompatible enchantments");
        run(source, "ec enchant sharpness 2");
        h.assertTrue(item.getEnchantments().getLevel(sharpness) == 2, "Lower existing level");
        run(source, "ec enchant sharpness 0");
        h.assertTrue(item.getEnchantments().getLevel(sharpness) == 0, "Remove with zero");
        h.assertTrue(item.getEnchantments().getLevel(smite) == 10, "Preserve other enchantments");
        h.assertTrue(run(source, "ec enchant sharpness 0") == 0, "Missing enchantment reported");
        h.succeed();
    }

    @GameTest
    public void customRegistryAndCompletion(GameTestHelper h) throws Exception {
        var item = new ItemStack(Items.STICK, 12);
        var source = source(h, item);
        String id = "enchanting_commands_test:probe";
        run(source, "ec enchant " + id + " 42");
        var enchantment = source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(net.minecraft.resources.Identifier.parse(id)).orElseThrow();
        h.assertTrue(item.getEnchantments().getLevel(enchantment) == 42, "Custom enchantment on non-tool");
        h.assertTrue(item.getCount() == 12, "Stack size preserved");
        var dispatcher = source.getServer().getCommands().getDispatcher();
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("ec enchant enchanting_commands_test:", source)).get();
        h.assertTrue(suggestions.getList().stream().anyMatch(s -> s.getText().equals(id)), "Custom ID in tab completion");
        run(source, "ec enchant " + id + " 0");
        h.assertTrue(item.getEnchantments().isEmpty(), "Custom enchantment removal");
        h.succeed();
    }

    @GameTest
    public void permissionAndArgumentValidation(GameTestHelper h) throws Exception {
        var item = new ItemStack(Items.STICK);
        var source = source(h, item);
        rejected(h, source, "ec enchant sharpness -1");
        rejected(h, source, "ec enchant sharpness 256");
        rejected(h, source, "ec enchant missing:enchantment 1");
        rejected(h, source.withPermission(LevelBasedPermissionSet.ALL), "ec enchant sharpness 1");
        h.assertTrue(item.getEnchantments().isEmpty(), "Rejected commands must not change item");
        h.assertTrue(run(source(h, ItemStack.EMPTY), "ec enchant sharpness 1") == 0, "Empty hand rejected");
        h.succeed();
    }

    @GameTest
    public void consoleAndUsage(GameTestHelper h) throws Exception {
        var source = h.getLevel().getServer().createCommandSourceStack().withSuppressedOutput();
        h.assertTrue(run(source, "ec") == 1, "Console usage");
        h.assertTrue(run(source, "ec list") == 1, "Console list");
        rejected(h, source, "ec enchant sharpness 1");
        h.succeed();
    }

    @GameTest
    public void everyLoadedCustomEnchantment(GameTestHelper h) throws Exception {
        var item = new ItemStack(Items.STICK);
        var source = source(h, item);
        var registry = source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int tested = 0;
        for (var id : registry.keySet()) {
            if (id.getNamespace().equals("minecraft")) continue;
            var holder = registry.get(id).orElseThrow();
            run(source, "ec enchant " + id + " 255");
            h.assertTrue(item.getEnchantments().getLevel(holder) == 255, "Set custom enchantment " + id);
            run(source, "ec enchant " + id + " 0");
            h.assertTrue(item.getEnchantments().getLevel(holder) == 0, "Remove custom enchantment " + id);
            tested++;
        }
        h.assertTrue(tested > 0, "At least the test datapack enchantment must be loaded");
        EnchantingCommands.LOGGER.debug("[Enchanting Commands] Tested {} custom registry enchantments.", tested);
        h.succeed();
    }

    @GameTest
    public void configPersistsAndInvalidFileIsPreserved(GameTestHelper h) throws Exception {
        var directory = java.nio.file.Files.createTempDirectory("enchanting-commands-test-");
        var file = directory.resolve("config.json");
        try {
            h.assertTrue(ModConfig.load(file).equals("ec"), "Default config created");
            String valid = "{\"enchantCommandString\":\"my_enchants\",\"futureOption\":true}";
            java.nio.file.Files.writeString(file, valid);
            h.assertTrue(ModConfig.load(file).equals("my_enchants"), "Configured command loaded");
            h.assertTrue(java.nio.file.Files.readString(file).equals(valid), "Existing settings preserved");
            for (String invalid : java.util.List.of("{broken", "null", "{\"enchantCommandString\":\"/bad name\"}")) {
                java.nio.file.Files.writeString(file, invalid);
                h.assertTrue(ModConfig.load(file).equals("ec"), "Invalid config uses default");
                h.assertTrue(java.nio.file.Files.readString(file).equals(invalid), "Invalid file preserved");
            }
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(directory);
        }
        h.succeed();
    }

    @GameTest
    public void reloadRenamesImmediatelyAndRejectsUnsafeChanges(GameTestHelper h) throws Exception {
        var directory = java.nio.file.Files.createTempDirectory("enchanting-reload-test-");
        var file = directory.resolve("config.json");
        try {
            var messages = new java.util.ArrayList<String>();
            var output = new net.minecraft.commands.CommandSource() {
                public void sendSystemMessage(net.minecraft.network.chat.Component message) { messages.add(message.getString()); }
                public boolean acceptsSuccess() { return true; }
                public boolean acceptsFailure() { return true; }
                public boolean shouldInformAdmins() { return false; }
            };
            var source = h.getLevel().getServer().createCommandSourceStack().withSource(output);
            var dispatcher = new com.mojang.brigadier.CommandDispatcher<CommandSourceStack>();
            var context = net.minecraft.commands.CommandBuildContext.simple(source.registryAccess(), source.enabledFeatures());
            var configuration = new CommandConfiguration(file, "reload_before");
            configuration.register(dispatcher, context);
            java.nio.file.Files.writeString(file, "{\"enchantCommandString\":\"reload_after\"}");
            try {
                dispatcher.execute("reload_before reload", source.withPermission(LevelBasedPermissionSet.ALL));
                h.fail("Reload must require admin permission");
            } catch (CommandSyntaxException expected) { }
            h.assertTrue(dispatcher.getRoot().getChild("reload_after") == null, "Unauthorized reload cannot rename");
            h.assertTrue(dispatcher.execute("reload_before reload", source) == 1, "Rename succeeds");
            h.assertTrue(messages.getLast().equals("[Enchanting Commands] Config reloaded. Active command: /reload_after."), "Prefixed success feedback");
            h.assertTrue(dispatcher.getRoot().getChild("reload_before") == null, "Old command removed from root");
            try {
                dispatcher.execute("reload_before list", source);
                h.fail("Old command must stop parsing");
            } catch (CommandSyntaxException expected) { }
            h.assertTrue(dispatcher.execute("reload_after list", source) == 1, "New command active immediately");
            var completion = dispatcher.getCompletionSuggestions(dispatcher.parse("reload_", source)).get();
            h.assertTrue(completion.getList().stream().anyMatch(s -> s.getText().equals("reload_after")), "New root completes");
            h.assertFalse(completion.getList().stream().anyMatch(s -> s.getText().equals("reload_before")), "Old root no longer completes");

            java.nio.file.Files.writeString(file, "{broken");
            h.assertTrue(dispatcher.execute("reload_after reload", source) == 0, "Invalid config rejected");
            h.assertTrue(messages.getLast().startsWith("[Enchanting Commands] Config reload failed."), "Prefixed failure feedback");
            h.assertTrue(java.nio.file.Files.readString(file).equals("{broken"), "Invalid file untouched");
            h.assertTrue(dispatcher.execute("reload_after list", source) == 1, "Keep working command on invalid config");

            var occupied = dispatcher.register(net.minecraft.commands.Commands.literal("occupied").executes(c -> 73));
            java.nio.file.Files.writeString(file, "{\"enchantCommandString\":\"occupied\"}");
            h.assertTrue(dispatcher.execute("reload_after reload", source) == 0, "Command collision rejected");
            h.assertTrue(messages.getLast().startsWith("[Enchanting Commands] Config reload failed:"), "Prefixed collision feedback");
            h.assertTrue(dispatcher.getRoot().getChild("occupied") == occupied, "Other command node untouched");
            h.assertTrue(dispatcher.execute("occupied", source) == 73, "Other command behavior preserved");

            java.nio.file.Files.writeString(file, "{\"enchantCommandString\":\"reload_before\"}");
            h.assertTrue(dispatcher.execute("reload_after reload", source) == 1, "Rename back succeeds");
            h.assertTrue(dispatcher.execute("reload_before reload", source) == 1, "Unchanged config reload succeeds");
            h.assertTrue(dispatcher.getRoot().getChild("reload_after") == null, "Intermediate root removed");
            var rebuilt = new com.mojang.brigadier.CommandDispatcher<CommandSourceStack>();
            configuration.register(rebuilt, context);
            h.assertTrue(rebuilt.getRoot().getChild("reload_before") != null, "Latest name survives dispatcher rebuild");
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(directory);
        }
        h.succeed();
    }

    @GameTest
    public void sharedCommandBranchesSurviveRejectedRename(GameTestHelper h) throws Exception {
        var directory = java.nio.file.Files.createTempDirectory("enchanting-shared-root-test-");
        var file = directory.resolve("config.json");
        try {
            var messages = new java.util.ArrayList<String>();
            var output = new net.minecraft.commands.CommandSource() {
                public void sendSystemMessage(net.minecraft.network.chat.Component message) { messages.add(message.getString()); }
                public boolean acceptsSuccess() { return true; }
                public boolean acceptsFailure() { return true; }
                public boolean shouldInformAdmins() { return false; }
            };
            var source = h.getLevel().getServer().createCommandSourceStack().withSource(output);
            var context = net.minecraft.commands.CommandBuildContext.simple(source.registryAccess(), source.enabledFeatures());
            for (int scenario = 0; scenario < 4; scenario++) {
                var dispatcher = new com.mojang.brigadier.CommandDispatcher<CommandSourceStack>();
                var configuration = new CommandConfiguration(file, "shared_before");
                configuration.register(dispatcher, context);
                var addition = net.minecraft.commands.Commands.literal("shared_before");
                String foreignCommand;
                switch (scenario) {
                    case 0 -> {
                        addition.then(net.minecraft.commands.Commands.literal("foreign").executes(c -> 73));
                        foreignCommand = "shared_before foreign";
                    }
                    case 1 -> {
                        addition.then(net.minecraft.commands.Commands.literal("list")
                                .then(net.minecraft.commands.Commands.literal("foreign").executes(c -> 73)));
                        foreignCommand = "shared_before list foreign";
                    }
                    case 2 -> {
                        addition.executes(c -> 73);
                        foreignCommand = "shared_before";
                    }
                    default -> {
                        addition.then(net.minecraft.commands.Commands.literal("list").executes(c -> 73));
                        foreignCommand = "shared_before list";
                    }
                }
                dispatcher.register(addition);
                var original = dispatcher.getRoot().getChild("shared_before");
                h.assertTrue(dispatcher.execute(foreignCommand, source) == 73, "Foreign command works before rename");
                String requested = "{\"enchantCommandString\":\"shared_after\"}";
                java.nio.file.Files.writeString(file, requested);
                h.assertTrue(dispatcher.execute("shared_before reload", source) == 0, "Shared root rename rejected: " + scenario);
                h.assertTrue(messages.getLast().startsWith("[Enchanting Commands] Config reload failed:"), "Prefixed shared-root error");
                h.assertTrue(dispatcher.getRoot().getChild("shared_before") == original, "Shared root preserved");
                h.assertTrue(dispatcher.getRoot().getChild("shared_after") == null, "No replacement registered on failure");
                h.assertTrue(dispatcher.execute(foreignCommand, source) == 73, "Foreign behavior preserved after rejection");
                h.assertTrue(java.nio.file.Files.readString(file).equals(requested), "Requested configuration preserved");
                var rebuilt = new com.mojang.brigadier.CommandDispatcher<CommandSourceStack>();
                configuration.register(rebuilt, context);
                h.assertTrue(rebuilt.getRoot().getChild("shared_before") != null, "Failed rename must not change active name");
                java.nio.file.Files.writeString(file, "{\"enchantCommandString\":\"shared_before\"}");
                h.assertTrue(dispatcher.execute("shared_before reload", source) == 1, "Same-name reload remains safe for a shared root");
            }
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(directory);
        }
        h.succeed();
    }

    @GameTest
    public void configuredCommandName(GameTestHelper h) throws Exception {
        var source = source(h, new ItemStack(Items.STICK));
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<CommandSourceStack>();
        var context = net.minecraft.commands.CommandBuildContext.simple(source.registryAccess(), source.enabledFeatures());
        new CommandConfiguration(java.nio.file.Path.of("unused-test-config.json"), "custom_ec").register(dispatcher, context);
        h.assertTrue(dispatcher.execute("custom_ec enchant sharpness 1", source) == 1, "Custom root works");
        h.assertTrue(dispatcher.getRoot().getChild("ec") == null, "Default root not registered");
        h.succeed();
    }
}
