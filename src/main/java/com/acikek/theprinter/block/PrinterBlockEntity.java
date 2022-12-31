package com.acikek.theprinter.block;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.advancement.PrinterUsedCriterion;
import com.acikek.theprinter.client.ThePrinterClient;
import com.acikek.theprinter.data.PrinterRule;
import com.acikek.theprinter.sound.ModSoundEvents;
import com.acikek.theprinter.util.ImplementedInventory;
import com.acikek.theprinter.util.PrinterExperienceOrbEntity;
import com.acikek.theprinter.world.ModGameRules;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class PrinterBlockEntity extends BlockEntity implements SidedInventory, ImplementedInventory {

	public static final int PRINTING_INTERVAL = 180;
	public static final int PAPER_MESSAGE_COUNT = 6;

	public static BlockEntityType<PrinterBlockEntity> BLOCK_ENTITY_TYPE;

	public Box playerDepositArea;
	public Box orbDepositArea;

	/**
	 * The printer's inventory.
	 * <p>
	 * The first stack will show up on the screen. It should be an exact copy of the input stack..<br>
	 * The second stack will render above the machine as it is printing the item. It is allowed to differ from the original stack.
	 * </p>
	 */
	public DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);

	public List<Map.Entry<Identifier, PrinterRule>> rules;
	public int xp = 0;
	public int requiredXP = -1;
	public int progress = 0;
	public int requiredTicks = -1;
	public int startOffset = 0;
	public int endOffset = -1;

	public PrinterBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BLOCK_ENTITY_TYPE, blockPos, blockState);
		Vec3d center = Vec3d.ofCenter(blockPos);
		playerDepositArea = Box.of(center, 3.5, 2.5, 3.5);
		orbDepositArea = Box.of(center, 6.0, 3.5, 6.0);
	}

	public int getMaxStackCount(ItemStack stack) {
		var sizeRules = PrinterRule.filterByType(rules, PrinterRule.Type.SIZE, stack);
		if (!sizeRules.isEmpty()) {
			return sizeRules.get(0).getValue().size.orElse(1);
		}
		return 1;
	}

	public boolean isEnabled(World world, ItemStack stack) {
		var enabledRules = PrinterRule.filterByType(rules, PrinterRule.Type.ENABLED, stack);
		boolean gameruleEnabled = world.isClient()
				? ThePrinterClient.printerEnabled
				: world.getGameRules().getBoolean(ModGameRules.PRINTER_ENABLED);
		return enabledRules.isEmpty()
				? gameruleEnabled
				: enabledRules.get(0).getValue().enabled.orElse(gameruleEnabled);
	}

	public boolean isXPRequired(World world) {
		return world.isClient()
				? ThePrinterClient.xpRequired
				: world.getGameRules().getBoolean(ModGameRules.XP_REQUIRED);
	}

	public ActionResult addItem(World world, PlayerEntity player, ItemStack handStack) {
		rules = PrinterRule.getMatchingRules(handStack);
		boolean enabled = isEnabled(world, handStack);
		if (!enabled) {
			return ActionResult.PASS;
		}
		try {
			requiredXP = Math.max(1, PrinterRule.getRequiredXP(rules, handStack));
		}
		catch (Exception e) {
			if (!world.isClient()) {
				ThePrinter.LOGGER.error("Error while calculating XP for '" + handStack + "': ", e);
			}
			return ActionResult.CONSUME;
		}
		requiredTicks = requiredXP * 3;
		if (!isXPRequired(world)) {
			requiredXP = 0;
		}
		int stackCount = Math.min(handStack.getCount(), getMaxStackCount(handStack));
		ItemStack copy = handStack.copy();
		copy.setCount(stackCount);
		setStack(0, copy);
		if (!player.isCreative()) {
			handStack.decrement(stackCount);
		}
		return ActionResult.SUCCESS;
	}

	public void tryDropXP(ServerWorld world, BlockPos pos) {
		if (xp > 0) {
			Vec3d xpPos = Vec3d.ofCenter(pos).add(0.0, 0.4, 0.0);
			ExperienceOrbEntity.spawn(world, xpPos, xp);
		}
	}

	public static void tryRemoveItems(NbtCompound nbt) {
		if (nbt != null && nbt.contains("Items")) {
			nbt.remove("Items");
		}
	}

	public static void modifyPrintingStack(World world, ItemStack stack) {
		if (stack.isOf(Items.PAPER)) {
			String key = "message.theprinter.paper_" + (world.random.nextInt(PAPER_MESSAGE_COUNT) + 1);
			stack.setCustomName(Text.translatable(key));
		}
		else if (stack.hasNbt()) {
			tryRemoveItems(stack.getNbt());
			tryRemoveItems(BlockItem.getBlockEntityNbtFromStack(stack));
		}
	}

	public boolean removePrintedItem(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		ItemStack stack = getStack(1);
		if (player != null && !stack.isEmpty()) {
			if (player instanceof ServerPlayerEntity serverPlayer) {
				PrinterUsedCriterion.INSTANCE.trigger(serverPlayer, requiredXP, requiredTicks, stack, stack.getRarity());
			}
			if (!player.isCreative()) {
				player.giveItemStack(stack);
			}
			setStack(1, ItemStack.EMPTY);
		}
		if (getStack(1).isEmpty()) {
			world.setBlockState(pos, state.with(PrinterBlock.FINISHED, false));
			xp = 0;
			if (player != null && !isXPRequired(world)) {
				removeItem(world, pos, player);
				return true;
			}
		}
		return false;
	}

	public void removeItem(World world, BlockPos pos, PlayerEntity player) {
		ItemStack removed = removeStack(0);
		if (!player.isCreative()) {
			player.giveItemStack(removed);
		}
		if (world instanceof ServerWorld serverWorld) {
			tryDropXP(serverWorld, pos);
		}
		rules = null;
		requiredXP = -1;
		requiredTicks = -1;
		xp = 0;
	}

	public DefaultedList<ItemStack> getActualInventory(BlockState state) {
		if (getStack(1).isEmpty() || state.get(PrinterBlock.FINISHED)) {
			return getItems();
		}
		return DefaultedList.copyOf(ItemStack.EMPTY, getStack(0));
	}

	public static boolean canDepositXP(PlayerEntity player) {
		return player.isSneaking() && (player.experienceLevel != 0 || player.experienceProgress > 0.0f);
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

	public void depositXP(World world, BlockPos pos, int amount, float soundChance) {
		xp += amount;
		if (world.random.nextFloat() > soundChance) {
			world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.5f, world.random.nextFloat() + 0.5f);
		}
		if (world instanceof ServerWorld serverWorld) {
			serverWorld.getChunkManager().markForUpdate(pos);
		}
	}

	/**
	 * Searches for players within the {@link PrinterBlockEntity#playerDepositArea} that qualify {@link PrinterBlockEntity#canDepositXP(PlayerEntity)}
	 * and takes some experience points from them, more if they're jumping.
	 */
	public void gatherXP(World world, BlockPos pos) {
		List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, playerDepositArea, PrinterBlockEntity::canDepositXP);
		for (PlayerEntity player : players) {
			int amount = adjustAmount(player.isOnGround() ? 1 : 3);
			player.addExperience(-amount);
			depositXP(world, pos, amount, player.isOnGround() ? 0.3f : 0.5f);
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
		ItemStack printingStack = getStack(0).copy();
		modifyPrintingStack(world, printingStack);
		setStack(1, printingStack);
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
				blockEntity.gatherXP(world, pos);
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
	public int[] getAvailableSlots(Direction side) {
		if (side == Direction.DOWN) {
			return new int[] { 1 };
		}
		return new int[0];
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
		return false;
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return dir == Direction.DOWN && getCachedState().get(PrinterBlock.FINISHED);
	}

	@Override
	public ItemStack removeStack(int slot, int count) {
		if (slot == 0) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = ImplementedInventory.super.removeStack(slot, count);
		removePrintedItem(world, pos, getCachedState(), null);
		return stack;
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		Inventories.readNbt(nbt, items);
		rules = PrinterRule.readNbt(nbt);
		xp = nbt.getInt("XP");
		requiredXP = nbt.getInt("RequiredXP");
		progress = nbt.getInt("Progress");
		requiredTicks = nbt.getInt("RequiredTicks");
		startOffset = nbt.getInt("StartOffset");
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		Inventories.writeNbt(nbt, items);
		if (rules != null) {
			PrinterRule.writeNbt(nbt, rules);
		}
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
	public NbtCompound toSyncedNbt() {
		return toNbt();
	}

	public static void register() {
		BLOCK_ENTITY_TYPE = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				ThePrinter.id("printer_block_entity"),
				FabricBlockEntityTypeBuilder.create(PrinterBlockEntity::new, PrinterBlock.INSTANCE).build()
		);
	}
}
