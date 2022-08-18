package com.acikek.theprinter.advancement;

import com.acikek.theprinter.ThePrinter;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Rarity;
import org.apache.commons.lang3.EnumUtils;

public class PrinterUsedCriterion extends AbstractCriterion<PrinterUsedCriterion.Conditions> {

	public static final PrinterUsedCriterion INSTANCE = new PrinterUsedCriterion();

	public static final Identifier ID = ThePrinter.id("printer_used");

	@Override
	protected Conditions conditionsFromJson(JsonObject obj, EntityPredicate.Extended playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
		NumberRange.IntRange xp = NumberRange.IntRange.fromJson(obj.get("xp"));
		NumberRange.IntRange time = NumberRange.IntRange.fromJson(obj.get("time"));
		ItemPredicate item = ItemPredicate.fromJson(obj.get("item"));
		Rarity rarity = obj.has("rarity") ? EnumUtils.getEnumIgnoreCase(Rarity.class, JsonHelper.asString(obj.get("rarity"), "rarity")) : Rarity.COMMON;
		return new Conditions(playerPredicate, xp, time, item, rarity);
	}

	public void trigger(ServerPlayerEntity player, int xp, int time, ItemStack stack, Rarity rarity) {
		trigger(player, conditions -> conditions.matches(xp, time, stack, rarity));
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	public static class Conditions extends AbstractCriterionConditions {

		public NumberRange.IntRange xp;
		public NumberRange.IntRange time;
		public ItemPredicate item;
		public Rarity rarity;

		public Conditions(EntityPredicate.Extended extended, NumberRange.IntRange xp, NumberRange.IntRange time, ItemPredicate item, Rarity rarity) {
			super(ID, extended);
			this.xp = xp;
			this.time = time;
			this.item = item;
			this.rarity = rarity;
		}

		public boolean matches(int xp, int time, ItemStack stack, Rarity rarity) {
			return this.xp.test(xp)
					&& this.time.test(time)
					&& item.test(stack)
					&& this.rarity.compareTo(rarity) >= 0;
		}

		@Override
		public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
			JsonObject obj = super.toJson(predicateSerializer);
			obj.add("xp", xp.toJson());
			obj.add("time", time.toJson());
			obj.add("item", item.toJson());
			obj.add("rarity", new JsonPrimitive(rarity.name().toLowerCase()));
			return obj;
		}
	}

	public static void register() {
		Criteria.register(INSTANCE);
	}
}
