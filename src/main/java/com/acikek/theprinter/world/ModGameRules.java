package com.acikek.theprinter.world;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.client.ThePrinterClient;
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
import net.minecraft.world.World;

public class ModGameRules implements ServerPlayConnectionEvents.Join {

	public static GameRules.Key<GameRules.BooleanRule> PRINTER_ENABLED;
	public static GameRules.Key<GameRules.BooleanRule> XP_REQUIRED;

	public static final Identifier GAMERULES_CHANGED = ThePrinter.id("gamerules_changed");

	public static boolean isPrinterEnabled(World world) {
		return world.isClient()
				? ThePrinterClient.printerEnabled
				: world.getGameRules().getBoolean(ModGameRules.PRINTER_ENABLED);
	}

	public static boolean isXPRequired(World world) {
		return world.isClient()
				? ThePrinterClient.xpRequired
				: world.getGameRules().getBoolean(ModGameRules.XP_REQUIRED);
	}

	public static PacketByteBuf getReloadBuf(GameRules gameRules) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(gameRules.getBoolean(PRINTER_ENABLED));
		buf.writeBoolean(gameRules.getBoolean(XP_REQUIRED));
		return buf;
	}

	public static void syncGameRule(MinecraftServer server) {
		PacketByteBuf buf = getReloadBuf(server.getGameRules());
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(player, GAMERULES_CHANGED, buf);
		}
	}

	@Override
	public void onPlayReady(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
		PacketByteBuf buf = getReloadBuf(server.getGameRules());
		ServerPlayNetworking.send(handler.player, GAMERULES_CHANGED, buf);
	}

	public static void register() {
		PRINTER_ENABLED = GameRuleRegistry.register(
				"enablePrinter",
				GameRules.Category.MISC,
				GameRuleFactory.createBooleanRule(true, (server, r) -> syncGameRule(server))
		);
		XP_REQUIRED = GameRuleRegistry.register(
				"requirePrinterXP",
				GameRules.Category.MISC,
				GameRuleFactory.createBooleanRule(true, (server, r) -> syncGameRule(server))
		);
		ServerPlayConnectionEvents.JOIN.register(new ModGameRules());
	}
}
