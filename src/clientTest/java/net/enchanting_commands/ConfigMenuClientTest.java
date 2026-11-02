package net.enchanting_commands;

import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AlertScreen;

public class ConfigMenuClientTest implements FabricClientGameTest {
    private ClothConfigScreen editor;

    private void open(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = new EnchantingCommandsModMenu().getModConfigScreenFactory().create(new TitleScreen());
            if (!EnchantingCommandsModMenu.hasCompatibleClothConfig()) {
                if (!(screen instanceof AlertScreen)) throw new AssertionError("Unsupported Cloth Config must show a safe notice");
                client.setScreenAndShow(screen);
                // Exercise the prepared entries/save pipeline without rendering incompatible widgets.
                editor = (ClothConfigScreen) EnchantingCommandsConfigScreen.create(new TitleScreen());
            } else {
                if (!(screen instanceof ClothConfigScreen cloth)) throw new AssertionError("Expected Cloth Config screen: " + screen);
                editor = cloth;
                client.setScreenAndShow(editor);
            }
        });
        context.waitTicks(3);
    }

    private StringListEntry entry() {
        return (StringListEntry) editor.getCategorizedEntries().values().iterator().next().getFirst();
    }

    private void save(ClientGameTestContext context, String name) {
        open(context);
        context.runOnClient(client -> {
            entry().setValue(name);
            editor.saveAll(true);
        });
        context.waitTicks(5);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cloth-config")) {
            context.runOnClient(client -> {
                var screen = new EnchantingCommandsModMenu().getModConfigScreenFactory().create(new TitleScreen());
                if (!(screen instanceof AlertScreen)) throw new AssertionError("Missing Cloth Config must show a notice");
                client.setScreenAndShow(screen);
            });
            context.waitTicks(3);
            return;
        }
        open(context);
        context.takeScreenshot("enchanting-commands-config");
        context.runOnClient(client -> {
            entry().setValue("/invalid name");
            if (entry().getConfigError().isEmpty()) throw new AssertionError("Invalid command must have an error");
            entry().setValue("cancelled_edit");
        });
        context.setScreen(TitleScreen::new);
        context.runOnClient(client -> {
            try {
                if (EnchantingCommands.commandForEditing().equals("cancelled_edit")) throw new AssertionError("Cancelled edit was saved");
            } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        });
        save(context, "menu_test");
        context.setScreen(TitleScreen::new);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                if (server.getCommands().getDispatcher().getRoot().getChild("menu_test") == null)
                    throw new AssertionError("Title-screen setting must apply to next integrated world");
            });
            save(context, "menu_live");
            world.getServer().runOnServer(server -> {
                var root = server.getCommands().getDispatcher().getRoot();
                if (root.getChild("menu_live") == null || root.getChild("menu_test") != null)
                    throw new AssertionError("In-world GUI save must rename command immediately");
            });
            save(context, "help");
            world.getServer().runOnServer(server -> {
                var root = server.getCommands().getDispatcher().getRoot();
                if (root.getChild("menu_live") == null || root.getChild("help") == null)
                    throw new AssertionError("Occupied name must preserve active command and vanilla help");
            });
            save(context, "ec");
            world.getServer().runOnServer(server -> {
                var root = server.getCommands().getDispatcher().getRoot();
                if (root.getChild("ec") == null || root.getChild("menu_live") != null)
                    throw new AssertionError("GUI must recover after a rejected rename");
            });
        }
    }
}
