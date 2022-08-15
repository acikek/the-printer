package com.acikek.theprinter;

import com.acikek.theprinter.block.PrinterBlock;
import com.acikek.theprinter.block.PrinterBlockEntity;
import com.acikek.theprinter.sound.ModSoundEvents;
import net.minecraft.util.Identifier;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThePrinter implements ModInitializer {

	public static final String ID = "theprinter";

	public static Identifier id(String path) {
		return new Identifier(ID, path);
	}

	public static final Logger LOGGER = LoggerFactory.getLogger(ID);

	@Override
	public void onInitialize(ModContainer mod) {
		LOGGER.info("Hello Quilt world from {}!", mod.metadata().name());
		PrinterBlock.register();
		PrinterBlockEntity.register();
		ModSoundEvents.register();
	}
}
