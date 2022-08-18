package com.acikek.theprinter.world;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.data.PrinterRule;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.qsl.networking.api.PacketByteBufs;
import org.quiltmc.qsl.networking.api.PacketSender;
import org.quiltmc.qsl.networking.api.ServerPlayConnectionEvents;
import org.quiltmc.qsl.networking.api.ServerPlayNetworking;
import org.quiltmc.qsl.resource.loader.api.ResourceLoader;
import org.quiltmc.qsl.resource.loader.api.ResourceLoaderEvents;
import org.quiltmc.qsl.resource.loader.api.reloader.IdentifiableResourceReloader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PrinterRuleReloader extends JsonDataLoader implements IdentifiableResourceReloader, ResourceLoaderEvents.EndDataPackReload, ServerPlayConnectionEvents.Join {

	public static final Identifier ID = ThePrinter.id("printer_rules");

	public PrinterRuleReloader() {
		super(new Gson(), "printer_rules");
	}

	@Override
	public @NotNull Identifier getQuiltId() {
		return ID;
	}

	public static List<PacketByteBuf> getReloadBufs() {
		boolean start = true;
		List<PacketByteBuf> bufs = new ArrayList<>();
		for (Map.Entry<Identifier, PrinterRule> entry : PrinterRule.RULES.entrySet()) {
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeBoolean(start);
			if (start) {
				start = false;
			}
			buf.writeIdentifier(entry.getKey());
			entry.getValue().write(buf);
			bufs.add(buf);
		}
		return bufs;
	}

	@Override
	protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
		PrinterRule.RULES.clear();
		int successful = 0;
		for (Map.Entry<Identifier, JsonElement> file : prepared.entrySet()) {
			JsonObject obj = file.getValue().getAsJsonObject();
			try {
				PrinterRule rule = PrinterRule.fromJson(obj);
				if (rule.validate()) {
					PrinterRule.RULES.put(file.getKey(), rule);
					successful++;
				}
			}
			catch (Exception e) {
				ThePrinter.LOGGER.error("Error in printer rule '" + file.getKey() + "': ", e);
			}
		}
		ThePrinter.LOGGER.info("Loaded " + successful + " printer rules");
	}

	@Override
	public void onEndDataPackReload(@Nullable MinecraftServer server, ResourceManager resourceManager, @Nullable Throwable error) {
		if (server == null) {
			return;
		}
		for (PacketByteBuf buf : getReloadBufs()) {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				ServerPlayNetworking.send(player, ID, buf);
			}
		}
	}

	@Override
	public void onPlayReady(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
		for (PacketByteBuf buf : getReloadBufs()) {
			ServerPlayNetworking.send(handler.player, ID, buf);
		}
	}

	public static void register() {
		PrinterRuleReloader reloader = new PrinterRuleReloader();
		ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(reloader);
		ResourceLoaderEvents.END_DATA_PACK_RELOAD.register(reloader);
		ServerPlayConnectionEvents.JOIN.register(reloader);
	}
}
