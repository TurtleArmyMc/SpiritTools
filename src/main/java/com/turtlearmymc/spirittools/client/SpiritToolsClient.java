package com.turtlearmymc.spirittools.client;

import com.turtlearmymc.spirittools.SpiritTools;
import com.turtlearmymc.spirittools.client.render.SpiritToolRenderer;
import com.turtlearmymc.spirittools.entities.SpiritPickaxeEntity;
import com.turtlearmymc.spirittools.network.S2CSummonSpiritToolPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class SpiritToolsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(S2CSummonSpiritToolPacket.ID, S2CSummonSpiritToolPacket::onPacket);
		EntityRendererRegistry.register(SpiritTools.SPIRIT_PICKAXE_ENTITY, SpiritToolRenderer::new);

		FabricModelPredicateProviderRegistry.register(
				SpiritTools.SPIRIT_PICKAXE_ITEM, new Identifier("summoned"), (itemStack, clientWorld, entity, seed) -> {
					Entity holder = entity != null ? entity : itemStack.getHolder();
					if (holder == null) return 0;

					if (clientWorld == null) {
						if (holder.world instanceof ClientWorld world) {
							clientWorld = world;
						} else {
							return 0;
						}
					}

					double expandBy = Math.sqrt(Math.pow(SpiritPickaxeEntity.SUMMON_RANGE, 2) * 2);
					return clientWorld.getEntitiesByType(SpiritTools.SPIRIT_PICKAXE_ENTITY,
							holder.getBoundingBox().expand(expandBy),
							(spiritPickaxe) -> holder.equals(spiritPickaxe.getOwner())
					).isEmpty() ? 0 : 1;
				});
	}
}