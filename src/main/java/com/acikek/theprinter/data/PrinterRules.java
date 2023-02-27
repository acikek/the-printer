package com.acikek.theprinter.data;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.world.ModGameRules;
import com.udojava.evalex.Expression;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.math.BigDecimal;
import java.util.*;

public record PrinterRules(List<Map.Entry<Identifier, PrinterRule>> rules) {

	public PrinterRules filterByType(PrinterRule.Type type, Object sourceObject) {
		var result = rules.stream()
				.filter(rule -> rule.getValue().types.contains(type))
				.toList();
		if (type.exclusive && result.size() > 1) {
			List<String> ids = result.stream()
					.map(Map.Entry::getKey)
					.map(Identifier::toString)
					.toList();
			String joined = String.join(", ", ids);
			ThePrinter.LOGGER.warn("Multiple " + type + " rules for object '" + sourceObject + "': " + joined);
		}
		return new PrinterRules(result);
	}

	public int getRequiredXP(ItemStack stack) {
		var overrides = filterByType(PrinterRule.Type.OVERRIDE, stack);
		if (!overrides.rules.isEmpty()) {
			return overrides.rules.get(0).getValue().override.orElse(0);
		}
		int baseXP = PrinterRule.getBaseXP(stack);
		var modifiers = filterByType(PrinterRule.Type.MODIFIER, null);
		if (!modifiers.rules.isEmpty()) {
			double result = baseXP;
			List<Expression> expressions = modifiers.rules.stream()
					.map(rule -> rule.getValue().expression)
					.filter(Objects::nonNull)
					.toList();
			for (Expression expression : expressions) {
				result = expression
						.with("value", BigDecimal.valueOf(result))
						.with("size", BigDecimal.valueOf(stack.getCount()))
						.eval().doubleValue();
			}
			return (int) result;
		}
		return baseXP;
	}

	/**
	 * @return the maximum amount of items allowed for a specified stack based on any {@link PrinterRule.Type#SIZE} rules.
	 */
	public int getMaxInsertCount(ItemStack stack) {
		var sizeRules = filterByType(PrinterRule.Type.SIZE, stack);
		if (!sizeRules.rules().isEmpty()) {
			return sizeRules.rules().get(0).getValue().size.orElse(1);
		}
		return 1;
	}

	/**
	 * @return whether the specified stack can be inserted based on any {@link PrinterRule.Type#ENABLED} rules
	 * and the {@link ModGameRules#PRINTER_ENABLED} game rule.
	 */
	public boolean canBeInserted(World world, ItemStack stack) {
		var enabledRules = filterByType(PrinterRule.Type.ENABLED, stack);
		boolean gameruleEnabled = ModGameRules.isPrinterEnabled(world);
		return enabledRules.rules().isEmpty()
				? gameruleEnabled
				: enabledRules.rules().get(0).getValue().enabled.orElse(gameruleEnabled);
	}

	public static PrinterRules readNbt(NbtCompound nbt) {
		if (!nbt.contains("Rules")) {
			return null;
		}
		NbtList ids = nbt.getList("Rules", NbtList.STRING_TYPE);
		List<Map.Entry<Identifier, PrinterRule>> list = new ArrayList<>();
		for (NbtElement element : ids) {
			Identifier id = Identifier.tryParse(element.asString());
			if (!PrinterRule.RULES.containsKey(id)) {
				ThePrinter.LOGGER.error("Unknown rule '" + id + "'");
				continue;
			}
			list.add(new AbstractMap.SimpleImmutableEntry<>(id, PrinterRule.RULES.get(id)));
		}
		return new PrinterRules(list);
	}

	public void writeNbt(NbtCompound nbt) {
		List<NbtString> ids = rules.stream()
				.map(Map.Entry::getKey)
				.map(Identifier::toString)
				.map(NbtString::of)
				.toList();
		NbtList list = new NbtList();
		list.addAll(ids);
		nbt.put("Rules", list);
	}
}
