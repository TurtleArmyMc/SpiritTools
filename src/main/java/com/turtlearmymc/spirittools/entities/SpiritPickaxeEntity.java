package com.turtlearmymc.spirittools.entities;

import com.turtlearmymc.spirittools.SpiritTools;
import com.turtlearmymc.spirittools.items.SpiritToolItem;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.world.World;

public class SpiritPickaxeEntity extends SpiritToolEntity {
	public static final String ENTITY_ID = "spirit_pickaxe";

	public SpiritPickaxeEntity(EntityType<? extends SpiritToolEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected ToolMaterial getMaterial() {
		return ToolMaterials.DIAMOND;
	}

	@Override
	protected SpiritToolItem getItem() {
		return SpiritTools.SPIRIT_PICKAXE_ITEM;
	}
}
