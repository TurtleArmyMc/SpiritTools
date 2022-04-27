package com.turtlearmymc.spirittools.entities;

import com.turtlearmymc.spirittools.SpiritTools;
import com.turtlearmymc.spirittools.items.SpiritToolItem;
import com.turtlearmymc.spirittools.items.SpiritToolMaterial;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ToolMaterial;
import net.minecraft.world.World;

public class SpiritPickaxeEntity extends SpiritToolEntity {
	public static final String ENTITY_ID = "spirit_pickaxe";

	public SpiritPickaxeEntity(EntityType<? extends SpiritToolEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected ToolMaterial getMaterial() {
		return SpiritToolMaterial.SPIRIT_TOOL_MATERIAL;
	}

	@Override
	protected SpiritToolItem getItem() {
		return SpiritTools.SPIRIT_PICKAXE_ITEM;
	}
}
