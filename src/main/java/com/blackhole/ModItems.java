package com.blackhole;

import com.blackhole.item.BlackHoleItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
    private ModItems() {}

    public static final Item BLACK_HOLE = Registry.register(Registries.ITEM, BlackHoleMod.id("black_hole"),
            new BlackHoleItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(BLACK_HOLE));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(BLACK_HOLE));
    }
}
