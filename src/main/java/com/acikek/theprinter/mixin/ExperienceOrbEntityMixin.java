package com.acikek.theprinter.mixin;

import com.acikek.theprinter.block.PrinterBlock;
import com.acikek.theprinter.block.PrinterBlockEntity;
import com.acikek.theprinter.util.PrinterExperienceOrbEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ExperienceOrbEntity.class)
public abstract class ExperienceOrbEntityMixin implements PrinterExperienceOrbEntity {

	@Shadow
	private PlayerEntity target;

	@Shadow
	public abstract int getExperienceAmount();

	public BlockPos theprinter$printerTarget;
	public boolean theprinter$canDeposit = true;

	@Override
	public BlockPos getPrinterTarget() {
		return theprinter$printerTarget;
	}

	@Override
	public boolean canDeposit() {
		return theprinter$canDeposit;
	}

	@Override
	public void setPrinterTarget(BlockPos target) {
		theprinter$printerTarget = target;
	}

	@Inject(method = "tick", locals = LocalCapture.CAPTURE_FAILHARD,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ExperienceOrbEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"))
	private void theprinter$attract(CallbackInfo ci) {
		Entity entity = (Entity) (Object) this;
		if (theprinter$printerTarget == null) {
			return;
		}
		else if (!PrinterBlock.canDepositXP(entity.getWorld().getBlockState(theprinter$printerTarget))) {
			theprinter$printerTarget = null;
			return;
		}
		if (target != null) {
			target = null;
			return;
		}
		Vec3d difference = Vec3d.ofCenter(theprinter$printerTarget).subtract(entity.getPos());
		if (difference.length() < 1.2 && entity.getWorld().getBlockEntity(theprinter$printerTarget) instanceof PrinterBlockEntity blockEntity) {
			blockEntity.depositXP(entity.getWorld(), theprinter$printerTarget, blockEntity.capXPDepositAmount(getExperienceAmount()), 0.0f);
			entity.discard();
			return;
		}
		double len = difference.lengthSquared();
		if (len < 64.0) {
			double e = 1.0 - Math.sqrt(len) / 8.0;
			entity.setVelocity(entity.getVelocity().add(difference.normalize().multiply(e * e * 0.1)));
		}
	}

	@Redirect(method = "expensiveUpdate",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getClosestPlayer(Lnet/minecraft/entity/Entity;D)Lnet/minecraft/entity/player/PlayerEntity;"))
	private PlayerEntity theprinter$cancelPlayerTargeting(World instance, Entity entity, double v) {
		if (theprinter$printerTarget != null) {
			return null;
		}
		return instance.getClosestPlayer(entity, v);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("HEAD"))
	private void theprinter$writeNbt(NbtCompound nbt, CallbackInfo ci) {
		if (theprinter$printerTarget != null) {
			nbt.putLong("PrinterTarget", theprinter$printerTarget.asLong());
		}
		nbt.putBoolean("CanDeposit", theprinter$canDeposit);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
	private void theprinter$readNbt(NbtCompound nbt, CallbackInfo ci) {
		theprinter$printerTarget = nbt.contains("PrinterTarget") ? BlockPos.fromLong(nbt.getLong("PrinterTarget")) : null;
		theprinter$canDeposit = nbt.getBoolean("CanDeposit");
 	}
}
