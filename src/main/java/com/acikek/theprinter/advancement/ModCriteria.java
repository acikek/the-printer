package com.acikek.theprinter.advancement;

import com.acikek.datacriteria.api.DataCriteriaAPI;
import com.acikek.theprinter.ThePrinter;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Rarity;

public class ModCriteria {

	public static void triggerPrinterUsed(ServerPlayerEntity player, int xp, int time, ItemStack stack, Rarity rarity) {
		DataCriteriaAPI.trigger(ThePrinter.id("printer_used"), true, player, xp, time, stack, rarity);
	}

	public static void register() {
		Registry.register(DataCriteriaAPI.getRegistry(), ThePrinter.id("rarity"), DataCriteriaAPI.createEnum(Rarity.class));
	}
}
