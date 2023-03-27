package com.acikek.theprinter.client.render;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.block.PrinterBlock;
import com.acikek.theprinter.block.PrinterBlockEntity;
import com.acikek.theprinter.client.ThePrinterClient;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class PrinterBlockEntityRenderer implements BlockEntityRenderer<PrinterBlockEntity> {

	public static final Identifier PROGRESS_BAR = ThePrinter.id("textures/progress_bar.png");

	public ItemRenderer itemRenderer;

	public PrinterBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
		itemRenderer = ctx.getItemRenderer();
	}

	public static int getLight(World world, BlockPos pos, int fallback) {
		return world != null ? WorldRenderer.getLightmapCoordinates(world, pos) : fallback;
	}

	public void positionOverlay(MatrixStack matrices, Direction facing) {
		Vector3f vec = facing.getUnitVector();
		matrices.translate(vec.x / 2.0f + 0.5f, vec.y / 2.0f + 0.5f, vec.z / 2.0f + 0.5f);
		matrices.multiply(facing.getRotationQuaternion());
		matrices.multiply(Axis.X_POSITIVE.rotationDegrees(-90));
		matrices.translate(0, 0, 0.005);
	}

	public void renderProgressBar(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int lightFront, float progress) {
		matrices.push();
		matrices.translate(0.5, -1.25, 0.0);
		matrices.multiply(Axis.Y_POSITIVE.rotationDegrees(180));
		VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(PROGRESS_BAR));
		Matrix4f matrix = matrices.peek().getModel();
		Matrix3f normal = matrices.peek().getNormal();
		float progressX = 0.8125f - 0.6250f * progress;
		buffer.vertex(matrix, progressX, 0.875f, 0).color(0xFF_FFFFFF).uv(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(lightFront).normal(normal, 0, 0, 1).next();
		buffer.vertex(matrix, progressX, 0.9375f, 0).color(0xFF_FFFFFF).uv(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(lightFront).normal(normal, 0, 0, 1).next();
		buffer.vertex(matrix, 0.8125f, 0.9375f, 0).color(0xFF_FFFFFF).uv(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(lightFront).normal(normal, 0, 0, 1).next();
		buffer.vertex(matrix, 0.8125f, 0.875f, 0).color(0xFF_FFFFFF).uv(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(lightFront).normal(normal, 0, 0, 1).next();
		matrices.pop();
	}

	public void renderScreenStack(ItemStack stack, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int lightFront, int overlay, int seed) {
		matrices.push();
		matrices.translate(0.0, -0.06, 0.0);
		matrices.scale(0.4f, 0.4f, 1);
		matrices.multiplyMatrix(new Matrix4f().scale(1, 1, 0.01f));
		itemRenderer.renderItem(stack, ModelTransformation.Mode.GUI, lightFront, overlay, matrices, vertexConsumers, seed);
		matrices.pop();
	}

	public static float getAngle(float tickDelta, int offset) {
		if (ThePrinterClient.renderTicks == offset) {
			return 0.0f;
		}
		return MathHelper.TAU * ((ThePrinterClient.renderTicks - offset % 120 + tickDelta) / 120.0f);
	}

	public void renderPrintingStack(ItemStack stack, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int lightAbove, int overlay, int seed, float progress, boolean finished, int offset) {
		matrices.push();
		matrices.translate(0.5, 1.4, 0.5);
		if (finished) {
			matrices.translate(0.0, MathHelper.sin(getAngle(tickDelta, offset)) * 0.1f, 0.0);
		}
		matrices.multiply(Axis.Y_POSITIVE.rotation(getAngle(tickDelta, 0)));
		matrices.scale(1.3f, 1.3f, 1.3f);
		// Temporarily disable translucent vertex buffers until Sodium releases its API
		VertexConsumerProvider vcp = !ThePrinter.IS_SODIUM && !finished && vertexConsumers instanceof VertexConsumerProvider.Immediate immediate
				? new TranslucentVertexConsumerProvider(immediate, progress)
				: vertexConsumers;
		itemRenderer.renderItem(stack, ModelTransformation.Mode.GROUND, lightAbove, overlay, matrices, vcp, seed);
		matrices.pop();
	}

	@Override
	public void render(PrinterBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		BlockState state = entity.getCachedState();
		if (!state.get(PrinterBlock.ON)) {
			return;
		}
		matrices.push();
		Direction facing = state.get(PrinterBlock.FACING);
		int lightFront = getLight(entity.getWorld(), entity.getPos().offset(facing), light);
		int seed = (int) entity.getPos().asLong();
		// Render screen overlay
		matrices.push();
		positionOverlay(matrices, facing);
		if (entity.xp > 0) {
			renderProgressBar(matrices, vertexConsumers, lightFront, (float) entity.xp / entity.requiredXP);
		}
		if (!entity.getItems().get(0).isEmpty()) {
			renderScreenStack(entity.getItems().get(0), matrices, vertexConsumers, lightFront, overlay, seed);
		}
		matrices.pop();
		// Adjust printing end tick offsets for smoother transitions
		boolean finished = state.get(PrinterBlock.FINISHED);
		if (finished && entity.endOffset == -1) {
			entity.endOffset = ThePrinterClient.renderTicks;
		}
		else if (!finished && entity.endOffset != -1) {
			entity.endOffset = -1;
		}
		// Render top printing stack
		if ((state.get(PrinterBlock.PRINTING) || finished) && !entity.getItems().get(1).isEmpty()) {
			int lightAbove = getLight(entity.getWorld(), entity.getPos().up(), light);
			float progress = finished ? 1.0f : ((float) entity.progress / entity.requiredTicks);
			renderPrintingStack(entity.getItems().get(1), tickDelta, matrices, vertexConsumers, lightAbove, overlay, seed, progress, finished, entity.endOffset);
		}
		matrices.pop();
	}

	@Override
	public boolean rendersOutsideBoundingBox(PrinterBlockEntity blockEntity) {
		return false;
	}

	public static void register() {
		BlockEntityRendererFactories.register(PrinterBlockEntity.BLOCK_ENTITY_TYPE, PrinterBlockEntityRenderer::new);
	}
}
