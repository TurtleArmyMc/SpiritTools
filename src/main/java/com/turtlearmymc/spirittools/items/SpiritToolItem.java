package com.turtlearmymc.spirittools.items;

import com.turtlearmymc.spirittools.entities.SpiritPickaxeEntity;
import com.turtlearmymc.spirittools.entities.SpiritToolEntity;
import net.fabricmc.yarn.constants.MiningLevels;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.TagKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public abstract class SpiritToolItem extends Item {
	protected static final double SEARCH_BLOCK_RANGE = 5;

	public SpiritToolItem(Settings settings) {
		super(settings);
	}

	public static float summonedPredicateProvider(
			ItemStack itemStack, ClientWorld clientWorld, LivingEntity entity, int seed
	) {
		Entity holder = entity != null ? entity : itemStack.getHolder();
		if (holder == null) return 0;

		if (clientWorld == null) {
			if (holder.world instanceof ClientWorld world) {
				clientWorld = world;
			} else {
				return 0;
			}
		}

		return ((SpiritToolItem) itemStack.getItem()).isEntitySummoned(clientWorld, holder) ? 1 : 0;
	}

	protected abstract ToolMaterial getMaterial();

	protected abstract TagKey<Block> getEffectiveBlocks();

	protected abstract EntityType<? extends SpiritToolEntity> getToolEntityType();

	protected boolean isEntitySummoned(World world, Entity holder) {
		double expandBy = Math.sqrt(Math.pow(SpiritPickaxeEntity.SUMMON_RANGE, 2) * 2);
		return !world.getEntitiesByType(getToolEntityType(), holder.getBoundingBox().expand(expandBy),
				spiritPickaxe -> holder.equals(spiritPickaxe.getOwner())
		).isEmpty();
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		if (player == null) return ActionResult.FAIL;

		World world = context.getWorld();
		BlockPos blockPos = context.getBlockPos();
		BlockState state = world.getBlockState(blockPos);
		if (!(world instanceof ServerWorld)) {
			return spiritToolSuitableFor(state) && !isEntitySummoned(world, player) ? ActionResult.SUCCESS :
					ActionResult.FAIL;
		}

		if (isEntitySummoned(world, player)) return ActionResult.FAIL;

		Set<BlockPos> miningPositions = findBlocksToMine(world, blockPos);
		if (miningPositions == null) return ActionResult.FAIL;

		ItemStack itemStack = context.getStack();
		Direction direction = context.getSide();
		Vec3d spawnAt =
				Vec3d.of(state.getCollisionShape(world, blockPos).isEmpty() ? blockPos : blockPos.offset(direction));

		SpiritToolEntity toolEntity = getToolEntityType().create(world);
		toolEntity.setPosition(spawnAt);
		toolEntity.setOwner(player);
		toolEntity.scheduleToMine(state.getBlock(), miningPositions);

		world.spawnEntity(toolEntity);
		return ActionResult.SUCCESS;
	}

	protected boolean spiritToolSuitableFor(BlockState state) {
		int i = getMaterial().getMiningLevel();
		if (i < MiningLevels.DIAMOND && state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) {
			return false;
		}
		if (i < MiningLevels.IRON && state.isIn(BlockTags.NEEDS_IRON_TOOL)) {
			return false;
		}
		if (i < MiningLevels.STONE && state.isIn(BlockTags.NEEDS_STONE_TOOL)) {
			return false;
		}
		return state.isIn(getEffectiveBlocks());
	}

	protected Set<BlockPos> findBlocksToMine(World world, BlockPos searchFrom) {
		BlockState state = world.getBlockState(searchFrom);
		if (!spiritToolSuitableFor(state)) return null;

		Set<BlockPos> positions = new HashSet<>();
		fillMiningPositions(positions, world, state.getBlock(), searchFrom, searchFrom);
		return positions;
	}

	protected void fillMiningPositions(
			Set<BlockPos> positions, World world, Block block, BlockPos center, BlockPos pos
	) {
		if (positions.contains(pos)) return;
		if (!center.isWithinDistance(pos, SEARCH_BLOCK_RANGE)) return;
		if (!world.getBlockState(pos).getBlock().equals(block)) return;
		positions.add(pos);
		fillMiningPositions(positions, world, block, center, pos.up());
		fillMiningPositions(positions, world, block, center, pos.down());
		fillMiningPositions(positions, world, block, center, pos.north());
		fillMiningPositions(positions, world, block, center, pos.south());
		fillMiningPositions(positions, world, block, center, pos.east());
		fillMiningPositions(positions, world, block, center, pos.west());
	}
}