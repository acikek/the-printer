package com.acikek.theprinter.block;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class PrinterBlockStorage {

	//public static final Identifier UPDATE = ThePrinter.id("printer_update");

	/**
	 * The printer's inventory.
	 * <p>
	 * The first stack will show up on the screen. It should be an exact copy of the input stack.<br>
	 * The second stack will render above the machine as it is printing the item. It is allowed to differ from the original stack.
	 * </p>
	 */
	public DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);
	public PrinterBlockEntity printer;

	public PrinterBlockStorage(PrinterBlockEntity printer) {
		this.printer = printer;
	}

	public class StorageBase extends SingleStackStorage {

		public int slot;

		public StorageBase(int slot) {
			this.slot = slot;
		}

		@Override
		protected ItemStack getStack() {
			return items.get(slot);
		}

		@Override
		protected void setStack(ItemStack stack) {
			printer.markDirty();
			printer.getWorld().updateListeners(printer.getPos(), printer.getCachedState(), printer.getCachedState(), Block.NOTIFY_LISTENERS);
			items.set(slot, stack);
		}
	}

	public Storage<ItemVariant> input = FilteringStorage.readOnlyOf(new StorageBase(0));

	public Storage<ItemVariant> output = new StorageBase(1) {

		@Override
		protected boolean canExtract(ItemVariant itemVariant) {
			return printer.getCachedState().get(PrinterBlock.FINISHED);
		}

		@Override
		protected void onFinalCommit() {
			if (getStack().isEmpty()) {
				printer.setFinished(printer.getWorld(), printer.getPos(), printer.getCachedState());
			}
			super.onFinalCommit();
		}
	};

	public Storage<ItemVariant> exposedOutput = FilteringStorage.extractOnlyOf(output);

	public Storage<ItemVariant> combined = new CombinedStorage<>(List.of(input, exposedOutput));

	public static Storage<ItemVariant> getExposedStorage(PrinterBlockEntity blockEntity, Direction direction) {
		PrinterBlockStorage storage = blockEntity.storage;
		if (direction == Direction.DOWN) {
			return storage.exposedOutput;
		}
		if (direction != Direction.UP) {
			return storage.combined;
		}
		return null;
	}

	public static void register() {
		ItemStorage.SIDED.registerForBlockEntity(PrinterBlockStorage::getExposedStorage, PrinterBlockEntity.BLOCK_ENTITY_TYPE);
	}
}
