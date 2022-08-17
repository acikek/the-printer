package com.acikek.theprinter.client;

import com.acikek.theprinter.block.PrinterBlock;
import com.acikek.theprinter.client.render.PrinterBlockEntityRenderer;
import net.minecraft.client.render.RenderLayer;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer;
import org.quiltmc.qsl.block.extensions.api.client.BlockRenderLayerMap;
import org.quiltmc.qsl.lifecycle.api.client.event.ClientWorldTickEvents;

public class ThePrinterClient implements ClientModInitializer {

	public static int renderTicks;

	@Override
	public void onInitializeClient(ModContainer mod) {
		BlockRenderLayerMap.put(RenderLayer.getCutout(), PrinterBlock.INSTANCE);
		PrinterBlockEntityRenderer.register();
		ClientWorldTickEvents.START.register((client, world) -> tick());
	}

	public static void tick() {
		renderTicks++;
		if (renderTicks >= 360) {
			renderTicks = 0;
		}
	}
}
