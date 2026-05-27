package backpackarsenal.network;

import backpackarsenal.BackpackArsenalMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * カスタムネットワークチャンネル。
 *
 * 現状は backpack 内 voltaic_blade を R で引き抜くための
 * {@link DrawFromBackpackPacket} 1 種類のみ。
 */
public class BackpackArsenalNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(BackpackArsenalMod.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(
            id++,
            DrawFromBackpackPacket.class,
            DrawFromBackpackPacket::encode,
            DrawFromBackpackPacket::decode,
            DrawFromBackpackPacket::handle
        );
        CHANNEL.registerMessage(
            id++,
            SheathToBackpackPacket.class,
            SheathToBackpackPacket::encode,
            SheathToBackpackPacket::decode,
            SheathToBackpackPacket::handle
        );
    }
}
