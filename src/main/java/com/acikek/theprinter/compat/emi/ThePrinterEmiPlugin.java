package com.acikek.theprinter.compat.emi;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.block.PrinterBlock;
import com.acikek.theprinter.client.ThePrinterClient;
import com.acikek.theprinter.data.PrinterRule;
import com.acikek.theprinter.data.PrinterRules;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ThePrinterEmiPlugin implements EmiPlugin {

	public static final Identifier SPRITES = ThePrinter.id("textures/gui/emi_sheet.png");
	public static final EmiStack WORKSTATION = EmiStack.of(PrinterBlock.INSTANCE);
	public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(ThePrinter.id("the_printer"), WORKSTATION);

	public static List<EmiStack> getMatchingStacks(Ingredient ingredient) {
		return Arrays.stream(ingredient.getMatchingStacks())
				.map(EmiStack::of)
				.toList();
	}

	public static Set<EmiStack> findStacks(Collection<PrinterRule> rules, Function<PrinterRule, Boolean> predicate) {
		Set<EmiStack> stacks = new HashSet<>();
		for (PrinterRule rule : rules) {
			if (predicate.apply(rule)) {
				stacks.addAll(getMatchingStacks(rule.input));
			}
		}
		return stacks;
	}

	public static List<Map.Entry<Identifier, PrinterRule>> gatherRelevantRules() {
		if (ThePrinterClient.printerEnabled) {
			return PrinterRule.RULES.entrySet().stream()
					.filter(entry -> entry.getValue().enabled.isEmpty())
					.toList();
		}
		Set<EmiStack> enabledStacks = findStacks(PrinterRule.RULES.values(), rule -> rule.enabled.orElse(false));
		return PrinterRule.RULES.entrySet().stream()
				.filter(entry -> entry.getValue().enabled.orElse(true))
				.filter(entry -> {
					List<EmiStack> matchingStacks = getMatchingStacks(entry.getValue().input);
					return enabledStacks.stream().anyMatch(matchingStacks::contains);
				})
				.toList();
	}

	public static Map<PrinterRules, List<EmiStack>> mapRulesToStacks(List<Map.Entry<Identifier, PrinterRule>> relevantRules) {
		Map<EmiStack, List<Map.Entry<Identifier, PrinterRule>>> stacksToRules = new HashMap<>();
		List<PrinterRule> ruleList = relevantRules.stream()
				.map(Map.Entry::getValue)
				.toList();
		Set<EmiStack> stacks = findStacks(ruleList, rule -> true);
		for (EmiStack stack : stacks) {
			var rulesForStack = relevantRules.stream()
					.filter(entry -> entry.getValue().input.test(stack.getItemStack()))
					.toList();
			stacksToRules.put(stack, rulesForStack);
		}
		Map<List<Map.Entry<Identifier, PrinterRule>>, List<EmiStack>> rulesToStacks = new HashMap<>();
		for (Map.Entry<EmiStack, List<Map.Entry<Identifier, PrinterRule>>> entry : stacksToRules.entrySet()) {
			boolean hasRules = rulesToStacks.containsKey(entry.getValue());
			boolean onlyEnabled = entry.getValue().stream().allMatch(rule -> rule.getValue().enabled.orElse(false));
			if (hasRules && !onlyEnabled) {
				rulesToStacks.get(entry.getValue()).add(entry.getKey());
			}
			else {
				List<EmiStack> listStart = new ArrayList<>(List.of(entry.getKey()));
				rulesToStacks.put(hasRules ? new ArrayList<>(entry.getValue()) : entry.getValue(), listStart);
			}
		}
		return rulesToStacks.entrySet().stream()
				.collect(Collectors.toMap(entry -> new PrinterRules(entry.getKey()), Map.Entry::getValue));
	}

	@Override
	public void register(EmiRegistry registry) {
		registry.addCategory(CATEGORY);
		registry.addWorkstation(CATEGORY, WORKSTATION);
		var relevantRules = gatherRelevantRules();
		var rulesToStacks = mapRulesToStacks(relevantRules);
		for (var data : rulesToStacks.entrySet()) {
			registry.addRecipe(new ThePrinterEmiRecipe(data));
		}
	}
}
