package net.enchanting_commands;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnchantingCommands implements ModInitializer {
    public static final String MOD_ID = "enchanting_commands";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        var path = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
        var configuration = new CommandConfiguration(path, ModConfig.load(path));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) ->
                configuration.register(dispatcher, context));
        String version = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        LOGGER.info("[Enchanting Commands] Mod initialized. Version: {}", version);
    }
}
