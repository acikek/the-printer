package com.acikek.theprinter.block;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.sound.ModSoundEvents;
import com.acikek.theprinter.util.ImplementedInventory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.command.StopSoundCommand;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Rarity;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.qsl.block.entity.api.QuiltBlockEntityTypeBuilder;
import org.quiltmc.qsl.networking.api.PlayerLookup;

import java.util.List;

public class PrinterBlockEntity extends BlockEntity implements ImplementedInventory {

	public static final int BASE_ITEM_COST = 55;
	public static final int BASE_BLOCK_COST = 91;
	public static final int PRINTING_INTERVAL = 180;

	public static BlockEntityType<PrinterBlockEntity> BLOCK_ENTITY_TYPE;

	public Box xpArea;

	public DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);
	public int xp = 0;
	public int requiredXP = -1;
	public int progress = 0;
	public int requiredTicks = -1;
	public int startOffset = 0;

	public PrinterBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BLOCK_ENTITY_TYPE, blockPos, blockState);
		xpArea = Box.of(Vec3d.ofCenter(blockPos), 3.5, 3.5, 3.5);
	}

	public void addItem(PlayerEntity player, ItemStack handStack) {
		requiredXP = getRequiredXP(handStack);
		requiredTicks = requiredXP * 3;
		setStack(0, handStack.copy());
		if (!player.isCreative()) {
			handStack.decrement(1);
		}
	}

	public void removeItem(PlayerEntity player, boolean printed) {
		if (!player.isCreative()) {
			player.giveItemStack(removeStack(0));
		}
		if (!printed) {
			requiredXP = -1;
			requiredTicks = -1;
		}
	}

	public static int getRarityXPMultiplier(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> 1;
			case UNCOMMON -> 2;
			case RARE -> 4;
			case EPIC -> 6;
		};
	}

	public static int getMaterialMultiplier(Item item) {
		if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof PrinterBlock) {
			return 8;
		}
		else if (item instanceof ToolItem) {
			return 3;
		}
		return 1;
	}

	public static int getNbtXPCost(ItemStack stack) {
		if (!stack.hasNbt()) {
			return 0;
		}
		NbtCompound nbt = stack.getOrCreateNbt();
		return nbt.getKeys().size() * 20;
	}

	/**
	 * Calculates the required experience cost for a given stack.
	 *
	 * TODO: Incorporate advancements
	 * TODO: Custom override behavior
	 */
	public static int getRequiredXP(ItemStack stack) {
		int baseCost = stack.getItem() instanceof BlockItem ? BASE_BLOCK_COST : BASE_ITEM_COST;
		int materialMultiplier = getMaterialMultiplier(stack.getItem());
		int rarityMultiplier = getRarityXPMultiplier(stack.getRarity());
		int nbtCost = getNbtXPCost(stack);
		return (baseCost * materialMultiplier * rarityMultiplier) + nbtCost;
	}

	public static boolean canDepositXP(PlayerEntity player) {
		return player.isSneaking() && player.experienceLevel != 0 && player.experienceProgress > 0.0f;
	}

	/**
	 * Searches for players within the {@link PrinterBlockEntity#xpArea} that qualify {@link PrinterBlockEntity#canDepositXP(PlayerEntity)}
	 * and takes some experience points from them, more if they're jumping.
	 * @return Whether the machine has reached the {@link PrinterBlockEntity#requiredXP} for printing.
	 */
	public boolean gatherXp(World world) {
		List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, xpArea, PrinterBlockEntity::canDepositXP);
		for (PlayerEntity player : players) {
			int amount = player.isOnGround() ? 1 : 3;
			player.addExperience(-amount);
			xp += amount;
			if (world.random.nextFloat() > (player.isOnGround() ? 0.5f : 0.3f)) {
				world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.5f, world.random.nextFloat() + 0.5f);
			}
			if (xp >= requiredXP) {
				return true;
			}
		}
		return false;
	}

	public static int getTickOffset(World world) {
		return (int) (world.getTime() % PRINTING_INTERVAL);
	}

	public void startPrinting(World world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state.with(PrinterBlock.PRINTING, true));
		startOffset = getTickOffset(world) + 1;
	}

	public void progressPrinting(World world, BlockPos pos) {
		progress++;
		if (getTickOffset(world) == startOffset) {
			world.playSound(null, pos, ModSoundEvents.PRINTING, SoundCategory.BLOCKS, 1.0f, 1.0f);
		}
	}

	public void finishPrinting(World world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state.with(PrinterBlock.PRINTING, false));
		xp = 0;
		startOffset = 0;
		progress = 0;
		if (world instanceof ServerWorld serverWorld) {
			StopSoundS2CPacket packet = new StopSoundS2CPacket(ModSoundEvents.PRINTING.getId(), SoundCategory.BLOCKS);
			for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
				player.networkHandler.sendPacket(packet);
			}
		}
	}

	public static void tick(World world, BlockPos pos, BlockState state, PrinterBlockEntity blockEntity) {
		if (!world.isClient()) {
			boolean on = state.get(PrinterBlock.ON);
			boolean printing = state.get(PrinterBlock.PRINTING);
			if (on && !printing && world.getTime() % 2 == 0 && blockEntity.gatherXp(world)) {
				blockEntity.startPrinting(world, pos, state);
			}
			if (printing && blockEntity.progress < blockEntity.requiredTicks) {
				blockEntity.progressPrinting(world, pos);
				if (blockEntity.progress == blockEntity.requiredTicks) {
					blockEntity.finishPrinting(world, pos, state);
				}
			}
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
		requiredXP = nbt.getInt("RequiredXP");
		progress = nbt.getInt("Progress");
		requiredTicks = nbt.getInt("RequiredTicks");
		startOffset = nbt.getInt("StartOffset");
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		Inventories.writeNbt(nbt, items);
		nbt.putInt("XP", xp);
		nbt.putInt("RequiredXP", requiredXP);
		nbt.putInt("Progress", progress);
		nbt.putInt("RequiredTicks", requiredTicks);
		nbt.putInt("StartOffset", startOffset);
		super.writeNbt(nbt);
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.of(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt() {
		return toNbt();
	}

	public static void register() {
		BLOCK_ENTITY_TYPE = Registry.register(
				Registry.BLOCK_ENTITY_TYPE,
				ThePrinter.id("printer_block_entity"),
				QuiltBlockEntityTypeBuilder.create(PrinterBlockEntity::new, PrinterBlock.INSTANCE).build()
		);
	}
}
