package com.acikek.theprinter.client;

import com.acikek.theprinter.block.PrinterBlock;
import com.acikek.theprinter.client.render.PrinterBlockEntityRenderer;
import com.acikek.theprinter.data.PrinterRule;
import com.acikek.theprinter.world.PrinterRuleReloader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.network.PacketByteBuf;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer;
import org.quiltmc.qsl.block.extensions.api.client.BlockRenderLayerMap;
import org.quiltmc.qsl.lifecycle.api.client.event.ClientWorldTickEvents;
import org.quiltmc.qsl.networking.api.PacketSender;
import org.quiltmc.qsl.networking.api.client.ClientPlayNetworking;

public class ThePrinterClient implements ClientModInitializer {

	public static int renderTicks;

	@Override
	public void onInitializeClient(ModContainer mod) {
		BlockRenderLayerMap.put(RenderLayer.getCutout(), PrinterBlock.INSTANCE);
		PrinterBlockEntityRenderer.register();
		ClientWorldTickEvents.START.register((client, world) -> tick());
		ClientPlayNetworking.registerGlobalReceiver(PrinterRuleReloader.ID, ThePrinterClient::reloadRule);
	}

	public static void tick() {
		renderTicks++;
		if (renderTicks >= 360) {
			renderTicks = 0;
		}
	}

	public static void reloadRule(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
		if (client.isInSingleplayer()) {
			return;
		}
		if (buf.readBoolean()) {
			PrinterRule.RULES.clear();
		}
		PrinterRule.RULES.put(buf.readIdentifier(), PrinterRule.read(buf));
	}
}
