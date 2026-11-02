package net.enchanting_commands;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnchantingCommands implements ModInitializer {
    public static final String MOD_ID = "enchanting_commands";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static CommandConfiguration configuration;

    private static java.nio.file.Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
    }

    public static String commandForEditing() throws java.io.IOException {
        return ModConfig.read(configPath());
    }

    public static boolean validCommandName(String command) {
        return ModConfig.validCommand(command);
    }

    // Called on the integrated server executor, or client thread when no world is open.
    public static net.minecraft.network.chat.Component saveEditedCommand(String command,
            net.minecraft.server.MinecraftServer server) {
        try {
            ModConfig.save(configPath(), command);
        } catch (java.io.IOException | RuntimeException exception) {
            return net.minecraft.network.chat.Component.literal("[Enchanting Commands] Could not save config. " + exception.getMessage());
        }
        if (server == null) {
            configuration.prepareNextServer(command);
            return net.minecraft.network.chat.Component.literal("[Enchanting Commands] Config saved. Command for the next world: /" + command + ".");
        }
        var feedback = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        configuration.reloadFromMenu(server, new net.minecraft.commands.CommandSource() {
            public void sendSystemMessage(net.minecraft.network.chat.Component message) { feedback.add(message); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        });
        return feedback.isEmpty() ? net.minecraft.network.chat.Component.literal("[Enchanting Commands] Config saved.") : feedback.getLast();
    }

    @Override
    public void onInitialize() {
        var path = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
        configuration = new CommandConfiguration(path, ModConfig.load(path));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(configuration::releaseServer);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) ->
                configuration.register(dispatcher, context));
        String version = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        LOGGER.info("[Enchanting Commands] Mod initialized. Version: {}", version);
    }
}
