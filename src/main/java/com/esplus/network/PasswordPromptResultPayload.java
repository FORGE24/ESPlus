package com.esplus.network;

import java.util.UUID;

import com.esplus.ESPlus;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server password result. Password travels only on the game connection,
 * never in chat or process argv.
 */
public record PasswordPromptResultPayload(
        UUID requestId,
        String purpose,
        boolean canceled,
        String password
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PasswordPromptResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ESPlus.MODID, "pw_result"));

    public static final StreamCodec<FriendlyByteBuf, PasswordPromptResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.requestId());
                buf.writeUtf(value.purpose(), 64);
                buf.writeBoolean(value.canceled());
                buf.writeUtf(value.password() == null ? "" : value.password(), 512);
            },
            buf -> new PasswordPromptResultPayload(
                    buf.readUUID(),
                    buf.readUtf(64),
                    buf.readBoolean(),
                    buf.readUtf(512)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
