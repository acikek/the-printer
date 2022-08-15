package com.acikek.theprinter.sound;

import com.acikek.theprinter.ThePrinter;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ModSoundEvents {

	public static SoundEvent STARTUP;
	public static SoundEvent SHUTDOWN;
	public static SoundEvent PRINTING;

	public static SoundEvent register(String path) {
		Identifier id = ThePrinter.id(path);
		return Registry.register(Registry.SOUND_EVENT, id, new SoundEvent(id));
	}

	public static void register() {
		STARTUP = register("block.the_printer.startup");
		SHUTDOWN = register("block.the_printer.shutdown");
		PRINTING = register("block.the_printer.printing");
	}
}
