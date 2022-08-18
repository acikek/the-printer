package com.acikek.theprinter.block;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.advancement.PrinterUsedCriterion;
import com.acikek.theprinter.sound.ModSoundEvents;
import com.acikek.theprinter.util.ImplementedInventory;
import com.acikek.theprinter.util.PrinterExperienceOrbEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ExperienceOrbEntity;
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

import java.util.List;

public class PrinterBlockEntity extends BlockEntity implements ImplementedInventory {

	public static final int BASE_ITEM_COST = 55;
	public static final int BASE_BLOCK_COST = 91;
	public static final int PRINTING_INTERVAL = 180;

	public static BlockEntityType<PrinterBlockEntity> BLOCK_ENTITY_TYPE;

	public Box playerDepositArea;
	public Box orbDepositArea;

	/**
	 * The printer's inventory.
	 * <p>
	 * The first stack will show up on the screen. Its quantity should be limited to {@code 1}, but in all other ways a copy of the input stack.<br>
	 * The second stack will render above the machine as it is printing the item. Its quantity should <bold>not</bold> be limited, but modifications are allowed.
	 * </p>
	 */
	public DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);

	public int xp = 0;
	public int requiredXP = -1;
	public int progress = 0;
	public int requiredTicks = -1;
	public int startOffset = 0;

	public PrinterBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BLOCK_ENTITY_TYPE, blockPos, blockState);
		Vec3d center = Vec3d.ofCenter(blockPos);
		playerDepositArea = Box.of(center, 3.5, 2.5, 3.5);
		orbDepositArea = Box.of(center, 6.0, 3.5, 6.0);
	}

	public static int getMaxStackCount(ItemStack stack) {
		return 1;
	}

	public void setStack(int slot, ItemStack handStack, int count) {
		ItemStack copy = handStack.copy();
		copy.setCount(count);
		setStack(slot, copy);
	}

	public void addItem(PlayerEntity player, ItemStack handStack) {
		requiredXP = getRequiredXP(handStack);
		requiredTicks = requiredXP * 3;
		setStack(0, handStack, 1);
		setStack(1, handStack, getMaxStackCount(handStack));
		if (!player.isCreative()) {
			handStack.decrement(1);
		}
	}

	public void tryDropXP(ServerWorld world, BlockPos pos) {
		if (xp > 0) {
			Vec3d xpPos = Vec3d.ofCenter(pos).add(0.0, 0.4, 0.0);
			ExperienceOrbEntity.spawn(world, xpPos, xp);
		}
	}

	/**
	 * Removes an item from the inventory.<br>
	 * If the item was printed, doesn't remove the item. Otherwise, removes the item, drops any leftover experience, and resets values.
	 * Either way, if the player isn't in creative mode, gives them the target item.<br>
	 * If the item was printed, also triggers {@link com.acikek.theprinter.advancement.PrinterUsedCriterion}.
	 *
	 * @see	PrinterBlockEntity#tryDropXP(ServerWorld, BlockPos)
	 * @param printed Whether the printed item is being removed.
	 */
	public void removeItem(World world, BlockPos pos, PlayerEntity player, boolean printed) {
		ItemStack removed = printed ? getStack(1) : removeStack(0);
		if (!player.isCreative()) {
			player.giveItemStack(removed);
		}
		if (!printed) {
			if (world instanceof ServerWorld serverWorld) {
				tryDropXP(serverWorld, pos);
			}
			requiredXP = -1;
			requiredTicks = -1;
		}
		else if (player instanceof ServerPlayerEntity serverPlayer) {
			PrinterUsedCriterion.INSTANCE.trigger(serverPlayer, requiredXP, requiredTicks, removed, removed.getRarity());
		}
		xp = 0;
	}

	public DefaultedList<ItemStack> getActualInventory(BlockState state) {
		if (getStack(1).isEmpty() || state.get(PrinterBlock.FINISHED)) {
			return getItems();
		}
		return DefaultedList.copyOf(ItemStack.EMPTY, getStack(0));
	}

	public static int getRarityXPMultiplier(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> 1;
			case UNCOMMON -> 2;
			case RARE -> 4;
			case EPIC -> 6;
		};
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
		int materialMultiplier = stack.getItem() instanceof ToolItem ? 3 : 1;
		int rarityMultiplier = getRarityXPMultiplier(stack.getRarity());
		int nbtCost = getNbtXPCost(stack);
		return (baseCost * materialMultiplier * rarityMultiplier) + nbtCost;
	}

	public static boolean canDepositXP(PlayerEntity player) {
		return player.isSneaking() && player.experienceLevel != 0 && player.experienceProgress > 0.0f;
	}

	public static boolean canDepositXP(ExperienceOrbEntity orb) {
		PrinterExperienceOrbEntity printerOrb = (PrinterExperienceOrbEntity) orb;
		return printerOrb.canDeposit() && printerOrb.getPrinterTarget() == null;
	}

	public int adjustAmount(int amount) {
		if (xp + amount >= requiredXP) {
			return requiredXP - xp;
		}
		return amount;
	}

	public void depositXP(World world, int amount, float soundChance) {
		xp += amount;
		if (world.random.nextFloat() > soundChance) {
			world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.5f, world.random.nextFloat() + 0.5f);
		}
	}

	/**
	 * Searches for players within the {@link PrinterBlockEntity#playerDepositArea} that qualify {@link PrinterBlockEntity#canDepositXP(PlayerEntity)}
	 * and takes some experience points from them, more if they're jumping.
	 */
	public void gatherXP(World world) {
		List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, playerDepositArea, PrinterBlockEntity::canDepositXP);
		for (PlayerEntity player : players) {
			int amount = adjustAmount(player.isOnGround() ? 1 : 3);
			player.addExperience(-amount);
			depositXP(world, amount, player.isOnGround() ? 0.3f : 0.5f);
		}
	}

	public void lureXPOrbs(World world, BlockPos pos) {
		List<ExperienceOrbEntity> orbs = world.getEntitiesByClass(ExperienceOrbEntity.class, orbDepositArea, PrinterBlockEntity::canDepositXP);
		for (ExperienceOrbEntity orb : orbs) {
			((PrinterExperienceOrbEntity) orb).setPrinterTarget(pos);
		}
	}

	public static int getTickOffset(World world) {
		return (int) (world.getTime() % PRINTING_INTERVAL);
	}

	public void startPrinting(World world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state.with(PrinterBlock.PRINTING, true));
		// Add one to the tick offset since the sound will begin next tick
		startOffset = getTickOffset(world) + 1;
	}

	public void progressPrinting(World world, BlockPos pos) {
		progress++;
		if (getTickOffset(world) == startOffset) {
			world.playSound(null, pos, ModSoundEvents.PRINTING, SoundCategory.BLOCKS, 1.0f, 1.0f);
		}
	}

	public void finishPrinting(World world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state.with(PrinterBlock.PRINTING, false).with(PrinterBlock.FINISHED, true));
		xp = 0;
		startOffset = 0;
		progress = 0;
		if (world instanceof ServerWorld serverWorld) {
			PrinterBlock.stopPrintingSound(serverWorld, pos);
		}
	}

	public static void tick(World world, BlockPos pos, BlockState state, PrinterBlockEntity blockEntity) {
		if (PrinterBlock.canDepositXP(state)) {
			// Every two ticks, gather XP from players
			if (world.getTime() % 2 == 0) {
				blockEntity.gatherXP(world);
			}
			// Lure nearby vacant XP orbs
			blockEntity.lureXPOrbs(world, pos);
			// If the machine has enough XP, start the printing process
			if (!blockEntity.isEmpty() && blockEntity.xp >= blockEntity.requiredXP) {
				blockEntity.startPrinting(world, pos, state);
			}
		}
		if (state.get(PrinterBlock.PRINTING) && blockEntity.progress < blockEntity.requiredTicks) {
			blockEntity.progressPrinting(world, pos);
			if (blockEntity.progress == blockEntity.requiredTicks) {
				blockEntity.finishPrinting(world, pos, state);
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
