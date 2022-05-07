package com.turtlearmymc.spirittools.items;

import com.turtlearmymc.spirittools.entities.SpiritPickaxeEntity;
import com.turtlearmymc.spirittools.entities.SpiritToolEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.MiningToolItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.TagKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public abstract class SpiritToolItem<ToolEntityType extends SpiritToolEntity> extends MiningToolItem {
	protected static final double SEARCH_BLOCK_RANGE = 5;

	public SpiritToolItem(float attackDamage, float attackSpeed, TagKey<Block> effectiveBlocks, Settings settings) {
		super(attackDamage, attackSpeed, SpiritToolMaterial.SPIRIT_TOOL_MATERIAL, effectiveBlocks, settings);
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

		return ((SpiritToolItem<?>) itemStack.getItem()).isEntitySummoned(itemStack, clientWorld, holder) ? 1 : 0;
	}

	// Spirit tools should not work as regular tools
	@Override
	public boolean isSuitableFor(BlockState state) {
		return false;
	}

	// Spirit tools should not work as regular tools
	@Override
	public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
		return 1;
	}

	protected abstract EntityType<ToolEntityType> getToolEntityType();

	protected boolean isEntitySummoned(ItemStack itemStack, World world, Entity holder) {
		return findSummonedEntity(itemStack, world, holder).isPresent();
	}

	protected Optional<ToolEntityType> findSummonedEntity(ItemStack itemStack, World world, Entity holder) {
		double expandBy = Math.sqrt(Math.pow(SpiritPickaxeEntity.SUMMON_RANGE, 2) * 2);
		return world.getEntitiesByType(getToolEntityType(), holder.getBoundingBox().expand(expandBy),
				spiritTool -> holder.getUuid().equals(spiritTool.getOwnerUUID()) && ItemStack.areEqual(
						spiritTool.getItemStack(), itemStack)
		).stream().findAny();
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		if (player == null) return ActionResult.FAIL;

		World world = context.getWorld();
		BlockPos blockPos = context.getBlockPos();
		BlockState state = world.getBlockState(blockPos);
		ItemStack itemStack = context.getStack();

		if (!(world instanceof ServerWorld)) {
			return spiritToolSuitableFor(state) && !isEntitySummoned(itemStack, world, player) ? ActionResult.SUCCESS :
					ActionResult.FAIL;
		}

		if (isEntitySummoned(itemStack, world, player)) return ActionResult.FAIL;

		Set<BlockPos> miningPositions = findBlocksToMine(world, blockPos);
		if (miningPositions == null) return ActionResult.FAIL;

		int damageAmount = 1;
		itemStack.damage(damageAmount, player, holder -> holder.sendToolBreakStatus(context.getHand()));
		Direction direction = context.getSide();
		Vec3d spawnAt =
				Vec3d.of(state.getCollisionShape(world, blockPos).isEmpty() ? blockPos : blockPos.offset(direction));

		ToolEntityType toolEntity = spawnToolEntity(world, spawnAt, player, itemStack);
		toolEntity.scheduleToMine(state.getBlock(), miningPositions);

		return ActionResult.SUCCESS;
	}

	public ToolEntityType spawnToolEntity(
			World world, Vec3d spawnAt, PlayerEntity owner, ItemStack stack
	) {
		ToolEntityType toolEntity = getToolEntityType().create(world);
		toolEntity.setPosition(spawnAt);
		toolEntity.setOwner(owner);
		toolEntity.setItemStack(stack);

		world.spawnEntity(toolEntity);

		return toolEntity;
	}

	public float spiritToolMiningSpeed() {
		return getMaterial().getMiningSpeedMultiplier();
	}

	protected boolean spiritToolSuitableFor(BlockState state) {
		return super.isSuitableFor(state);
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