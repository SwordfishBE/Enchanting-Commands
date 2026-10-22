package net.enchanting_commands.mixin;

import com.mojang.brigadier.tree.CommandNode;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Brigadier has no public removal API; keep both root indexes consistent. */
@Mixin(value = CommandNode.class, remap = false)
public interface CommandNodeAccessor {
    @Accessor("children")
    Map<String, ?> enchantingCommands$getChildren();

    @Accessor("literals")
    Map<String, ?> enchantingCommands$getLiterals();
}
