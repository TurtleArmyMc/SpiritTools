package com.turtlearmymc.spirittools.client;

import com.turtlearmymc.spirittools.SpiritTools;
import com.turtlearmymc.spirittools.client.render.SpiritToolRenderer;
import com.turtlearmymc.spirittools.network.S2CSummonSpiritToolPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

@Environment(EnvType.CLIENT)
public class SpiritToolsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(S2CSummonSpiritToolPacket.ID, S2CSummonSpiritToolPacket::onPacket);
		EntityRendererRegistry.register(SpiritTools.SPIRIT_PICKAXE_ENTITY, SpiritToolRenderer::new);
	}
}