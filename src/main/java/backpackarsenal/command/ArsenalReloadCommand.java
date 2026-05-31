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
                        sendStatus(ctx.getSource(), "reloaded from disk");
                        return 1;
                    })
                )
                .then(Commands.literal("default")
                    .requires(s -> s.hasPermission(2))
                    .executes(ctx -> {
                        ArsenalBackpackConfig.inventorySlots = ArsenalBackpackConfig.DEFAULT_INVENTORY_SLOTS;
                        ArsenalBackpackConfig.upgradeSlots   = ArsenalBackpackConfig.DEFAULT_UPGRADE_SLOTS;
                        ArsenalBackpackConfig.save();
                        sendStatus(ctx.getSource(), "reset to default and saved");
                        return 1;
                    })
                )
                .then(Commands.literal("show")
                    .executes(ctx -> {
                        sendStatus(ctx.getSource(), "current in-memory values");
                        return 1;
                    })
                )
        );
    }

    private static void sendStatus(CommandSourceStack src, String prefix) {
        src.sendSuccess(() -> Component.literal(
            "[backpack_arsenal] " + prefix + ": "
                + "inv=" + ArsenalBackpackConfig.inventorySlots
                + " upg=" + ArsenalBackpackConfig.upgradeSlots
        ), true);
        BackpackArsenalMod.LOGGER.info(
            "[{}] {} by {}", BackpackArsenalMod.MODID, prefix, src.getTextName());
    }
}
