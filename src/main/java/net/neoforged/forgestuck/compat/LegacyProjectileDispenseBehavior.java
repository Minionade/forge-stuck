package net.neoforged.forgestuck.compat;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

public abstract class LegacyProjectileDispenseBehavior extends DefaultDispenseItemBehavior {
    protected abstract Projectile m_6895_(Level level, Position position, ItemStack stack);

    protected float m_7101_() {
        return 6.0F;
    }

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        Direction direction = source.state().getValue(DispenserBlock.FACING);
        Position position = DispenserBlock.getDispensePosition(source);
        Projectile projectile = m_6895_(source.level(), position, stack.split(1));
        projectile.shoot(direction.getStepX(), direction.getStepY(), direction.getStepZ(), 6.0F, m_7101_());
        source.level().addFreshEntity(projectile);
        return stack;
    }

    @Override
    protected void playSound(BlockSource source) {
        source.level().levelEvent(1002, source.pos(), 0);
    }
}
