package com.acikek.theprinter.sound;

import com.acikek.theprinter.ThePrinter;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSoundEvents {

	public static SoundEvent STARTUP;
	public static SoundEvent SHUTDOWN;
	public static SoundEvent PRINTING;

	public static SoundEvent register(String path) {
		Identifier id = ThePrinter.id(path);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void register() {
		STARTUP = register("block.the_printer.startup");
		SHUTDOWN = register("block.the_printer.shutdown");
		PRINTING = register("block.the_printer.printing");
	}
}
