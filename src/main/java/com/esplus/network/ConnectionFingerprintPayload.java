package com.esplus.network;

import com.esplus.ESPlus;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ConnectionFingerprintPayload(
        String hwid,
        String zoneId,
        String probeToken,
        String mode
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConnectionFingerprintPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ESPlus.MODID, "connection_fingerprint"));

    public static final StreamCodec<FriendlyByteBuf, ConnectionFingerprintPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUtf(value.hwid() == null ? "" : value.hwid(), 256);
                buf.writeUtf(value.zoneId() == null ? "" : value.zoneId(), 128);
                buf.writeUtf(value.probeToken() == null ? "" : value.probeToken(), 128);
                buf.writeUtf(value.mode() == null ? "" : value.mode(), 64);
            },
            buf -> new ConnectionFingerprintPayload(
                    buf.readUtf(256),
                    buf.readUtf(128),
                    buf.readUtf(128),
                    buf.readUtf(64)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
