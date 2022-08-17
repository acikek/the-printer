package com.acikek.theprinter.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;

public class TranslucentVertexConsumerProvider implements VertexConsumerProvider {

	public VertexConsumerProvider.Immediate immediate;
	public float progress;

	public TranslucentVertexConsumerProvider(Immediate immediate, float progress) {
		this.immediate = immediate;
		this.progress = progress;
	}

	@Override
	public VertexConsumer getBuffer(RenderLayer renderLayer) {
		return new TranslucentVertexConsumer(immediate.getBuffer(TexturedRenderLayers.getItemEntityTranslucentCull()), progress);
	}

	public static class TranslucentVertexConsumer implements VertexConsumer {

		public VertexConsumer delegate;
		public float progress;

		public TranslucentVertexConsumer(VertexConsumer delegate, float progress) {
			this.delegate = delegate;
			this.progress = progress;
		}

		public int getProgress(int value) {
			return Math.max(1, (int) (value * progress));
		}

		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			return delegate.vertex(x, y, z);
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			return delegate.color(getProgress(red), getProgress(green), getProgress(blue), getProgress(255));
		}

		@Override
		public VertexConsumer uv(float u, float v) {
			return delegate.uv(u, v);
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			return delegate.overlay(u, v);
		}

		@Override
		public VertexConsumer light(int u, int v) {
			return delegate.light(u, v);
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			return delegate.normal(x, y, z);
		}

		@Override
		public void next() {
			delegate.next();
		}

		@Override
		public void fixColor(int red, int green, int blue, int alpha) {
			delegate.fixColor(red, green, blue, alpha);
		}

		@Override
		public void unfixColor() {
			delegate.unfixColor();
		}
	}
}
