package com.turtlearmymc.spirittools.items;

import com.turtlearmymc.spirittools.entities.SpiritToolEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.tag.TagKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

	public static ActionResult attackBlockHandler(
			PlayerEntity player, World world, Hand hand, BlockPos hitPos, Direction hitSide
	) {
		if (!world.isClient() && player.getMainHandStack().getItem() instanceof SpiritToolItem<?> spiritToolItem) {
			spiritToolItem.onSpiritToolSwing(player.getMainHandStack(), world, player, hitPos, hitSide);
		}
		return ActionResult.PASS;
	}

	// Spirit tools should not be able to break blocks of their tool type
	@Override
	public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
		return !super.isSuitableFor(state);
	}

	// Spirit tools should not work as regular tools
	@Override
	public boolean isSuitableFor(BlockState state) {
		return false;
	}

	// Spirit tools should not be able to break blocks of their tool type
	@Override
	public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
		return super.isSuitableFor(state) ? 0 : 1;
	}

	protected abstract EntityType<ToolEntityType> getToolEntityType();

	protected boolean isEntitySummoned(ItemStack itemStack, World world, Entity holder) {
		return findSummonedEntity(itemStack, world, holder).isPresent();
	}

	protected Optional<ToolEntityType> findSummonedEntity(ItemStack stack, World world, Entity holder) {
		if (!stack.hasNbt() || !stack.getNbt().contains("summonedTool")) return Optional.empty();

		UUID toolUuid = stack.getNbt().getUuid("summonedTool");
		double expandBy = Math.sqrt(Math.pow(SpiritToolEntity.SUMMON_RANGE, 2) * 2);
		return world.getEntitiesByType(getToolEntityType(), holder.getBoundingBox().expand(expandBy),
				spiritTool -> spiritTool.getUuid().equals(toolUuid)
		).stream().findAny();
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity holder, Hand hand) {
		ItemStack stack = holder.getStackInHand(hand);
		if (world.isClient()) {
			return isEntitySummoned(stack, world, holder) ? TypedActionResult.success(stack) :
					TypedActionResult.fail(stack);
		}
		boolean recalled = tryRecallToolEntity(stack, world, holder);
		return recalled ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
	}

	public void onSpiritToolSwing(
			ItemStack stack, World world, PlayerEntity holder, BlockPos hitPos, Direction hitSide
	) {
		// FIXME: This should be done after tool is found/spawned
		Set<BlockPos> miningPositions = findBlocksToMine(world, hitPos);
		if (miningPositions == null) return;

		BlockState state = world.getBlockState(hitPos);

		ToolEntityType toolEntity;
		int previousEstimatedBreakableBlocks = 0;
		Optional<ToolEntityType> foundEntity = findSummonedEntity(stack, world, holder);
		if (foundEntity.isPresent()) {
			toolEntity = foundEntity.get();
			// TODO: Allow multiple materials to be scheduled simultaneously
			if (toolEntity.getMineMaterial() != state.getBlock()) return;
			previousEstimatedBreakableBlocks = toolEntity.estimateBreakableScheduledBlocks(hitPos);
			toolEntity.resetDespawnTimer();
		} else {
			Vec3d spawnAt =
					Vec3d.of(state.getCollisionShape(world, hitPos).isEmpty() ? hitPos : hitPos.offset(hitSide));
			toolEntity = spawnToolEntity(world, spawnAt, holder, stack);
		}
		Set<BlockPos> scheduledPositions = toolEntity.scheduleToMine(state.getBlock(), miningPositions);
		int damageAmount = toolEntity.estimateBreakableScheduledBlocks(hitPos) - previousEstimatedBreakableBlocks;
		stack.damage(damageAmount, holder, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
	}

	protected ToolEntityType spawnToolEntity(
			World world, Vec3d spawnAt, PlayerEntity owner, ItemStack stack
	) {
		ToolEntityType toolEntity = getToolEntityType().create(world);
		toolEntity.setPosition(spawnAt);
		toolEntity.setOwner(owner);
		toolEntity.setSummonStack(stack);

		world.spawnEntity(toolEntity);

		stack.setSubNbt("summonedTool", NbtHelper.fromUuid(toolEntity.getUuid()));

		return toolEntity;
	}

	protected boolean tryRecallToolEntity(ItemStack stack, World world, PlayerEntity holder) {
		Optional<ToolEntityType> toolEntity = findSummonedEntity(stack, world, holder);
		toolEntity.ifPresent(ToolEntityType::tryReturnToOwner);
		return toolEntity.isPresent();
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