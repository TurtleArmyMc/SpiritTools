package com.turtlearmymc.spirittools.client.render;

import com.turtlearmymc.spirittools.SpiritTools;
import com.turtlearmymc.spirittools.entities.SpiritToolEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class SpiritToolRenderer extends EntityRenderer<SpiritToolEntity> {
	ItemStack item = new ItemStack(Items.DIAMOND_PICKAXE);

	public SpiritToolRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(SpiritToolEntity entity) {
		return new Identifier(SpiritTools.MOD_ID, "textures/entity/spirit_pickaxe/spirit_pickaxe.png");
	}

	@Override
	public void render(
			SpiritToolEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light
	) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw() + 90));
		matrices.translate(0, 0.5, 0);
		MinecraftClient.getInstance().getItemRenderer()
				.renderItem(item, ModelTransformation.Mode.FIXED, light, OverlayTexture.DEFAULT_UV, matrices,
						vertexConsumers, entity.getId()
				);
		matrices.pop();
	}
}