package com.esplus.network;

import java.util.UUID;

import com.esplus.ESPlus;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenPasswordPromptPayload(
        UUID requestId,
        String purpose,
        String title,
        String prompt,
        boolean confirm
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenPasswordPromptPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ESPlus.MODID, "open_pw"));

    public static final StreamCodec<FriendlyByteBuf, OpenPasswordPromptPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.requestId());
                buf.writeUtf(value.purpose(), 64);
                buf.writeUtf(value.title(), 128);
                buf.writeUtf(value.prompt(), 256);
                buf.writeBoolean(value.confirm());
            },
            buf -> new OpenPasswordPromptPayload(
                    buf.readUUID(),
                    buf.readUtf(64),
                    buf.readUtf(128),
                    buf.readUtf(256),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
