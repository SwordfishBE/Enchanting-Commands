package net.enchanting_commands;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Kept separate so this entrypoint can load without any Cloth classes. */
public class MissingClothClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = new EnchantingCommandsModMenu().getModConfigScreenFactory().create(new TitleScreen());
            if (!(screen instanceof AlertScreen)) throw new AssertionError("Missing Cloth Config must show a notice");
            client.setScreenAndShow(screen);
        });
        context.waitTicks(3);
        context.setScreen(TitleScreen::new);
    }
}
