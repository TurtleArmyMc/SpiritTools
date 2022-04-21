package com.turtlearmymc.spirittools.entities;

import com.turtlearmymc.spirittools.items.SpiritToolItem;
import com.turtlearmymc.spirittools.network.S2CSummonSpiritToolPacket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class SpiritToolEntity extends Entity {
	public static final int SUMMON_RANGE = 20;
	protected static final int DESPAWN_AGE = 200;

	protected int toolAge;
	protected LivingEntity owner;
	protected UUID ownerUuid;

	protected Set<BlockPos> scheduledMiningPositions;
	protected Block mineMaterial;
	protected int miningTicks;
	protected BlockPos miningAt;
	protected int prevBreakStage;

	public SpiritToolEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	protected abstract ToolMaterial getMaterial();

	protected abstract SpiritToolItem getItem();

	public LivingEntity getOwner() {
		return owner;
	}

	public void setOwner(LivingEntity owner) {
		this.owner = owner;
		ownerUuid = owner != null ? owner.getUuid() : null;
	}

	public UUID getOwnerUUID() {
		return ownerUuid;
	}

	public void scheduleToMine(Block mineMaterial, Set<BlockPos> miningPositions) {
		this.mineMaterial = mineMaterial;
		scheduledMiningPositions = miningPositions;
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
		if (ownerUuid == null) {
			discard();
			return;
		}
		if (world instanceof ServerWorld) {
			owner = (LivingEntity) ((ServerWorld) world).getEntity(ownerUuid);
			if (owner == null) {
				discard();
				return;
			}
		}
		if (++toolAge >= DESPAWN_AGE || !holderWithinRange()) {
			discard();
			return;
		}
		if (mineMaterial == null) return;
		if (miningAt == null) {
			if (!findNextMiningBlock()) {
				discard();
				return;
			}
			lookAt(miningAt);
		}
		++miningTicks;
		BlockState stateAt = world.getBlockState(miningAt);
		float breakProgress = calcBlockBreakingDelta(stateAt) * (miningTicks + 1f);
		int breakStage = (int) (breakProgress * 10f);
		if (breakProgress >= 1) {
			world.setBlockBreakingInfo(getId(), miningAt, -1);
			world.breakBlock(miningAt, true);
			miningAt = null;
			miningTicks = 0;
			prevBreakStage = 0;
		} else if (breakStage != prevBreakStage) {
			world.setBlockBreakingInfo(getId(), miningAt, breakStage);
			prevBreakStage = breakStage;
		}
	}

	protected boolean holderWithinRange() {
		if (owner == null || squaredDistanceTo(owner) >= SUMMON_RANGE * SUMMON_RANGE) return false;
		if (owner instanceof PlayerEntity player) {
			return player.getInventory().contains(new ItemStack(getItem()));
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
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putUuid("owner", ownerUuid);
		nbt.putInt("toolAge", toolAge);
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