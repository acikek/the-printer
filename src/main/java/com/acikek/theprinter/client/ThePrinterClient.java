package com.acikek.theprinter.client;

import com.acikek.theprinter.block.PrinterBlock;
import com.acikek.theprinter.client.render.PrinterBlockEntityRenderer;
import com.acikek.theprinter.data.PrinterRule;
import com.acikek.theprinter.world.PrinterRuleReloader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.network.PacketByteBuf;

public class ThePrinterClient implements ClientModInitializer {

	public static int renderTicks;
	public static boolean printerEnabled;

	@Override
	public void onInitializeClient() {
		BlockRenderLayerMap.INSTANCE.putBlock(PrinterBlock.INSTANCE, RenderLayer.getCutout());
		PrinterBlockEntityRenderer.register();
		ClientTickEvents.START_WORLD_TICK.register(world -> tick());
		ClientPlayNetworking.registerGlobalReceiver(PrinterRuleReloader.ID, ThePrinterClient::reloadRule);
		ClientPlayNetworking.registerGlobalReceiver(
				PrinterBlock.GAMERULE_CHANGED,
				(client, handler, buf, responseSender) -> printerEnabled = buf.readBoolean()
		);
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
