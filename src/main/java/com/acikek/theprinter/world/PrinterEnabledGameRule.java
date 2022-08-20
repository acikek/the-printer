package com.acikek.theprinter.world;

import com.acikek.theprinter.ThePrinter;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

public class PrinterEnabledGameRule implements ServerPlayConnectionEvents.Join {

	public static GameRules.Key<GameRules.BooleanRule> INSTANCE;
	public static final Identifier GAMERULE_CHANGED = ThePrinter.id("printer_enabled_gamerule");

	public static PacketByteBuf getReloadBuf(GameRules.BooleanRule rule) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(rule.get());
		return buf;
	}

	public static void syncGameRule(MinecraftServer server, GameRules.BooleanRule rule) {
		PacketByteBuf buf = getReloadBuf(rule);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(player, GAMERULE_CHANGED, buf);
		}
	}

	@Override
	public void onPlayReady(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
		PacketByteBuf buf = getReloadBuf(server.getGameRules().get(INSTANCE));
		ServerPlayNetworking.send(handler.player, GAMERULE_CHANGED, buf);
	}

	public static void register() {
		INSTANCE = GameRuleRegistry.register(
				"enablePrinter",
				GameRules.Category.MISC,
				GameRuleFactory.createBooleanRule(true, PrinterEnabledGameRule::syncGameRule)
		);
		ServerPlayConnectionEvents.JOIN.register(new PrinterEnabledGameRule());
	}
}
