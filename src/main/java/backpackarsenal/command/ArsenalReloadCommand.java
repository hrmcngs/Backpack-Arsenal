package backpackarsenal.command;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalBackpackConfig;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * `/backpack_arsenal reload` で config/backpack_arsenal.json を再読み込み。
 * OP 権限が必要 (level 2)。
 *
 * 既に開いている GUI には反映されない (再度開き直す必要あり)。
 * Item の slot 数 (inventorySlots, upgradeSlots) は新規生成 backpack のみに影響。
 */
public class ArsenalReloadCommand {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            (LiteralArgumentBuilder<CommandSourceStack>)
            Commands.literal("backpack_arsenal")
                .then(Commands.literal("reload")
                    .requires(s -> s.hasPermission(2))
                    .executes(ctx -> {
                        ArsenalBackpackConfig.reload();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "[backpack_arsenal] Config reloaded: "
                                + "inv=" + ArsenalBackpackConfig.inventorySlots
                                + " upg=" + ArsenalBackpackConfig.upgradeSlots
                                + " chargeSlot=(" + ArsenalBackpackConfig.chargeSlotX
                                + "," + ArsenalBackpackConfig.chargeSlotY + ")"
                                + " — 既に開いている GUI は再オープン必要"
                        ), true);
                        BackpackArsenalMod.LOGGER.info(
                            "[{}] Config reloaded by {}",
                            BackpackArsenalMod.MODID,
                            ctx.getSource().getTextName());
                        return 1;
                    })
                )
        );
    }
}
