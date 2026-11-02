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
    static synchronized String read(Path path) throws IOException {
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
            if (!validCommand(command)) {
                throw new IllegalArgumentException("enchantCommandString must contain lowercase letters, digits, underscores or hyphens");
            }
            return command;
    }

    static boolean validCommand(String command) {
        return command != null && command.matches("[a-z0-9_-]+");
    }

    static synchronized void save(Path path, String command) throws IOException {
        if (!validCommand(command)) throw new IllegalArgumentException("Invalid command name.");
        JsonObject config = new JsonObject();
        if (Files.exists(path)) {
            // Refuse to replace a malformed file, and retain unknown options.
            config = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        config.addProperty("enchantCommandString", command);
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "enchanting_commands-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(config)
                    + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
