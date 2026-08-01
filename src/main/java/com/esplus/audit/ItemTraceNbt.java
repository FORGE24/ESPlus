package com.esplus.audit;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ItemTraceNbt {
    public static final String TAG_TRACE = "esplus_trace";
    public static final String TAG_ORIGIN = "esplus_origin";

    private ItemTraceNbt() {
    }

    public static void stamp(ItemStack stack, String traceId, String originType) {
        if (stack.isEmpty() || traceId == null || traceId.isBlank()) {
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(TAG_TRACE, traceId);
        if (originType != null) {
            tag.putString(TAG_ORIGIN, originType);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static Optional<String> readTraceId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG_TRACE)) {
            return Optional.empty();
        }
        String value = tag.getString(TAG_TRACE);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public static String ensureTrace(ItemStack stack, AuditService audit, String itemId, String originType, UUID actor, String actorName, String detail) {
        Optional<String> existing = readTraceId(stack);
        if (existing.isPresent()) {
            return existing.get();
        }
        String traceId = audit.createItemTrace(itemId, originType, actor, actorName, detail);
        stamp(stack, traceId, originType);
        return traceId;
    }
}
