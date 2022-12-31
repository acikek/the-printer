package com.acikek.theprinter.block;

import com.acikek.theprinter.ThePrinter;
import com.acikek.theprinter.sound.ModSoundEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PrinterBlock extends HorizontalFacingBlock implements BlockEntityProvider {

	public static final BooleanProperty ON = BooleanProperty.of("on");
	public static final BooleanProperty PRINTING = BooleanProperty.of("printing");
	public static final BooleanProperty FINISHED = BooleanProperty.of("finished");

	public static final Settings SETTINGS = FabricBlockSettings.of(Material.METAL)
			.sounds(BlockSoundGroup.METAL)
			.strength(6.0f, 6.0f)
			.luminance(value -> value.get(PRINTING) ? 9 : value.get(ON) ? 6 : 1);

	public static final VoxelShape SHAPE = VoxelShapes.union(
			VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.75, 1.0),
			VoxelShapes.cuboid(0.125, 0.75, 0.125, 0.875, 0.875, 0.875),
			VoxelShapes.cuboid(0.0, 0.875, 0.0, 1.0, 1.0, 1.0)
	);

	public static final PrinterBlock INSTANCE = new PrinterBlock();

	public PrinterBlock() {
		super(SETTINGS);
		setDefaultState(getDefaultState()
				.with(FACING, Direction.NORTH)
				.with(ON, false)
				.with(PRINTING, false)
				.with(FINISHED, false));
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	public static boolean canDepositXP(BlockState state) {
		return state.isOf(INSTANCE) && state.get(ON) && !state.get(PRINTING) && !state.get(FINISHED);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (hand == Hand.MAIN_HAND && world.getBlockEntity(pos) instanceof PrinterBlockEntity blockEntity) {
			boolean on = state.get(ON);
			boolean printing = state.get(PRINTING);
			boolean finished = state.get(FINISHED);
			List<SoundEvent> events = new ArrayList<>();
			ItemStack handStack = player.getStackInHand(hand);
			if (!handStack.isEmpty() && !on) {
				ActionResult result = blockEntity.addItem(world, player, handStack);
				if (result != ActionResult.SUCCESS) {
					return result;
				}
				world.setBlockState(pos, state.with(ON, true));
				if (handStack.getItem() instanceof BlockItem blockItem) {
					world.playSound(null, pos, blockItem.getBlock().getDefaultState().getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0f, 1.3f);
				}
				events.add(ModSoundEvents.STARTUP);
			}
			else if (handStack.isEmpty() && !printing) {
				boolean remove = false;
				if (finished) {
					remove = blockEntity.removePrintedItem(world, pos, state, player);
					events.add(SoundEvents.ENTITY_ITEM_PICKUP);
				}
				if (remove || (on && !finished)) {
					world.setBlockState(pos, world.getBlockState(pos).with(ON, false));
					blockEntity.removeItem(world, pos, player);
					events.add(ModSoundEvents.SHUTDOWN);
				}
			}
			for (SoundEvent event : events) {
				world.playSound(null, pos, event, SoundCategory.BLOCKS, 1.0f, 1.0f);
			}
			if (!events.isEmpty()) {
				return ActionResult.SUCCESS;
			}
		}
		return ActionResult.PASS;
	}

	public static void stopPrintingSound(ServerWorld world, BlockPos pos) {
		StopSoundS2CPacket packet = new StopSoundS2CPacket(ModSoundEvents.PRINTING.getId(), SoundCategory.BLOCKS);
		for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
			player.networkHandler.sendPacket(packet);
		}
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (state.getBlock() != newState.getBlock() && world.getBlockEntity(pos) instanceof PrinterBlockEntity blockEntity) {
			ItemScatterer.spawn(world, pos, blockEntity.getActualInventory(state));
			if (world instanceof ServerWorld serverWorld) {
				blockEntity.tryDropXP(serverWorld, pos);
				if (state.get(PRINTING)) {
					stopPrintingSound(serverWorld, pos);
				}
			}
			world.removeBlockEntity(pos);
		}
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return super.getPlacementState(ctx).with(FACING, ctx.getPlayerFacing().getOpposite());
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PrinterBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return BlockWithEntity.checkType(type, PrinterBlockEntity.BLOCK_ENTITY_TYPE, PrinterBlockEntity::tick);
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(FACING, ON, PRINTING, FINISHED);
	}

	public static void register() {
		Identifier id = ThePrinter.id("the_printer");
		Registry.register(Registries.BLOCK, id, INSTANCE);
		BlockItem blockItem = new BlockItem(INSTANCE, new FabricItemSettings().rarity(Rarity.RARE));
		Registry.register(Registries.ITEM, id, blockItem);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.addAfter(Blocks.ENCHANTING_TABLE, blockItem);
		});
	}
}
