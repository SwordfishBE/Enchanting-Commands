package net.enchanting_commands;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftCompatibilityTest {
    @ParameterizedTest
    @ValueSource(strings = {"26.4-snapshot-6", "26.4-snapshot-7", "26.4-snapshot-10",
            "26.4-pre-1", "26.4-rc-1", "26.4"})
    void acceptsSnapshotSixThroughFinalRelease(String version) throws Exception {
        assertTrue(supports(version), version);
    }

    @ParameterizedTest
    @ValueSource(strings = {"26.3", "26.4-snapshot-1", "26.4-snapshot-5",
            "26.4.1-pre-1", "26.4.1", "26.5-snapshot-1", "26.5", "27.1"})
    void rejectsVersionsOutsideRequestedRange(String version) throws Exception {
        assertFalse(supports(version), version);
    }

    private boolean supports(String gameVersion) throws Exception {
        var version = Version.parse(McVersionLookup.normalizeVersion(gameVersion, McVersionLookup.getRelease(gameVersion)));
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/fabric.mod.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var range = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonObject("depends").get("minecraft").getAsString();
            return VersionPredicate.parse(range).test(version);
        }
    }
}
