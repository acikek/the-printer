package com.acikek.theprinter.client.render;

import com.acikek.theprinter.block.PrinterBlock;
import com.acikek.theprinter.block.PrinterBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;

public class PrinterBlockEntityRenderer implements BlockEntityRenderer<PrinterBlockEntity> {

	public ItemRenderer itemRenderer;

	public PrinterBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
		itemRenderer = ctx.getItemRenderer();
	}

	public void renderScreenStack(ItemStack stack, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int lightAbove, int overlay, int seed) {
		matrices.push();
		matrices.scale(0.75f, 0.75f, 1);
		matrices.multiplyMatrix(Matrix4f.scale(1, 1, 0.01f));
		itemRenderer.renderItem(stack, ModelTransformation.Mode.GUI, lightAbove, overlay, matrices, vertexConsumers, seed);
		matrices.pop();
	}

	@Override
	public void render(PrinterBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (entity.isEmpty()) {
			return;
		}
		matrices.push();
		int lightAbove = entity.getWorld() != null ? WorldRenderer.getLightmapCoordinates(entity.getWorld(), entity.getPos().up()) : light;
		BlockState state = entity.getCachedState();
		Direction facing = state.get(PrinterBlock.FACING);
		Vec3f vec = facing.getUnitVector();
		matrices.translate(vec.getX() / 2.0f + 0.5f, vec.getY() / 2.0f + 0.5, vec.getZ() / 2.0f + 0.5);
		matrices.multiply(facing.getRotationQuaternion());
		matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-90));
		matrices.translate(0, 0, 0.01);
		if (state.get(PrinterBlock.ON) && !entity.getStack(0).isEmpty()) {
			renderScreenStack(entity.getStack(0), matrices, vertexConsumers, lightAbove, overlay, (int) entity.getPos().asLong());
		}
		matrices.pop();
	}

	@Override
	public boolean rendersOutsideBoundingBox(PrinterBlockEntity blockEntity) {
		return true;
	}

	public static void register() {
		BlockEntityRendererRegistry.register(PrinterBlockEntity.BLOCK_ENTITY_TYPE, PrinterBlockEntityRenderer::new);
	}
}
