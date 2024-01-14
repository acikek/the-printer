package com.acikek.theprinter.data;

import com.acikek.theprinter.ThePrinter;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class ModTags {
	public static TagKey<Item> LOSES_NBT;

	public static TagKey<Item> register(String path) {
		Identifier id = ThePrinter.id(path);
		return TagKey.of(Registries.ITEM.getKey(), id);
	}

	public static void register() {
		LOSES_NBT = register("loses_nbt");
	}
}
