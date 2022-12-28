package com.turtlearmymc.spirittools;

import com.turtlearmymc.spirittools.entities.SpiritPickaxeEntity;
import com.turtlearmymc.spirittools.items.SpiritPickaxeItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SpiritTools implements ModInitializer {
	public static final String MOD_ID = "spirittools";
	public static final String MOD_NAME = "Spirit Tools";
	// Register items
	public static final SpiritPickaxeItem SPIRIT_PICKAXE_ITEM =
			new SpiritPickaxeItem(new Item.Settings());
	// Register entities
	public static final EntityType<SpiritPickaxeEntity> SPIRIT_PICKAXE_ENTITY =
			Registry.register(Registries.ENTITY_TYPE, new Identifier(MOD_ID, SpiritPickaxeEntity.ENTITY_ID),
					FabricEntityTypeBuilder.create(SpawnGroup.MISC, SpiritPickaxeEntity::new).trackRangeChunks(8)
							.dimensions(EntityDimensions.fixed(0.75f, 0.75f)).build()
			);
	public static Logger LOGGER = LogManager.getLogger();

	public static void log(Level level, String message) {
		LOGGER.log(level, "[" + MOD_NAME + "] " + message);
	}

	@Override
	public void onInitialize() {
		log(Level.INFO, "Initializing");

		Registry.register(Registries.ITEM, new Identifier(MOD_ID, SpiritPickaxeItem.ITEM_ID), SPIRIT_PICKAXE_ITEM);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(SPIRIT_PICKAXE_ITEM));
	}
}
