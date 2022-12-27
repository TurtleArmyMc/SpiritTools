package com.turtlearmymc.spirittools.entities;

import com.turtlearmymc.spirittools.items.SpiritToolItem;
import com.turtlearmymc.spirittools.network.S2CSummonSpiritToolPacket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.Packet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import java.util.*;
import java.util.stream.Collectors;

public abstract class SpiritToolEntity extends Entity {
	public static final int SUMMON_RANGE = 20;
	protected static final int DESPAWN_AGE = 200;

	protected int toolAge;
	protected ItemStack summonStack;
	protected UUID ownerUuid;
	protected PlayerEntity owner;

	protected List<ItemStack> inventory;
	protected int xpAmount;

	protected Set<BlockPos> scheduledMiningPositions;
	protected Block mineMaterial;
	protected int miningTicks;
	protected BlockPos miningAt;
	protected int prevBreakStage;

	public SpiritToolEntity(EntityType<?> type, World world) {
		super(type, world);
		inventory = new ArrayList<>();
		scheduledMiningPositions = new HashSet<>();
	}

	public int getTicksUntilDespawn() {
		return DESPAWN_AGE - toolAge;
	}

	public PlayerEntity getOwner() {
		if (owner != null && !owner.isRemoved()) {
			return owner;
		}
		if (getOwnerUUID() != null) {
			return world.getPlayerByUuid(getOwnerUUID());
		}
		return null;
	}

	public void setOwner(PlayerEntity owner) {
		this.owner = owner;
		setOwnerUUID(owner != null ? owner.getUuid() : null);
	}

	public ItemStack getSummonStack() {
		return summonStack;
	}

	public void setSummonStack(ItemStack stack) {
		summonStack = stack;
	}

	public UUID getOwnerUUID() {
		return ownerUuid;
	}

	public void setOwnerUUID(UUID ownerUuid) {
		this.ownerUuid = ownerUuid;
	}

	public Block getMineMaterial() {
		return mineMaterial;
	}

	public void resetDespawnTimer() {
		toolAge = 0;
	}

	protected int estimateBlocksBreakableWithinTime(BlockPos pos) {
		int estimatedTicksToBreak = calcTicksToBreak(mineMaterial.getDefaultState(), pos);
		int remainingTicks = getTicksUntilDespawn() + miningTicks - estimatedTicksToBreak;
		--remainingTicks; // 1 tick is removed because the tool is aged before mining
		if (miningAt == null) --remainingTicks; // 1 tick is removed for the time it's going to take to find the next block
		int blocks = 0;
		// 1 tick is removed at the end of each loop for the time it takes the tool to find the next block
		for (; remainingTicks > 0; remainingTicks -= estimatedTicksToBreak + 1) {
			blocks++;
		}
		return blocks;
	}

	public int estimateBreakableScheduledBlocks(BlockPos pos) {
		return Math.min(
				estimateBlocksBreakableWithinTime(pos),
				(int) scheduledMiningPositions.stream().filter(this::isPosOfMineMaterial).count()
		);
	}

	/**
	 * @return scheduled mining positions
	 */
	public Set<BlockPos> scheduleToMine(Block mineMaterial, Set<BlockPos> miningPositions) {
		Set<BlockPos> newlyScheduled = new HashSet<>(miningPositions);
		newlyScheduled.removeAll(scheduledMiningPositions);

		this.mineMaterial = mineMaterial;
		scheduledMiningPositions.addAll(miningPositions);

		return newlyScheduled;
	}

	@Override
	public void tick() {
		if (world.isClient()) {
			clientTick();
		} else {
			serverTick();
		}
		super.tick();
	}

	protected void clientTick() {
	}

	protected void serverTick() {
		if (++toolAge >= DESPAWN_AGE) {
			tryReturnToOwner();
			return;
		}

		// If the entity has not been fully deserialized/synced yet including the owner uuid, do nothing
		if (getOwnerUUID() == null) return;

		if (!holderWithinRange()) {
			tryReturnToOwner();
			return;
		}

		if (mineMaterial == null) return;
		if (miningAt == null) {
			if (!findNextMiningBlock()) {
				tryReturnToOwner();
				return;
			}
			lookAt(miningAt);
			// Take 1 tick before beginning to mine a block
			return;
		}
		++miningTicks;
		BlockState stateAt = world.getBlockState(miningAt);
		float breakProgress = calcBlockBreakingDelta(stateAt, miningAt) * miningTicks;
		int breakStage = (int) (breakProgress * 10f);
		if (breakProgress >= 1) {
			finishBreakingBlock(stateAt);
		} else if (breakStage != prevBreakStage) {
			world.setBlockBreakingInfo(getId(), miningAt, breakStage);
			prevBreakStage = breakStage;
		}
	}

