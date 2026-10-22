package net.enchanting_commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ModConfig {
    private static final String DEFAULT_COMMAND = "ec";
    private ModConfig() {}

    static String load(Path path) {
        try {
            return read(path);
        } catch (IOException | RuntimeException exception) {
            EnchantingCommands.LOGGER.warn("[Enchanting Commands] Cannot load config {}; using /ec. Existing file preserved. {}",
                    path, exception.getMessage());
            return DEFAULT_COMMAND;
        }
    }

    // Reload callers handle errors without replacing the last working configuration.
    static String read(Path path) throws IOException {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                JsonObject defaults = new JsonObject();
                defaults.addProperty("enchantCommandString", DEFAULT_COMMAND);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(path, gson.toJson(defaults) + System.lineSeparator(), StandardCharsets.UTF_8);
                return DEFAULT_COMMAND;
            }
            JsonObject config = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            var value = config.get("enchantCommandString");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("enchantCommandString must be a string");
            }
            String command = value.getAsString();
            if (!command.matches("[a-z0-9_-]+")) {
                throw new IllegalArgumentException("enchantCommandString must contain lowercase letters, digits, underscores or hyphens");
            }
            return command;
    }
}
