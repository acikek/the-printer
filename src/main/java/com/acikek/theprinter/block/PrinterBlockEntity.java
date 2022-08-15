package com.acikek.theprinter.block;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.util.ImplementedInventory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.quiltmc.qsl.block.entity.api.QuiltBlockEntityTypeBuilder;

import java.util.List;

public class PrinterBlockEntity extends BlockEntity implements ImplementedInventory {

	public static BlockEntityType<PrinterBlockEntity> BLOCK_ENTITY_TYPE;

	public Box xpArea;

	public DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);
	public int xp;

	public PrinterBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BLOCK_ENTITY_TYPE, blockPos, blockState);
		xpArea = Box.of(Vec3d.ofCenter(blockPos), 3.5, 3.5, 3.5);
	}

	public void gatherXp(World world) {
		List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, xpArea, PlayerEntity::isSneaking);
		for (PlayerEntity player : players) {
			if (player.experienceLevel == 0 && player.experienceProgress == 0.0f) {
				continue;
			}
			int amount = player.isOnGround() ? 1 : 3;
			player.addExperience(-amount);
			xp += amount;
			if (world.random.nextFloat() > (player.isOnGround() ? 0.5f : 0.3f)) {
				world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.5f, world.random.nextFloat() + 0.5f);
			}
		}
	}

	public static void tick(World world, BlockPos pos, BlockState state, PrinterBlockEntity blockEntity) {
		if (world.getTime() % 2 == 0) {
			blockEntity.gatherXp(world);
		}
	}

	@Override
	public DefaultedList<ItemStack> getItems() {
		return items;
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		Inventories.readNbt(nbt, items);
		xp = nbt.getInt("XP");
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		nbt.putInt("XP", xp);
		Inventories.writeNbt(nbt, items);
		super.writeNbt(nbt);
	}

	public static void register() {
		BLOCK_ENTITY_TYPE = Registry.register(
				Registry.BLOCK_ENTITY_TYPE,
				ThePrinter.id("printer_block_entity"),
				QuiltBlockEntityTypeBuilder.create(PrinterBlockEntity::new, PrinterBlock.INSTANCE).build()
		);
	}
}
