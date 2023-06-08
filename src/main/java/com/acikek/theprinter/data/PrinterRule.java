package com.acikek.theprinter.data;

import com.google.gson.JsonObject;
import com.udojava.evalex.Expression;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Rarity;

import java.util.*;

public class PrinterRule {

	public enum Type {

		OVERRIDE(true),
		MODIFIER(false),
		SIZE(true),
		ENABLED(true);

		public final boolean exclusive;

		Type(boolean exclusive) {
			this.exclusive = exclusive;
		}
	}

	public static Map<Identifier, PrinterRule> RULES = new HashMap<>();

	public static final int BASE_ITEM_COST = 55;
	public static final int BASE_BLOCK_COST = 91;

	public Ingredient input;
	public Optional<Integer> override;
	public Optional<String> modifier;
	public Optional<Integer> size;
	public Optional<Boolean> enabled;
	public Expression expression;
	public List<Type> types = new ArrayList<>();

	public PrinterRule(Ingredient input, Optional<Integer> override, Optional<String> modifier, Optional<Integer> size, Optional<Boolean> enabled) {
		this.input = input;
		this.override = override;
		this.modifier = modifier;
		this.size = size;
		this.enabled = enabled;
		modifier.ifPresent(s -> expression = new Expression(s));
		if (override.isPresent() || modifier.isPresent()) {
			types.add(override.isPresent() ? Type.OVERRIDE : Type.MODIFIER);
		}
		if (size.isPresent()) {
			types.add(Type.SIZE);
		}
		if (enabled.isPresent()) {
			types.add(Type.ENABLED);
		}
	}

	public boolean validate() {
		if (types.isEmpty()) {
			throw new IllegalStateException("rule must contain properties other than 'input'");
		}
		if (override.isPresent() && modifier.isPresent()) {
			throw new IllegalStateException("'modifier' rule is mutually exclusive with 'override'");
		}
		return true;
	}

	public static PrinterRules getMatchingRules(ItemStack stack) {
		if (RULES.isEmpty()) {
			return new PrinterRules(Collections.emptyList());
		}
		var list = RULES.entrySet().stream()
				.filter(pair -> pair.getValue().input.test(stack))
				.toList();
		return new PrinterRules(list);
	}

	public static int getRarityXPMultiplier(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> 1;
			case UNCOMMON -> 2;
			case RARE -> 4;
			case EPIC -> 6;
		};
	}

	public static int getNbtXPCost(ItemStack stack) {
		if (!stack.hasNbt()) {
			return 0;
		}
		NbtCompound nbt = stack.getOrCreateNbt();
		return nbt.getKeys().size() * 20;
	}

	public static int getBaseXP(ItemStack stack) {
		int baseCost = stack.getItem() instanceof BlockItem ? BASE_BLOCK_COST : BASE_ITEM_COST;
		int materialMultiplier = stack.getItem() instanceof ToolItem ? 3 : 1;
		int rarityMultiplier = getRarityXPMultiplier(stack.getRarity());
		int nbtCost = getNbtXPCost(stack);
		return (baseCost * materialMultiplier * rarityMultiplier) + nbtCost;
	}

	public static PrinterRule fromJson(JsonObject obj) {
		Ingredient input = Ingredient.method_8102(obj.get("input"), false);
		int override = JsonHelper.getInt(obj, "override", -1);
		String modifier = JsonHelper.getString(obj, "modifier", null);
		int size = JsonHelper.getInt(obj, "size", -1);
		Boolean enabled = JsonHelper.hasBoolean(obj, "enabled") ? JsonHelper.getBoolean(obj, "enabled") : null;
		return new PrinterRule(
				input,
				override == -1 ? Optional.empty() : Optional.of(override),
				Optional.ofNullable(modifier),
				size == -1 ? Optional.empty() : Optional.of(size),
				Optional.ofNullable(enabled)
		);
	}

	public static PrinterRule read(PacketByteBuf buf) {
		Ingredient input = Ingredient.fromPacket(buf);
		Optional<Integer> override = buf.readOptional(PacketByteBuf::readInt);
		Optional<String> modifier = buf.readOptional(PacketByteBuf::readString);
		Optional<Integer> size = buf.readOptional(PacketByteBuf::readInt);
		Optional<Boolean> enabled = buf.readOptional(PacketByteBuf::readBoolean);
		return new PrinterRule(input, override, modifier, size, enabled);
	}

	public void write(PacketByteBuf buf) {
		input.write(buf);
		buf.writeOptional(override, PacketByteBuf::writeInt);
		buf.writeOptional(modifier, PacketByteBuf::writeString);
		buf.writeOptional(size, PacketByteBuf::writeInt);
		buf.writeOptional(enabled, PacketByteBuf::writeBoolean);
	}

	@Override
	public String toString() {
		List<String> values = new ArrayList<>();
		for (Type type : types) {
			values.add(switch (type) {
				case OVERRIDE -> "override=" + override.orElse(null);
				case MODIFIER -> "modifier=" + modifier.orElse(null);
				case SIZE -> "size=" + size.orElse(null);
				case ENABLED -> "enabled=" + enabled.orElse(null);
			});
		}
		return "PrinterRule["
				+ "for " + input.toJson() + "; "
				+ String.join(", ", values)
				+ "]";
	}
}
