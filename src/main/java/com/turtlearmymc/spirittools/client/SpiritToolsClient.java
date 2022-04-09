package com.turtlearmymc.spirittools.client;

import com.turtlearmymc.spirittools.SpiritTools;
import com.turtlearmymc.spirittools.client.render.SpiritToolRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

@Environment(EnvType.CLIENT)
public class SpiritToolsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(SpiritTools.SPIRIT_PICKAXE_ENTITY, SpiritToolRenderer::new);
	}
}