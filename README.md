# Enchanting Commands

Provides convenient commands for vanilla, mod and datapack enchantments.

## Requirements and installation

- Install `enchanting_commands-<version>.jar` in the server's `mods` folder, or in the client instance for single-player. Multiplayer clients do not need this mod.

## Commands

Requires Minecraft's `COMMANDS_ADMIN` permission (vanilla operator level 3 or higher),
matching the original mod. Enchanting requires a player with an item in the main hand.

```mcfunction
/ec
/ec list
/ec enchant minecraft:sharpness 10
/ec enchant sharpness 255
/ec enchant nova_structures:antidote 5
/ec enchant sharpness 0
```

`/ec` displays usage. `/ec list` lists all currently registered enchantments.
Use Tab to complete enchantment IDs. Vanilla IDs accept the short form; custom IDs include their namespace. 
The Dungeons and Taverns example requires that pack or mod to be loaded successfully in the current world.

Levels are 0–255. A positive level sets or replaces that enchantment, including lowering its existing level; zero removes it.
Other enchantments and item data are preserved. Any held item can be enchanted, and mutually exclusive enchantments can coexist.
The command bypasses normal item compatibility and maximum levels; the actual effects still follow each enchantment's own conditions and implementation.
The entire held stack is affected. Like the original, enchantments are written to the active `minecraft:enchantments` component, including on books; this is not an anvil stored-book editor.

The console can display usage and the list, but cannot enchant an item.

## Configuration

Created once at `config/enchanting_commands.json`:

```json
{
  "enchantCommandString": "ec"
}
```

Change the root command if another mod uses `/ec`, then restart the server.
Use lowercase letters, digits, underscores or hyphens without the slash.
Existing files, including unknown options, are never rewritten on startup.
An invalid or unreadable file produces a warning and uses `/ec` for that run; the original file is preserved.

## Build and verification

Run `./gradlew build` (Windows: `.\gradlew.bat build`) with Java 25.
The installable jar is in `build/libs`; do not install the `-sources.jar`.
`build` includes configuration and text-rendering regression verification.

## License

AGPL-3.0; see [LICENSE](LICENSE). Copyright (c) 2026 SwordfishBE.