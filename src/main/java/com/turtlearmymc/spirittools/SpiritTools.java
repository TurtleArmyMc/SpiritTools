package com.turtlearmymc.spirittools;

import com.turtlearmymc.spirittools.entities.SpiritPickaxeEntity;
import com.turtlearmymc.spirittools.items.SpiritPickaxeItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SpiritTools implements ModInitializer {
	public static final String MOD_ID = "spirittools";
	public static final String MOD_NAME = "Spirit Tools";
	public static final EntityType<SpiritPickaxeEntity> SPIRIT_PICKAXE_ENTITY =
			Registry.register(Registry.ENTITY_TYPE, new Identifier(MOD_ID, SpiritPickaxeEntity.ENTITY_ID),
					FabricEntityTypeBuilder.create(SpawnGroup.MISC, SpiritPickaxeEntity::new)
							.dimensions(EntityDimensions.fixed(0.75f, 0.75f)).trackRangeBlocks(4).trackedUpdateRate(10)
							.build()
			);
	public static final Item SPIRIT_PICKAXE_ITEM = new SpiritPickaxeItem(new Item.Settings().group(ItemGroup.MISC));
	public static Logger LOGGER = LogManager.getLogger();

	public static void log(Level level, String message) {
		LOGGER.log(level, "[" + MOD_NAME + "] " + message);
	}

	@Override
	public void onInitialize() {
		log(Level.INFO, "Initializing");

		Registry.register(Registry.ITEM, new Identifier(MOD_ID, SpiritPickaxeItem.ITEM_ID), SPIRIT_PICKAXE_ITEM);
	}
}
