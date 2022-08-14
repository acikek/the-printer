package com.acikek.theprinter.block;

import com.acikek.theprinter.ThePrinter;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.quiltmc.qsl.block.entity.api.QuiltBlockEntityTypeBuilder;

public class PrinterBlockEntity extends BlockEntity {

	public static BlockEntityType<PrinterBlockEntity> BLOCK_ENTITY_TYPE;

	public PrinterBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BLOCK_ENTITY_TYPE, blockPos, blockState);
	}

	public static void tick(World world, BlockPos pos, BlockState state, PrinterBlockEntity blockEntity) {

	}

	public static void register() {
		BLOCK_ENTITY_TYPE = Registry.register(
				Registry.BLOCK_ENTITY_TYPE,
				ThePrinter.id("printer_block_entity"),
				QuiltBlockEntityTypeBuilder.create(PrinterBlockEntity::new, PrinterBlock.INSTANCE).build()
		);
	}
}
