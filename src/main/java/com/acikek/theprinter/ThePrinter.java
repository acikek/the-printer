package com.acikek.theprinter;

import com.acikek.theprinter.advancement.PrinterUsedCriterion;
import com.acikek.theprinter.block.PrinterBlock;
import com.acikek.theprinter.block.PrinterBlockEntity;
import com.acikek.theprinter.sound.ModSoundEvents;
import com.acikek.theprinter.world.PrinterRuleReloader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThePrinter implements ModInitializer {

	public static final String ID = "theprinter";

	public static Identifier id(String path) {
		return new Identifier(ID, path);
	}

	public static final Logger LOGGER = LoggerFactory.getLogger(ID);

	@Override
	public void onInitialize() {
		LOGGER.info("The Printer - Have your XP ready!");
		PrinterBlock.register();
		PrinterBlock.registerGameRule();
		PrinterBlockEntity.register();
		PrinterUsedCriterion.register();
		ModSoundEvents.register();
		PrinterRuleReloader.register();
		registerDatapack();
	}

	public static void registerDatapack() {
		FabricLoader.getInstance().getModContainer(ID).ifPresent(mod ->
				ResourceManagerHelper.registerBuiltinResourcePack(
						ThePrinter.id("default"), mod, "Default Printer Rules",
						ResourcePackActivationType.DEFAULT_ENABLED
				)
		);
	}
}
