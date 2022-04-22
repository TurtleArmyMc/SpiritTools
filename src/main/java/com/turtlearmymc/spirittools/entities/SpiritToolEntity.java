package com.turtlearmymc.spirittools.entities;

import com.turtlearmymc.spirittools.items.SpiritToolItem;
import com.turtlearmymc.spirittools.network.S2CSummonSpiritToolPacket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
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

public abstract class SpiritToolEntity extends Entity {
	public static final int SUMMON_RANGE = 20;
	protected static final int DESPAWN_AGE = 200;
	protected static final int MAX_TICKS_OUTSIDE_RANGE = 20;

	protected int toolAge;
	protected Entity owner;
	protected UUID ownerUuid;
	protected ItemStack itemStack;

	protected List<ItemStack> inventory;
	protected int xpAmount;

	protected Set<BlockPos> scheduledMiningPositions;
	protected Block mineMaterial;
	protected int miningTicks;
	protected BlockPos miningAt;
	protected int prevBreakStage;

	protected int ticksOutsideRange;

	public SpiritToolEntity(EntityType<?> type, World world) {
		super(type, world);
		inventory = new ArrayList<>();
		scheduledMiningPositions = new HashSet<>();
	}

	protected abstract ToolMaterial getMaterial();

	protected abstract SpiritToolItem getItem();

	public Entity getOwner() {
		if (owner != null && !owner.isRemoved()) {
			return owner;
		}
		if (ownerUuid != null && world instanceof ServerWorld serverWorld) {
			owner = serverWorld.getEntity(ownerUuid);
			return owner;
		}
		return null;
	}

	public void setOwner(LivingEntity owner) {
		this.owner = owner;
		ownerUuid = owner != null ? owner.getUuid() : null;
	}

	public ItemStack getItemStack() {
		return itemStack;
	}

	public void setItemStack(ItemStack itemStack) {
		this.itemStack = itemStack;
	}

	public UUID getOwnerUUID() {
		return ownerUuid;
	}

	public void scheduleToMine(Block mineMaterial, Set<BlockPos> miningPositions) {
		this.mineMaterial = mineMaterial;
		scheduledMiningPositions.addAll(miningPositions);
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
			returnToOwner();
			return;
		}

		// If the entity has not been fully deserialized yet including the ownerUuid, do nothing
		if (ownerUuid == null) return;

		ticksOutsideRange = ownerWithinRange() ? 0 : ticksOutsideRange + 1;
		if (ticksOutsideRange >= MAX_TICKS_OUTSIDE_RANGE) {
			returnToOwner();
			return;
		}

		if (mineMaterial == null) return;
		if (miningAt == null) {
			if (!findNextMiningBlock()) {
				returnToOwner();
				return;
			}
			lookAt(miningAt);
		}
		++miningTicks;
		BlockState stateAt = world.getBlockState(miningAt);
		float breakProgress = calcBlockBreakingDelta(stateAt) * (miningTicks + 1f);
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
		state.onStacksDropped((ServerWorld) world, miningAt, itemStack);
		collectXp(miningAt);

		world.setBlockBreakingInfo(getId(), miningAt, -1);
		world.breakBlock(miningAt, false, this);

