package com.turtlearmymc.spirittools.items;

import com.turtlearmymc.spirittools.entities.SpiritToolEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public abstract class SpiritToolItem extends Item {
	public SpiritToolItem(Settings settings) {
		super(settings);
	}

	protected abstract EntityType<? extends SpiritToolEntity> getToolEntityType();

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (!(world instanceof ServerWorld)) {
			return ActionResult.SUCCESS;
		}
		BlockPos blockPos = context.getBlockPos();
		BlockState blockState = world.getBlockState(blockPos);
		SpiritToolEntity toolEntity = getToolEntityType().create(world);

		if (!toolEntity.isSuitableFor(blockState)) return ActionResult.SUCCESS;

		ItemStack itemStack = context.getStack();
		Direction direction = context.getSide();
		Vec3d spawnAt = Vec3d.of(
				blockState.getCollisionShape(world, blockPos).isEmpty() ? blockPos : blockPos.offset(direction));

		toolEntity.setPosition(spawnAt);
		toolEntity.setOwner(context.getPlayer());
		toolEntity.scheduleToMine(blockPos);

		world.spawnEntity(toolEntity);
		return ActionResult.SUCCESS;
	}
}