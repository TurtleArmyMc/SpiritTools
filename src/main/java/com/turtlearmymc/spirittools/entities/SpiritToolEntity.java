package com.turtlearmymc.spirittools.entities;

import com.turtlearmymc.spirittools.network.S2CSummonSpiritToolPacket;
import net.fabricmc.yarn.constants.MiningLevels;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ToolMaterial;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

public abstract class SpiritToolEntity extends Entity {
	protected static final int DESPAWN_AGE = 200;
	protected static final int MINING_TICKS = 20;
	protected static final double SEARCH_BLOCK_RANGE = 5;

	protected final TagKey<Block> effectiveBlocks;
	protected final ToolMaterial material;

	protected int toolAge;
	protected LivingEntity owner;
	protected UUID ownerUuid;

	protected HashSet<BlockPos> scheduledMiningPositions;
	protected Block mineMaterial;
	protected int miningTicks;
	protected BlockPos miningAt;

	public SpiritToolEntity(
			EntityType<?> type, World world, TagKey<Block> effectiveBlocks, ToolMaterial material
	) {
		super(type, world);

		this.effectiveBlocks = effectiveBlocks;
		this.material = material;
		scheduledMiningPositions = new HashSet<>();
	}

	public void setOwner(LivingEntity owner) {
		this.owner = owner;
		ownerUuid = owner != null ? owner.getUuid() : null;
	}

	public void scheduleToMine(BlockPos searchFrom) {
		if (world.isClient) {
			System.out.println("OH NO");
		} else {
			BlockState state = world.getBlockState(searchFrom);
			if (isSuitableFor(state)) {
				mineMaterial = state.getBlock();
				scheduleToMine(searchFrom, searchFrom);
			}
		}
	}

	protected void scheduleToMine(BlockPos center, BlockPos pos) {
		if (scheduledMiningPositions.contains(pos)) return;
		if (!center.isWithinDistance(pos, SEARCH_BLOCK_RANGE)) return;
		if (!world.getBlockState(pos).getBlock().equals(mineMaterial)) return;
		scheduledMiningPositions.add(pos);
		scheduleToMine(center, pos.up());
		scheduleToMine(center, pos.down());
		scheduleToMine(center, pos.north());
		scheduleToMine(center, pos.south());
		scheduleToMine(center, pos.east());
		scheduleToMine(center, pos.west());
	}

	public boolean isSuitableFor(BlockState state) {
		int i = material.getMiningLevel();
		if (i < MiningLevels.DIAMOND && state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) {
			return false;
		}
		if (i < MiningLevels.IRON && state.isIn(BlockTags.NEEDS_IRON_TOOL)) {
			return false;
		}
		if (i < MiningLevels.STONE && state.isIn(BlockTags.NEEDS_STONE_TOOL)) {
			return false;
		}
		return state.isIn(effectiveBlocks);
	}

	@Override
	public void tick() {
		super.tick();
		if (world.isClient()) {
			clientTick();
		} else {
			serverTick();
		}
	}

	protected void clientTick() {
	}

	protected void serverTick() {
		if (ownerUuid != null && world instanceof ServerWorld) {
			owner = (LivingEntity) ((ServerWorld) world).getEntity(ownerUuid);
		}
		if (++toolAge >= DESPAWN_AGE) {
			discard();
		}
		if (mineMaterial == null) return;
		if (miningAt == null) {
			if (!findNextMiningBlock()) {
				discard();
				return;
			}
			lookAt(miningAt);
		}
		if (++miningTicks >= MINING_TICKS) {
			world.breakBlock(miningAt, true);
			miningAt = null;
			miningTicks = 0;
		}
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