	protected void finishBreakingBlock(BlockState state) {
		addDropsToInventory(miningAt, state);
		state.onStacksDropped((ServerWorld) world, miningAt, getSummonStack());
		collectXp(miningAt);
		world.breakBlock(miningAt, false, this);
		resetBlockBreakProgress();
	}

	protected void resetBlockBreakProgress() {
		world.setBlockBreakingInfo(getId(), miningAt, -1);
		miningAt = null;
		miningTicks = 0;
		prevBreakStage = 0;
	}

	protected void collectXp(BlockPos pos) {
		world.getEntitiesByType(EntityType.EXPERIENCE_ORB, new Box(pos), orb -> orb.age == 0 && !orb.isRemoved())
				.forEach(orb -> {
					xpAmount += orb.getExperienceAmount();
					orb.discard();
				});
	}

	public void tryReturnToOwner() {
		tryGiveItemsToOwner();
		tryGiveXpToOwner();
		tryClearItemNbt();
		discard();
	}

	protected void addDropsToInventory(BlockPos pos, BlockState state) {
		inventory.addAll(getDropStacks(pos, state));
	}

	protected List<ItemStack> getDropStacks(BlockPos pos, BlockState state) {
		BlockEntity blockEntity = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;
		return Block.getDroppedStacks(state, (ServerWorld) world, miningAt, blockEntity, this, getSummonStack());
	}

	protected boolean isSummonStack(ItemStack stack) {
		if (!(stack.getItem() instanceof SpiritToolItem<?>)) return false;
		if (!stack.hasNbt() || !stack.getNbt().contains("summonedTool")) return false;
		return getUuid().equals(stack.getNbt().getUuid("summonedTool"));
	}

	// Returns a list instead of a single item in case summon item was duplicated
	protected List<ItemStack> getSummonStacks() {
		if (getOwner() == null) return Collections.emptyList();

		List<ItemStack> summonItems = getOwner().getInventory().main.stream().filter(this::isSummonStack)
				.collect(Collectors.toCollection(ArrayList::new));
		if (isSummonStack(getOwner().getOffHandStack())) summonItems.add(getOwner().getOffHandStack());

		return summonItems;
	}

	protected boolean holderWithinRange() {
		if (getOwner() == null || squaredDistanceTo(getOwner()) >= SUMMON_RANGE * SUMMON_RANGE) return false;
		return !getSummonStacks().isEmpty();
	}

	public int calcTicksToBreak(BlockState state, BlockPos pos) {
		float delta = calcBlockBreakingDelta(state, pos);
		return delta != 0 ? (int) Math.ceil(1 / delta) : 0;
	}

	protected float calcBlockBreakingDelta(BlockState state, BlockPos pos) {
		float hardness = state.getHardness(world, pos);
		if (hardness == -1.0f) {
			return 0.0f;
		}
		return getBlockBreakingSpeed() / hardness / 30;
	}

	protected float getBlockBreakingSpeed() {
		ItemStack stack = getSummonStack();
		float speed = ((SpiritToolItem<?>) stack.getItem()).spiritToolMiningSpeed();

		int efficiency = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, stack);
		if (efficiency > 0) {
			speed += (float) (efficiency * efficiency + 1);
		}

		if (isSubmergedIn(FluidTags.WATER)) speed /= 5f;

