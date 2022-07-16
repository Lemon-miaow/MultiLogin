package moe.caa.multilogin.core.command.commands;

import com.mojang.brigadier.context.CommandContext;
import lombok.SneakyThrows;
import moe.caa.multilogin.api.plugin.ISender;
import moe.caa.multilogin.api.util.Pair;
import moe.caa.multilogin.core.command.CommandHandler;
import moe.caa.multilogin.core.command.Permissions;
import moe.caa.multilogin.core.command.argument.StringArgumentType;

public class MultiLoginCommand {
    private final CommandHandler handler;

    public MultiLoginCommand(CommandHandler handler) {
        this.handler = handler;
    }

    public void register() {
        handler.getDispatcher().register(
                handler.literal("multilogin")
                        .then(handler.literal("reload")
                                .requires(sender -> sender.hasPermission(Permissions.COMMAND_MULTI_LOGIN_RELOAD))
                                .executes(this::executeReload)
                        ).then(handler.literal("eraseUsername")
                                .requires(sender -> sender.hasPermission(Permissions.COMMAND_MULTI_LOGIN_ERASE_USERNAME))
                                .then(handler.argument("username", StringArgumentType.string())
                                        .executes(this::executeEraseUsername)
                                )
                        ).then(handler.literal("whitelist")

                        ).then(handler.literal("query")

                        ).then(handler.literal("merge")

                        ).then(handler.literal("confirm")

                        )
        );

    }

    @SneakyThrows
    private int executeEraseUsername(CommandContext<ISender> context) {
        String string = StringArgumentType.getString(context, "username");
        int i = handler.getCore().getSqlManager().getInGameProfileTable().eraseUsername(string);
        if (i == 0) {
            context.getSource().sendMessage(handler.getCore().getLanguageHandler().getMessage("command_message_erase_username_none",
                    new Pair<>("current_username", string)
            ));
        } else {
            context.getSource().sendMessage(handler.getCore().getLanguageHandler().getMessage("command_message_erase_username_done",
                    new Pair<>("current_username", string)
            ));
        }
        return 0;
    }

    @SneakyThrows
    private int executeReload(CommandContext<ISender> context) {
        handler.getCore().getPluginConfig().reload();
        context.getSource().sendMessage(handler.getCore().getLanguageHandler().getMessage("command_message_reloaded"));
        return 0;
    }
}
