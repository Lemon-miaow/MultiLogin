package moe.caa.multilogin.core.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import lombok.SneakyThrows;
import moe.caa.multilogin.api.plugin.IPlayer;
import moe.caa.multilogin.api.plugin.ISender;
import moe.caa.multilogin.api.util.Pair;
import moe.caa.multilogin.core.command.CommandHandler;
import moe.caa.multilogin.core.command.Permissions;
import moe.caa.multilogin.core.command.argument.StringArgumentType;
import moe.caa.multilogin.core.configuration.yggdrasil.YggdrasilServiceConfig;

import java.util.*;
import java.util.stream.Collectors;

public class RootCommand {
    private final CommandHandler handler;
    private final MWhitelistCommand mWhitelistCommand;

    public RootCommand(CommandHandler handler) {
        this.handler = handler;
        this.mWhitelistCommand = new MWhitelistCommand(handler);
    }

    public LiteralArgumentBuilder<ISender> register(LiteralArgumentBuilder<ISender> literalArgumentBuilder) {
        return literalArgumentBuilder.then(handler.literal("reload")
                        .requires(sender -> sender.hasPermission(Permissions.COMMAND_MULTI_LOGIN_RELOAD))
                        .executes(this::executeReload))
                .then(handler.literal("eraseUsername")
                        .requires(sender -> sender.hasPermission(Permissions.COMMAND_MULTI_LOGIN_ERASE_USERNAME))
                        .then(handler.argument("username", StringArgumentType.string())
                                .executes(this::executeEraseUsername)))
                .then(handler.literal("list")
                        .requires(sender -> sender.hasPermission(Permissions.COMMAND_MULTI_LOGIN_LIST))
                        .executes(this::executeList))
                .then(mWhitelistCommand.register(handler.literal("whitelist")));
    }

    private int executeList(CommandContext<ISender> context) {
        Set<IPlayer> onlinePlayers = handler.getCore().getPlugin().getRunServer().getPlayerManager().getOnlinePlayers();
        if (onlinePlayers.size() == 0) {
            context.getSource().sendMessagePL(handler.getCore().getLanguageHandler().getMessage("command_message_list_no_player"));
            return 0;
        }
        Map<Integer, List<String>> identifiedPlayerMap = new HashMap<>();
        for (IPlayer player : onlinePlayers) {
            Pair<UUID, Integer> profile = handler.getCore().getCache().getPlayerOnlineProfile(player.getUniqueId());
            int yid = -1;
            if (profile != null) {
                yid = profile.getValue2();
            }
            List<String> list = identifiedPlayerMap.getOrDefault(yid, new ArrayList<>());
            list.add(player.getName());
            identifiedPlayerMap.put(yid, list);
        }

        String message = handler.getCore().getLanguageHandler().getMessage("command_message_list",
                new Pair<>("list", identifiedPlayerMap.entrySet().stream().map(entry -> {
                    // 获得 Ygg Name
                    String yggName;
                    if (entry.getKey() == -1) {
                        // 黑户
                        yggName = handler.getCore().getLanguageHandler().getMessage("command_message_list_unidentified_entry_name");
                    } else {
                        YggdrasilServiceConfig serviceConfig = handler.getCore().getPluginConfig().getIdMap().get(entry.getKey());
                        if (serviceConfig == null) {
                            // 不明的
                            yggName = handler.getCore().getLanguageHandler().getMessage("command_message_list_unknown_entry_name");
                        } else {
                            yggName = serviceConfig.getName();
                        }
                    }

                    // 玩家列表
                    String playerListString = entry.getValue().stream()
                            .map(s -> handler.getCore().getLanguageHandler().getMessage("command_message_list_player_entry",
                                    new Pair<>("player_name", s)
                            ))
                            .collect(Collectors.joining(
                                            handler.getCore().getLanguageHandler().getMessage("command_message_list_player_delimiter")
                                    )
                            );

                    return handler.getCore().getLanguageHandler().getMessage("command_message_list_entry",
                            new Pair<>("yggdrasil_name", yggName),
                            new Pair<>("yggdrasil_id", entry.getKey()),
                            new Pair<>("player_count", entry.getValue().size()),
                            new Pair<>("player_list", playerListString)
                    );
                }).collect(Collectors.joining(handler.getCore().getLanguageHandler().getMessage("command_message_list_delimiter")))),
                new Pair<>("count", onlinePlayers.size())
        );
        context.getSource().sendMessagePL(message);
        return 0;
    }

    @SneakyThrows
    private int executeEraseUsername(CommandContext<ISender> context) {
        String string = StringArgumentType.getString(context, "username").toLowerCase(Locale.ROOT);
        int i = handler.getCore().getSqlManager().getInGameProfileTable().eraseUsername(string);
        // 更新前先踢一下
        String kickMsg = handler.getCore().getLanguageHandler().getMessage("in_game_username_occupy",
                new Pair<>("current_username", string));
        // 踢出
        for (IPlayer player : handler.getCore().getPlugin().getRunServer().getPlayerManager().getPlayers(string)) {
            player.kickPlayer(kickMsg);
        }
        if (i == 0) {
            context.getSource().sendMessagePL(handler.getCore().getLanguageHandler().getMessage("command_message_erase_username_none",
                    new Pair<>("current_username", string)
            ));
        } else {
            context.getSource().sendMessagePL(handler.getCore().getLanguageHandler().getMessage("command_message_erase_username_done",
                    new Pair<>("current_username", string)
            ));
        }
        return 0;
    }

    @SneakyThrows
    private int executeReload(CommandContext<ISender> context) {
        handler.getCore().reload();
        context.getSource().sendMessagePL(handler.getCore().getLanguageHandler().getMessage("command_message_reloaded"));
        return 0;
    }
}
