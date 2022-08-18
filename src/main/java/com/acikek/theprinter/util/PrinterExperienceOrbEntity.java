package com.acikek.theprinter.util;

import net.minecraft.util.math.BlockPos;

public interface PrinterExperienceOrbEntity {

	BlockPos getPrinterTarget();

	boolean canDeposit();

	void setPrinterTarget(BlockPos target);
}
