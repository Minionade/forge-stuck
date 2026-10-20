package net.neoforged.forgestuck.compat;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;

public final class LegacyBlockInteractionShim {
    private LegacyBlockInteractionShim() {}

    public static ItemInteractionResult toItemInteractionResult(InteractionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case PASS -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            case FAIL -> ItemInteractionResult.FAIL;
        };
    }
}
