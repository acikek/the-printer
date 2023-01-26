package com.acikek.theprinter.compat.wthit;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.block.PrinterBlockEntity;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import net.minecraft.util.Identifier;

public class ThePrinterWailaPlugin implements IWailaPlugin {

	public static final Identifier PRINTER_INFO = ThePrinter.id("printer_info");

	@Override
	public void register(IRegistrar registrar) {
		registrar.addConfig(PRINTER_INFO, true);
		registrar.addComponent(ThePrinterProvider.INSTANCE, TooltipPosition.BODY, PrinterBlockEntity.class);
		registrar.addBlockData(ThePrinterProvider.INSTANCE, PrinterBlockEntity.class);
	}
}
