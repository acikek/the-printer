package com.acikek.theprinter.data;

import com.google.gson.JsonObject;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

import java.util.HashMap;
import java.util.Map;

public class PrinterRule {

	public static Map<Identifier, PrinterRule> RULES = new HashMap<>();

	public Ingredient input;
	public int override;
	public String modifier;
	public int size;

	public PrinterRule(Ingredient input, int override, String modifier, int size) {
		this.input = input;
		this.override = override;
		this.modifier = modifier;
		this.size = size;
	}

	public boolean validate() {
		if (override != -1 && modifier != null) {
			throw new IllegalStateException("'modifier' rule is mutually exclusive with 'override'");
		}
		if (override == -1 && modifier == null) {
			throw new IllegalStateException("rule must contain either 'override' or 'modifier'");
		}
		return true;
	}

	public static PrinterRule fromJson(JsonObject obj) {
		Ingredient input = Ingredient.fromJson(JsonHelper.getObject(obj, "input"));
		int override = Math.max(-1, JsonHelper.getInt(obj, "override", -1));
		String modifier = JsonHelper.getString(obj, "modifier", null);
		int size = Math.max(1, JsonHelper.getInt(obj, "size", 1));
		return new PrinterRule(input, override, modifier, size);
	}

	public static PrinterRule read(PacketByteBuf buf) {
		Ingredient input = Ingredient.fromPacket(buf);
		int override = buf.readInt();
		String modifier = buf.readNullable(PacketByteBuf::readString);
		int size = buf.readInt();
		return new PrinterRule(input, override, modifier, size);
	}

	public void write(PacketByteBuf buf) {
		input.write(buf);
		buf.writeInt(override);
		buf.writeNullable(modifier, PacketByteBuf::writeString);
		buf.writeInt(size);
	}
}
