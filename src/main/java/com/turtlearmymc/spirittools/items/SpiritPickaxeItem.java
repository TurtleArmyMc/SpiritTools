package com.turtlearmymc.spirittools.items;

import com.turtlearmymc.spirittools.SpiritTools;
import com.turtlearmymc.spirittools.entities.SpiritToolEntity;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.TagKey;

public class SpiritPickaxeItem extends SpiritToolItem {
	public static final String ITEM_ID = "spirit_pickaxe";

	public SpiritPickaxeItem(Settings settings) {
		super(settings);
	}

	@Override
	protected ToolMaterial getMaterial() {
		return ToolMaterials.DIAMOND;
	}

	@Override
	protected TagKey<Block> getEffectiveBlocks() {
		return BlockTags.PICKAXE_MINEABLE;
	}

	@Override
	protected EntityType<? extends SpiritToolEntity> getToolEntityType() {
		return SpiritTools.SPIRIT_PICKAXE_ENTITY;
	}
}
