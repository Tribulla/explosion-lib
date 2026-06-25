package com.example.explosionlib.network;

import com.example.explosionlib.Constants;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExplodePayload(int mode, BlockPos pos, float yield, int flags) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ExplodePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "explode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExplodePayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExplodePayload::mode,
            BlockPos.STREAM_CODEC, ExplodePayload::pos,
            ByteBufCodecs.FLOAT, ExplodePayload::yield,
            ByteBufCodecs.VAR_INT, ExplodePayload::flags,
            ExplodePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
