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
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
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
	private static final TrackedData<ItemStack> STACK =
			DataTracker.registerData(SpiritToolEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
	private static final TrackedData<Optional<UUID>> OWNER_UUID =
			DataTracker.registerData(SpiritToolEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	protected int toolAge;
	protected Entity owner;

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

	public Entity getOwner() {
		if (owner != null && !owner.isRemoved()) {
			return owner;
		}
		if (getOwnerUUID() != null && world instanceof ServerWorld serverWorld) {
			owner = serverWorld.getEntity(getOwnerUUID());
			return owner;
		}
		return null;
	}

	public void setOwner(LivingEntity owner) {
		this.owner = owner;
		setOwnerUUID(owner != null ? owner.getUuid() : null);
	}

	public ItemStack getItemStack() {
		return getDataTracker().get(STACK);
	}

	public void setItemStack(ItemStack itemStack) {
		getDataTracker().set(STACK, itemStack);
	}

	public UUID getOwnerUUID() {
		return getDataTracker().get(OWNER_UUID).orElse(null);
	}

	public void setOwnerUUID(UUID uuid) {
		getDataTracker().set(OWNER_UUID, Optional.ofNullable(uuid));
	}

	/**
	 * @return scheduled mining positions
	 */
	public Set<BlockPos> scheduleToMine(Block mineMaterial, Set<BlockPos> miningPositions) {
		Set<BlockPos> scheduled = new HashSet<>(miningPositions);
		scheduled.removeAll(scheduledMiningPositions);

		this.mineMaterial = mineMaterial;
		scheduledMiningPositions.addAll(miningPositions);

		return scheduled;
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

		if (!ownerWithinRange()) {
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
		state.onStacksDropped((ServerWorld) world, miningAt, getItemStack());
		collectXp(miningAt);

		world.setBlockBreakingInfo(getId(), miningAt, -1);
		world.breakBlock(miningAt, false, this);

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
		discard();
	}

	protected void addDropsToInventory(BlockPos pos, BlockState state) {
		inventory.addAll(getDropStacks(pos, state));
	}

	protected List<ItemStack> getDropStacks(BlockPos pos, BlockState state) {
		BlockEntity blockEntity = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;
		return Block.getDroppedStacks(state, (ServerWorld) world, miningAt, blockEntity, this, getItemStack());
	}

	protected boolean ownerWithinRange() {
		if (getOwner() == null || squaredDistanceTo(getOwner()) >= SUMMON_RANGE * SUMMON_RANGE) return false;
		if (getOwner() instanceof PlayerEntity player) {
			return player.getInventory().contains(getItemStack());
		}
		return false;
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
		ItemStack stack = getItemStack();
		float speed = ((SpiritToolItem<?>) stack.getItem()).spiritToolMiningSpeed();

		int efficiency = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, stack);
		if (efficiency > 0) {
			speed += (float) (efficiency * efficiency + 1);
		}

		if (isSubmergedIn(FluidTags.WATER)) speed /= 5f;

		return speed;
	}

	protected void tryGiveItemsToOwner() {
		if (getOwner() instanceof PlayerEntity player) {
			inventory.forEach(player.getInventory()::offerOrDrop);
			inventory.clear();
		}
	}

	protected void dropItems() {
		inventory.forEach(stack -> ItemScatterer.spawn(world, getX(), getY(), getZ(), stack));
		inventory.clear();
	}

	protected void tryGiveXpToOwner() {
		if (getOwner() instanceof PlayerEntity player) {
			player.addExperience(xpAmount);
			xpAmount = 0;
		}
	}

	protected void dropXp() {
		ExperienceOrbEntity.spawn((ServerWorld) world, getPos(), xpAmount);
		xpAmount = 0;
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
		}

		super.remove(removalReason);
	}

	/**
	 * @return whether a block was found
	 */
	protected boolean findNextMiningBlock() {
		Optional<BlockPos> candidate = scheduledMiningPositions.stream()
				.sorted((a, b) -> (int) Math.signum(squaredDistanceTo(Vec3d.of(a)) - squaredDistanceTo(Vec3d.of(b))))
				.filter(pos -> mineMaterial.equals(world.getBlockState(pos).getBlock())).findFirst();
		if (candidate.isEmpty()) return false;
		miningAt = candidate.get();
		return true;
	}

	@Override
	protected void initDataTracker() {
		getDataTracker().startTracking(STACK, ItemStack.EMPTY);
		getDataTracker().startTracking(OWNER_UUID, Optional.empty());
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

		if (nbt.contains("itemStack")) setItemStack(ItemStack.fromNbt(nbt.getCompound("itemStack")));
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

		if (!getItemStack().isEmpty()) nbt.put("itemStack", getItemStack().getNbt());
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