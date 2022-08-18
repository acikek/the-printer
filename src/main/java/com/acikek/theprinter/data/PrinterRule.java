package com.acikek.theprinter.data;

import com.google.gson.JsonObject;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PrinterRule {

	public static Map<Identifier, PrinterRule> RULES = new HashMap<>();

	public Ingredient input;
	public Optional<Integer> override;
	public Optional<String> modifier;
	public Optional<Integer> size;

	public PrinterRule(Ingredient input, Optional<Integer> override, Optional<String> modifier, Optional<Integer> size) {
		this.input = input;
		this.override = override;
		this.modifier = modifier;
		this.size = size;
	}

	public boolean validate() {
		if (override.isPresent() && modifier.isPresent()) {
			throw new IllegalStateException("'modifier' rule is mutually exclusive with 'override'");
		}
		if (override.isEmpty() && modifier.isEmpty() && size.isEmpty()) {
			throw new IllegalStateException("rule must contain either 'override', 'modifier', or 'size'");
		}
		return true;
	}

	public static PrinterRule fromJson(JsonObject obj) {
		Ingredient input = Ingredient.fromJson(JsonHelper.getObject(obj, "input"));
		int override = JsonHelper.getInt(obj, "override", -1);
		String modifier = JsonHelper.getString(obj, "modifier", null);
		int size = JsonHelper.getInt(obj, "size", -1);
		return new PrinterRule(
				input,
				override == -1 ? Optional.empty() : Optional.of(override),
				Optional.ofNullable(modifier),
				size == -1 ? Optional.empty() : Optional.of(size)
		);
	}

	public static PrinterRule read(PacketByteBuf buf) {
		Ingredient input = Ingredient.fromPacket(buf);
		Optional<Integer> override = buf.readOptional(PacketByteBuf::readInt);
		Optional<String> modifier = buf.readOptional(PacketByteBuf::readString);
		Optional<Integer> size = buf.readOptional(PacketByteBuf::readInt);
		return new PrinterRule(input, override, modifier, size);
	}

	public void write(PacketByteBuf buf) {
		input.write(buf);
		buf.writeOptional(override, PacketByteBuf::writeInt);
		buf.writeOptional(modifier, PacketByteBuf::writeString);
		buf.writeOptional(size, PacketByteBuf::writeInt);
	}
}
