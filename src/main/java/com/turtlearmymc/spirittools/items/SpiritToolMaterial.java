package com.turtlearmymc.spirittools.items;

import net.fabricmc.yarn.constants.MiningLevels;
import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.recipe.Ingredient;

public class SpiritToolMaterial implements ToolMaterial {
	public static final SpiritToolMaterial SPIRIT_TOOL_MATERIAL = new SpiritToolMaterial();

	private SpiritToolMaterial() {
	}

	@Override
	public int getDurability() {
		return ToolMaterials.DIAMOND.getDurability();
	}

	@Override
	public float getMiningSpeedMultiplier() {
		return ToolMaterials.DIAMOND.getMiningSpeedMultiplier();
	}

	@Override
	public float getAttackDamage() {
		return ToolMaterials.DIAMOND.getAttackDamage();
	}

	@Override
	public int getMiningLevel() {
		return MiningLevels.DIAMOND;
	}

	@Override
	public int getEnchantability() {
		return 12;
	}

	@Override
	public Ingredient getRepairIngredient() {
		return Ingredient.ofItems(Items.ENDER_PEARL);
	}
}
