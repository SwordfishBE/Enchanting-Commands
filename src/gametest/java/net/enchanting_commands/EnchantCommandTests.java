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
    public void configuredCommandName(GameTestHelper h) throws Exception {
        var source = source(h, new ItemStack(Items.STICK));
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<CommandSourceStack>();
        var context = net.minecraft.commands.CommandBuildContext.simple(source.registryAccess(), source.enabledFeatures());
        EnchantCommand.register(dispatcher, context, "custom_ec");
        h.assertTrue(dispatcher.execute("custom_ec enchant sharpness 1", source) == 1, "Custom root works");
        h.assertTrue(dispatcher.getRoot().getChild("ec") == null, "Default root not registered");
        h.succeed();
    }
}
