package com.turtlearmymc.spirittools.entities;

import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

public class SpiritPickaxeEntity extends SpiritToolEntity {
	public static final String ENTITY_ID = "spirit_pickaxe";

	public SpiritPickaxeEntity(EntityType<? extends SpiritToolEntity> type, World world) {
		super(type, world);
	}
}
