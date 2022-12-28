package com.turtlearmymc.spirittools.items;

import com.turtlearmymc.spirittools.SpiritTools;
import com.turtlearmymc.spirittools.entities.SpiritPickaxeEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.tag.BlockTags;

public class SpiritPickaxeItem extends SpiritToolItem<SpiritPickaxeEntity> {
	public static final String ITEM_ID = "spirit_pickaxe";

	public SpiritPickaxeItem(Settings settings) {
		super(1, -2.8f, BlockTags.PICKAXE_MINEABLE, settings);
	}

	@Override
	protected EntityType<SpiritPickaxeEntity> getToolEntityType() {
		return SpiritTools.SPIRIT_PICKAXE_ENTITY;
	}
}