		return speed;
	}

	protected void tryGiveItemsToOwner() {
		if (getOwner() != null) {
			inventory.forEach(getOwner().getInventory()::offerOrDrop);
			inventory.clear();
		}
	}

	protected void dropItems() {
		inventory.forEach(stack -> ItemScatterer.spawn(world, getX(), getY(), getZ(), stack));
		inventory.clear();
	}

	protected void tryGiveXpToOwner() {
		if (getOwner() != null) {
			getOwner().addExperience(xpAmount);
			xpAmount = 0;
		}
	}

	protected void dropXp() {
		ExperienceOrbEntity.spawn((ServerWorld) world, getPos(), xpAmount);
		xpAmount = 0;
	}

	protected void tryClearItemNbt() {
		getSummonStacks().forEach(stack -> stack.removeSubNbt("summonedTool"));
	}

	@Override
	public void remove(RemovalReason removalReason) {
		if (miningAt != null) {
			// Clear block breaking progress when removed
			world.setBlockBreakingInfo(getId(), miningAt, -1);
		}
		if (!world.isClient && removalReason.shouldDestroy()) {
			dropItems();
			dropXp();
			tryClearItemNbt();
		}

		super.remove(removalReason);
	}

	protected boolean isPosOfMineMaterial(BlockPos pos) {
		return mineMaterial.equals(world.getBlockState(pos).getBlock());
	}

	/**
	 * @return whether a block was found
	 */
	protected boolean findNextMiningBlock() {
		Optional<BlockPos> candidate = scheduledMiningPositions.stream()
				.sorted((a, b) -> (int) Math.signum(squaredDistanceTo(Vec3d.of(a)) - squaredDistanceTo(Vec3d.of(b))))
				.filter(this::isPosOfMineMaterial).findFirst();
		if (candidate.isEmpty()) return false;
		miningAt = candidate.get();
		return true;
	}

	@Override
	protected void initDataTracker() {
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.containsUuid("owner")) setOwnerUUID(nbt.getUuid("owner"));
		if (nbt.contains("toolAge")) toolAge = nbt.getInt("toolAge");

		if (nbt.contains("inventory")) inventory.addAll(
				nbt.getList("inventory", NbtCompound.COMPOUND_TYPE).stream().map(NbtCompound.class::cast)
						.map(ItemStack::fromNbt).toList());

		if (nbt.contains("xpAmount")) xpAmount = nbt.getInt("xpAmount");

		if (nbt.contains("miningPositions")) scheduledMiningPositions.addAll(
				nbt.getList("miningPositions", NbtElement.COMPOUND_TYPE).stream().map(NbtCompound.class::cast)
						.map(NbtHelper::toBlockPos).toList());

		if (nbt.contains("miningMaterial"))
			mineMaterial = Registry.BLOCK.get(new Identifier(nbt.getString("miningMaterial")));
		if (nbt.contains("miningProgress")) miningTicks = nbt.getInt("miningProgress");
		if (nbt.contains("miningAt")) miningAt = NbtHelper.toBlockPos(nbt.getCompound("miningAt"));

		if (nbt.contains("itemStack")) setSummonStack(ItemStack.fromNbt(nbt.getCompound("itemStack")));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putUuid("owner", getOwnerUUID());
		nbt.putInt("toolAge", toolAge);

		nbt.put("inventory", inventory.stream().map(stack -> stack.writeNbt(new NbtCompound()))
				.collect(Collectors.toCollection(NbtList::new)));

		nbt.putInt("xpAmount", xpAmount);

		nbt.put("miningPositions", scheduledMiningPositions.stream().map(NbtHelper::fromBlockPos)
				.collect(Collectors.toCollection(NbtList::new)));


		nbt.putString("miningMaterial", Registry.BLOCK.getId(mineMaterial).toString());
		nbt.putInt("miningProgress", miningTicks);
		if (miningAt != null) nbt.put("miningAt", NbtHelper.fromBlockPos(miningAt));

		if (!getSummonStack().isEmpty()) nbt.put("itemStack", getSummonStack().getNbt());
	}

	@Override
	public Packet<?> createSpawnPacket() {
		return S2CSummonSpiritToolPacket.createPacket(this);
	}

	protected void lookAtEntity(Entity targetEntity) {
		double y;
		if (targetEntity instanceof LivingEntity livingEntity) {
			y = livingEntity.getEyeY();
		} else {
			y = targetEntity.getBoundingBox().minY + targetEntity.getBoundingBox().maxY / 2.0;
		}
		lookAt(targetEntity.getX(), y, targetEntity.getZ());
	}

	protected void lookAt(BlockPos pos) {
		lookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	protected void lookAt(Vec3d pos) {
		lookAt(pos.getX(), pos.getY(), pos.getZ());
	}

	protected void lookAt(double x, double y, double z) {
		double xDelta = x - getX();
		double yDelta = y - getEyeY();
		double zDelta = z - getZ();

		double xyDistance = Math.sqrt(xDelta * xDelta + zDelta * zDelta);
		float yaw = (float) (MathHelper.atan2(zDelta, xDelta) * 57.2957763671875) - 90;
		float pitch = (float) (-(MathHelper.atan2(yDelta, xyDistance) * 57.2957763671875));
		setPitch(pitch);
		setYaw(yaw);
	}
}