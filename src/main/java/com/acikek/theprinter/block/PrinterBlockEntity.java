package com.acikek.theprinter.block;

import com.acikek.datacriteria.api.DataCriteriaAPI;
import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.data.PrinterRule;
import com.acikek.theprinter.data.PrinterRules;
import com.acikek.theprinter.data.ModTags;
import com.acikek.theprinter.sound.ModSoundEvents;
import com.acikek.theprinter.util.PrinterExperienceOrbEntity;
import com.acikek.theprinter.world.ModGameRules;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Rarity;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PrinterBlockEntity extends BlockEntity {

	public static final int PRINTING_INTERVAL = 180;
	public static final int PAPER_MESSAGE_COUNT = 6;

	public static BlockEntityType<PrinterBlockEntity> BLOCK_ENTITY_TYPE;

	public Box playerDepositArea;
	public Box orbDepositArea;

	public PrinterBlockStorage storage = new PrinterBlockStorage(this);

	public PrinterRules rules;
	public int xp = 0;
	public int requiredXP = -1;
	public int progress = 0;
	public int requiredTicks = -1;
	public int startOffset = 0;
	public int endOffset = -1;

	/**
	 * Initializes {@link PrinterBlockEntity#playerDepositArea} and {@link PrinterBlockEntity#orbDepositArea} based on the printer block's position.
	 */
	public PrinterBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BLOCK_ENTITY_TYPE, blockPos, blockState);
		Vec3d center = Vec3d.ofCenter(blockPos);
		playerDepositArea = Box.of(center, 3.5, 2.5, 3.5);
		orbDepositArea = Box.of(center, 6.0, 3.5, 6.0);
	}

	public static void triggerPrinterUsed(ServerPlayerEntity player, int xp, int time, ItemStack stack, Rarity rarity) {
		DataCriteriaAPI.trigger(ThePrinter.id("printer_used"), player, xp, time, stack, rarity);
	}

	/**
	 * Attempts to insert a stack into the printing slot.
	 * <p>
	 * Fails if the stack cannot be inserted (determined by {@link PrinterRules#canBeInserted(World, ItemStack)})
	 * or if the required XP calculation ({@link PrinterRules#getRequiredXP(ItemStack)}) errors.
	 * </p>
	 */
	public ActionResult addItem(World world, PlayerEntity player, ItemStack handStack) {
		rules = PrinterRule.getMatchingRules(handStack);
		boolean enabled = rules.canBeInserted(world, handStack);
		if (!enabled) {
			return ActionResult.PASS;
		}
		try {
			requiredXP = Math.max(1, rules.getRequiredXP(handStack));
		}
		catch (Exception e) {
			if (!world.isClient()) {
				ThePrinter.LOGGER.error("Error while calculating XP for '" + handStack + "': ", e);
			}
			return ActionResult.CONSUME;
		}
		requiredTicks = requiredXP * 3;
		if (!ModGameRules.isXPRequired(world)) {
			requiredXP = 0;
		}
		int stackCount = Math.min(handStack.getCount(), rules.getMaxInsertCount(handStack));
		ItemStack copy = handStack.copy();
		copy.setCount(stackCount);
		getItems().set(0, copy);
		if (!player.isCreative()) {
			handStack.decrement(stackCount);
		}
		return ActionResult.SUCCESS;
	}

	/**
	 * If any XP is stored in the {@link PrinterBlockEntity#xp} value, drops it at the printer's position.
	 */
	public void tryDropXP(ServerWorld world, BlockPos pos) {
		if (xp > 0) {
			Vec3d xpPos = Vec3d.ofCenter(pos).add(0.0, 0.4, 0.0);
			ExperienceOrbEntity.spawn(world, xpPos, xp);
		}
	}

	/**
	 * Attempts to sanitize an NBT compound to remove any internally held items.
	 */
	public static void tryRemoveContainedItems(NbtCompound nbt) {
		if (nbt != null && nbt.contains("Items")) {
			nbt.remove("Items");
		}
	}

	/**
	 * Modifiea a stack's NBT in-place to prepare it for the printing output.
	 */
	public static void modifyPrintingStack(World world, ItemStack stack) {
		if (stack.isOf(Items.PAPER)) {
			String key = "message.theprinter.paper_" + (world.random.nextInt(PAPER_MESSAGE_COUNT) + 1);
			stack.setCustomName(Text.translatable(key));
		}
		else if (stack.hasNbt()) {
			tryRemoveContainedItems(stack.getNbt());
			tryRemoveContainedItems(BlockItem.getBlockEntityNbtFromStack(stack));
		}
	}

	public void setFinished(World world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state.with(PrinterBlock.FINISHED, false));
		xp = 0;
	}

	/**
	 * @param player if {@code null}, can be used as a callback for non-player interactions
	 * @return whether the printed stack has been fully removed
	 */
	public boolean removePrintedItem(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		ItemStack stack = getItems().get(1);
		if (player instanceof ServerPlayerEntity serverPlayer) {
			triggerPrinterUsed(serverPlayer, requiredXP, requiredTicks, stack, stack.getRarity());
		}
		if (!player.isCreative()) {
			player.giveItemStack(stack);
		}
		getItems().set(1, ItemStack.EMPTY);
		setFinished(world, pos, state);
		if (!ModGameRules.isXPRequired(world)) {
			removeItem(world, pos, player);
			return true;
		}
		return false;
	}

	/**
	 * Removes the item being printed and resets all values corresponding to it.
	 */
	public void removeItem(World world, BlockPos pos, PlayerEntity player) {
		ItemStack removed = Inventories.removeStack(getItems(), 0);
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

	/**
	 * @return only the inventory of the first stack and not the façade stack used for the printing display
	 */
	public DefaultedList<ItemStack> getMaterialInventory(BlockState state) {
		if (getItems().get(1).isEmpty() || state.get(PrinterBlock.FINISHED)) {
			return getItems();
		}
		return DefaultedList.copyOf(ItemStack.EMPTY, getItems().get(0));
	}

	/**
	 * @param entity the entity trying to deposit XP. If this entity is a {@link PlayerEntity}, checks if they are
	 *               sneaking and that they have sufficient experience. If this entity is a {@link ExperienceOrbEntity},
	 *               checks that its {@link PrinterExperienceOrbEntity} has a printer target and it is able to deposit.
	 */
	public static boolean canDepositXP(Entity entity) {
		if (entity instanceof PlayerEntity player) {
			return player.isSneaking() && (player.experienceLevel != 0 || player.experienceProgress > 0.0f);
		}
		else if (entity instanceof ExperienceOrbEntity orb) {
			PrinterExperienceOrbEntity printerOrb = (PrinterExperienceOrbEntity) orb;
			return printerOrb.canDeposit() && printerOrb.getPrinterTarget() == null;
		}
		return false;
	}

	public int capXPDepositAmount(int amount) {
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
			serverWorld.updateNeighbors(pos, PrinterBlock.INSTANCE);
		}
	}

	/**
	 * Searches for players within the {@link PrinterBlockEntity#playerDepositArea} that qualify {@link PrinterBlockEntity#canDepositXP(Entity)}
	 * and takes some experience points from them, more if they're jumping.
	 */
	public void gatherXP(World world, BlockPos pos) {
		List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, playerDepositArea, PrinterBlockEntity::canDepositXP);
		for (PlayerEntity player : players) {
			int amount = capXPDepositAmount(player.isOnGround() ? 1 : 3);
			player.addExperience(-amount);
			depositXP(world, pos, amount, player.isOnGround() ? 0.3f : 0.5f);
		}
	}

	/**
	 * Searches for XP orbs within the {@link PrinterBlockEntity#orbDepositArea} that wuality {@link PrinterBlockEntity#canDepositXP(Entity)}
	 * and sets their target to the printer's block position.
	 */
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
		ItemStack printingStack = getItems().get(0).copy();
		if (printingStack.isIn(ModTags.LOSES_NBT)) {
			printingStack.setNbt(null);
		}
		modifyPrintingStack(world, printingStack);
		getItems().set(1, printingStack);
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
			if (!blockEntity.getItems().isEmpty() && blockEntity.xp >= blockEntity.requiredXP) {
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

	public DefaultedList<ItemStack> getItems() {
		return storage.items;
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		Inventories.readNbt(nbt, getItems());
		rules = PrinterRules.readNbt(nbt);
		xp = nbt.getInt("XP");
		requiredXP = nbt.getInt("RequiredXP");
		progress = nbt.getInt("Progress");
		requiredTicks = nbt.getInt("RequiredTicks");
		startOffset = nbt.getInt("StartOffset");
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		Inventories.writeNbt(nbt, getItems());
		if (rules != null) {
			rules.writeNbt(nbt);
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
		PrinterBlockStorage.register();
		Registry.register(DataCriteriaAPI.getRegistry(), ThePrinter.id("rarity"), DataCriteriaAPI.createEnum(Rarity.class));
	}
}
