package net.enchanting_commands;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigSaveTest {
    @TempDir Path directory;

    @Test void editingPreservesUnknownOptionsAndRejectsInvalidInput() throws Exception {
        var file = directory.resolve("config.json");
        Files.writeString(file, "{\"enchantCommandString\":\"ec\",\"futureOption\":{\"value\":73}}");
        ModConfig.save(file, "ench");
        assertEquals("ench", ModConfig.read(file));
        assertTrue(Files.readString(file).contains("futureOption"));
        var saved = Files.readString(file);
        assertThrows(IllegalArgumentException.class, () -> ModConfig.save(file, "/invalid name"));
        assertEquals(saved, Files.readString(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void malformedFileIsNeverOverwrittenByEditor() throws Exception {
        var file = directory.resolve("config.json");
        Files.writeString(file, "{broken");
        assertThrows(RuntimeException.class, () -> ModConfig.save(file, "ench"));
        assertEquals("{broken", Files.readString(file));
    }
}
