package kr.simtaehyeon.reality;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SurvivalHudPayload(int thirst, int fatigue, int bodyTemperatureTenths, int heat)
        implements CustomPacketPayload {
    public static final Type<SurvivalHudPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RealityMinecraft.MOD_ID, "survival_hud"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SurvivalHudPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SurvivalHudPayload::thirst,
                    ByteBufCodecs.INT, SurvivalHudPayload::fatigue,
                    ByteBufCodecs.INT, SurvivalHudPayload::bodyTemperatureTenths,
                    ByteBufCodecs.INT, SurvivalHudPayload::heat,
                    SurvivalHudPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
