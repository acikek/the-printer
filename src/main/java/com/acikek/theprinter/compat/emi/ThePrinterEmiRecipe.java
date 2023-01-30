package com.acikek.theprinter.compat.emi;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.client.ThePrinterClient;
import com.acikek.theprinter.data.PrinterRule;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.*;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ThePrinterEmiRecipe implements EmiRecipe {

	public static final EmiTexture INPUT_SCREEN_OFF = getScreenTexture(false, 0);
	public static final List<EmiTexture> INPUT_SCREEN_ON = new ArrayList<>();
	public static final EmiTexture OUTPUT_GRID = new EmiTexture(ThePrinterEmiPlugin.SPRITES, 0, 106, 32, 19, 256, 150, 256, 256);
	public static final EmiTexture XP_ORB = new EmiTexture(ThePrinterEmiPlugin.SPRITES, 0, 32, 9, 9);

	public static EmiTexture getScreenTexture(boolean on, int index) {
		return new EmiTexture(ThePrinterEmiPlugin.SPRITES, index * 16, on ? 16 : 0, 30, 30, 16, 16, 256, 256);
	}

	static {
		for (int i = 0; i < 11; i++) {
			INPUT_SCREEN_ON.add(getScreenTexture(true, i));
		}
	}

	public Map.Entry<List<Map.Entry<Identifier, PrinterRule>>, List<EmiStack>> data;
	public EmiIngredient input;
	public List<EmiStack> output;
	public Identifier id;
	public int maxStackSize;

	public int stackSize = 1;
	public int requiredXP;
	public int requiredTicks;
	public boolean doXPText;

	public ThePrinterEmiRecipe(Map.Entry<List<Map.Entry<Identifier, PrinterRule>>, List<EmiStack>> data) {
		this.data = data;
		List<EmiStack> stacks = data.getValue();
		input = EmiIngredient.of(stacks);
		output = stacks;
		id = createId();
		maxStackSize = getMaxStackSize();
		update();
	}

	public Stream<Identifier> getRuleIds() {
		return data.getKey().stream().map(Map.Entry::getKey);
	}

	public Identifier createId() {
		List<String> namespaces = getRuleIds()
				.map(Identifier::getNamespace)
				.distinct()
				.toList();
		boolean singleNamespace = namespaces.size() == 1;
		String namespace = singleNamespace ? namespaces.get(0) : ThePrinter.ID;
		List<String> formattedIds = getRuleIds()
				.map(id -> singleNamespace ? id.getPath() : id.toString())
				.map(string -> string.replaceAll("[:/]", "_"))
				.toList();
		List<String> stackNames = data.getValue().stream()
				.map(stack -> stack.getItemStack().getItem().toString())
				.toList();
		String path = String.join("/", formattedIds) + "/" + String.join("/", stackNames);
		return new Identifier(namespace, "/" + path);
	}

	public int getMaxStackSize() {
		List<PrinterRule> sizeRules = data.getKey().stream()
				.map(Map.Entry::getValue)
				.filter(rule -> rule.types.contains(PrinterRule.Type.SIZE))
				.toList();
		return !sizeRules.isEmpty() ? sizeRules.get(0).size.orElse(1) : 1;
	}

	public void update() {
		ItemStack stack = data.getValue().get(0).getItemStack().withCount(stackSize);
		requiredXP = Math.max(1, PrinterRule.getRequiredXP(data.getKey(), stack));
		requiredTicks = requiredXP * 3;
		if (!ThePrinterClient.xpRequired) {
			requiredXP = 0;
		}
		doXPText = requiredXP <= 999;
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return ThePrinterEmiPlugin.CATEGORY;
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return List.of();
	}

	@Override
	public List<EmiIngredient> getCatalysts() {
		return List.of(input);
	}

	@Override
	public List<EmiStack> getOutputs() {
		return output;
	}

	@Override
	public boolean supportsRecipeTree() {
		return false;
	}

	@Override
	public int getDisplayWidth() {
		return 100;
	}

	@Override
	public int getDisplayHeight() {
		return 34;
	}

	public static class FrontTextWidget extends TextWidget {

		public FrontTextWidget(OrderedText text, int x, int y, int color, boolean shadow) {
			super(text, x, y, color, shadow);
		}

		@Override
		public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
			matrices.push();
			matrices.translate(0.0, 0.0, 1.0);
			super.render(matrices, mouseX, mouseY, delta);
			matrices.pop();
		}
	}

	public static class TooltipButtonWidget extends ButtonWidget {

		public Supplier<TooltipComponent> tooltipSupplier;

		public TooltipButtonWidget(int x, int y, int width, int height, int u, int v, Identifier texture, BooleanSupplier isActive, ClickAction action, Supplier<TooltipComponent> tooltipSupplier) {
			super(x, y, width, height, u, v, texture, isActive, action);
			this.tooltipSupplier = tooltipSupplier;
		}

		@Override
		public List<TooltipComponent> getTooltip(int mouseX, int mouseY) {
			return List.of(tooltipSupplier.get());
		}
	}

	public void addAnimatableScreen(WidgetHolder widgets, int yOffset) {
		widgets.addDrawable(1, 1 + yOffset, 30, 30, (matrices, mouseX, mouseY, delta) -> {
			boolean inBounds = mouseX >= 0 && mouseX <= getDisplayWidth() && mouseY >= 0 && mouseY <= getDisplayHeight();
			EmiTexture screen = !inBounds
					? INPUT_SCREEN_OFF
					: INPUT_SCREEN_ON.get((int) (System.currentTimeMillis() % (1100L) / 100L));
			screen.render(matrices, 0, 0, delta);
		});
	}

	public void addStackSizeButtons(WidgetHolder widgets) {
		widgets.addButton(5, 25, 9, 9, 238, 0, ThePrinterEmiPlugin.SPRITES, () -> stackSize > 1, (mouseX, mouseY, button) -> {
			if (stackSize > 1) {
				stackSize -= 1;
				update();
			}
		});
		Supplier<TooltipComponent> maxStackSizeTooltip = () ->
				TooltipComponent.of(Text.translatable("emi.tooltip.theprinter.max_size", maxStackSize).asOrderedText());
		var increaseButton = new TooltipButtonWidget(18, 25, 9, 9, 247, 0, ThePrinterEmiPlugin.SPRITES, () -> stackSize < maxStackSize, (mouseX, mouseY, button) -> {
			if (stackSize < maxStackSize) {
				stackSize += 1;
				update();
			}
		}, maxStackSizeTooltip);
		widgets.add(increaseButton);
		widgets.addDrawable(25, 13, 5, 7, (matrices, mouseX, mouseY, delta) -> {
			if (stackSize > 1) {
				new FrontTextWidget(Text.literal(String.valueOf(stackSize)).asOrderedText(), 0, 0, 0xFFFFFF, true)
						.horizontalAlign(TextWidget.Alignment.END)
						.render(matrices, mouseX, mouseY, delta);
			}
		});
	}

	public void addInputScreen(WidgetHolder widgets) {
		boolean variableStackSize = maxStackSize > 1;
		int yOffset = variableStackSize ? 0 : 5;
		addAnimatableScreen(widgets, yOffset);
		widgets.addSlot(input, 7, 3 + yOffset).drawBack(false).catalyst(true);
		if (variableStackSize) {
			addStackSizeButtons(widgets);
		}
	}

	public void addOutputGrid(WidgetHolder widgets) {

		widgets.addTexture(OUTPUT_GRID, 67, 13);
		widgets.addSlot(input, 74, 2).drawBack(false).recipeContext(this);
	}

	public void addXPInfo(WidgetHolder widgets) {
		DrawableWidget widget = widgets.addDrawable(41, 22, 9, 9, (matrices, mouseX, mouseY, delta) -> {
			String xpTextString = doXPText ? String.valueOf(requiredXP) : "...";
			int color = doXPText ? 0xFFFFFF : 0x8B8B8B;
			TextWidget xpText = new TextWidget(Text.literal(xpTextString).asOrderedText(), 14 + (doXPText ? 0 : -3), 0, color, doXPText);
			if (doXPText) {
				xpText.horizontalAlign(TextWidget.Alignment.CENTER);
			}
			TextureWidget xpOrb = new TextureWidget(XP_ORB.texture, xpText.getBounds().x() - 11, 0, XP_ORB.width, XP_ORB.height, XP_ORB.u, XP_ORB.v);
			xpText.render(matrices, mouseX, mouseY, delta);
			xpOrb.render(matrices, mouseX, mouseY, delta);
		});
		widget.tooltip((mx, my) -> {
			if (doXPText) {
				return Collections.emptyList();
			}
			Text text = Text.literal(String.valueOf(requiredXP)).formatted(Formatting.GREEN);
			return List.of(TooltipComponent.of(text.asOrderedText()));
		});
	}

	public void addProcessingInfo(WidgetHolder widgets) {
		boolean displayXP = requiredXP > 0;
		widgets.addFillingArrow(38, 4 + (displayXP ? 0 : 5), requiredTicks * 50).tooltip((mx, my) ->
				List.of(TooltipComponent.of((Text.translatable("emi.cooking.time", requiredTicks / 20.0f).asOrderedText())))
		);
		if (!displayXP) {
			return;
		}
		addXPInfo(widgets);
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		addInputScreen(widgets);
		addOutputGrid(widgets);
		addProcessingInfo(widgets);
	}
}
