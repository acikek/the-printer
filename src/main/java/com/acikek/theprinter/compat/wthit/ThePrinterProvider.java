package com.acikek.theprinter.compat.wthit;

import com.acikek.theprinter.block.PrinterBlockEntity;
import com.acikek.theprinter.world.ModGameRules;
import mcp.mobius.waila.api.*;
import mcp.mobius.waila.api.component.ItemComponent;
import mcp.mobius.waila.api.component.ProgressArrowComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

public class ThePrinterProvider implements IBlockComponentProvider, IServerDataProvider<PrinterBlockEntity> {

	public static ThePrinterProvider INSTANCE = new ThePrinterProvider();

	@Override
	public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
		if (config.getBoolean(ThePrinterWailaPlugin.PRINTER_INFO)) {
			NbtCompound data = accessor.getServerData();
			if (data.contains("WInput")) {
				ITooltipLine line = tooltip.addLine()
						.with(new ItemComponent(ItemStack.fromNbt(data.getCompound("WInput"))));
				if (data.contains("WProgress")) {
					line.with(new ProgressArrowComponent(data.getFloat("WProgress")));
				}
				else if (data.contains("WXP")) {
					line.with(Text.Serializer.fromJson(data.getString("WXP")));
				}
			}
		}
	}

	@Override
	public void appendServerData(NbtCompound data, IServerAccessor<PrinterBlockEntity> accessor, IPluginConfig config) {
		if (config.getBoolean(ThePrinterWailaPlugin.PRINTER_INFO)) {
			PrinterBlockEntity printer = accessor.getTarget();
			if (!printer.getItems().isEmpty()) {
				data.put("WInput", printer.getItems().get(0).copy().writeNbt(new NbtCompound()));
			}
			if (printer.progress > 0) {
				data.putFloat("WProgress", (float) printer.progress / printer.requiredTicks);
			}
			else if (ModGameRules.isXPRequired(accessor.getWorld()) && printer.requiredXP > 0) {
				Text text = Text.literal("XP: " + (printer.xp + "/" + printer.requiredXP));
				data.putString("WXP", Text.Serializer.toJson(text));
			}
		}
	}
}