		miningAt = null;
		miningTicks = 0;
		prevBreakStage = 0;
	}

	protected void collectXp(BlockPos pos) {
		world.getEntitiesByType(EntityType.EXPERIENCE_ORB, new Box(pos), orb -> orb.age == 0).forEach(orb -> {
			xpAmount += orb.getExperienceAmount();
			orb.discard();
		});
	}

	protected void returnToOwner() {
		if (!giveItemsToOwner()) dropItems();
		if (!giveXpToOwner()) dropXp();
		discard();
	}

	protected void addDropsToInventory(BlockPos pos, BlockState state) {
		inventory.addAll(getDropStacks(pos, state));
	}

	protected List<ItemStack> getDropStacks(BlockPos pos, BlockState state) {
		BlockEntity blockEntity = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;
		return Block.getDroppedStacks(state, (ServerWorld) world, miningAt, blockEntity, this, itemStack);
	}

	protected boolean ownerWithinRange() {
		if (getOwner() == null || squaredDistanceTo(getOwner()) >= SUMMON_RANGE * SUMMON_RANGE) return false;
		if (getOwner() instanceof PlayerEntity player) {
			return player.getInventory().contains(itemStack);
		}
		return false;
	}

	protected float calcBlockBreakingDelta(BlockState stateAt) {
		float hardness = stateAt.getHardness(world, miningAt);
		if (hardness == -1.0f) {
			return 0.0f;
		}
		return getBlockBreakingSpeed() / hardness / 30;
	}

	protected float getBlockBreakingSpeed() {
		float speed = getMaterial().getMiningSpeedMultiplier();

		if (isSubmergedIn(FluidTags.WATER)) speed /= 5f;

		return speed;
	}

	/**
	 * @return whether items were successfully inserted/dropped
	 */
	protected boolean giveItemsToOwner() {
		if (getOwner() instanceof PlayerEntity player) {
			inventory.forEach(player.getInventory()::offerOrDrop);
			inventory.clear();
			return true;
		}
		return false;
	}

	protected void dropItems() {
		inventory.forEach(stack -> ItemScatterer.spawn(world, getX(), getY(), getZ(), stack));
		inventory.clear();
	}

	/**
	 * @return whether xp was successfully given
	 */
	protected boolean giveXpToOwner() {
		if (getOwner() instanceof PlayerEntity player) {
			player.addExperience(xpAmount);
			xpAmount = 0;
			return true;
		}
		return false;
	}

	protected void dropXp() {
		ExperienceOrbEntity.spawn((ServerWorld) world, getPos(), xpAmount);
	}

	@Override
	public void remove(RemovalReason removalReason) {
		if (miningAt != null) {
			// Clear block breaking progress when despawning
			world.setBlockBreakingInfo(getId(), miningAt, -1);
		}
		super.remove(removalReason);
	}

	/**
	 * @return whether a block was found
	 */
	protected boolean findNextMiningBlock() {
		Optional<BlockPos> candidate = scheduledMiningPositions.stream()
				.sorted((a, b) -> squaredDistanceTo(Vec3d.of(a)) < squaredDistanceTo(Vec3d.of(b)) ? -1 : 1)
				.filter(pos -> mineMaterial.equals(world.getBlockState(pos).getBlock())).findFirst();
		if (candidate.isEmpty()) return false;
		miningAt = candidate.get();
		return true;
	}

	@Override
	protected void initDataTracker() {
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.containsUuid("owner")) ownerUuid = nbt.getUuid("owner");
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

		if (nbt.contains("itemStack")) itemStack = ItemStack.fromNbt(nbt.getCompound("itemStack"));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putUuid("owner", ownerUuid);
		nbt.putInt("toolAge", toolAge);

		NbtList inventoryNbt = new NbtList();
		inventoryNbt.addAll(inventory.stream().map(stack -> stack.writeNbt(new NbtCompound())).toList());
		nbt.put("inventory", inventoryNbt);

		nbt.putInt("xpAmount", xpAmount);

		NbtList miningPositionsNbt = new NbtList();
		miningPositionsNbt.addAll(scheduledMiningPositions.stream().map(NbtHelper::fromBlockPos).toList());
		nbt.put("miningPositions", miningPositionsNbt);

		nbt.putString("miningMaterial", Registry.BLOCK.getId(mineMaterial).toString());
		nbt.putInt("miningProgress", miningTicks);
		if (miningAt != null) nbt.put("miningAt", NbtHelper.fromBlockPos(miningAt));

		if (itemStack != null) nbt.put("itemStack", itemStack.getNbt());
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