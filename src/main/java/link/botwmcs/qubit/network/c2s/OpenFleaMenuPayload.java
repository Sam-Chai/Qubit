package link.botwmcs.qubit.network.c2s;

import link.botwmcs.qubit.Qubit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenFleaMenuPayload(int page) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Qubit.MODID, "open_flea");
    public static final Type<OpenFleaMenuPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, OpenFleaMenuPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    OpenFleaMenuPayload::page,
                    OpenFleaMenuPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
