package com.acikek.theprinter.client;

import com.acikek.theprinter.block.PrinterBlock;
import com.acikek.theprinter.client.render.PrinterBlockEntityRenderer;
import net.minecraft.client.render.RenderLayer;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer;
import org.quiltmc.qsl.block.extensions.api.client.BlockRenderLayerMap;

public class ThePrinterClient implements ClientModInitializer {

	@Override
	public void onInitializeClient(ModContainer mod) {
		BlockRenderLayerMap.put(RenderLayer.getCutout(), PrinterBlock.INSTANCE);
		PrinterBlockEntityRenderer.register();
	}
}
