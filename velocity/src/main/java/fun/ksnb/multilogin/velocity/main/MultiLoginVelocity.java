package fun.ksnb.multilogin.velocity.main;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.StateRegistry;
import fun.ksnb.multilogin.velocity.auth.MultiLoginEncryptionResponse;
import fun.ksnb.multilogin.velocity.impl.VelocityServer;
import fun.ksnb.multilogin.velocity.impl.VelocityUserLogin;
import fun.ksnb.multilogin.velocity.loader.main.BaseVelocityPlugin;
import fun.ksnb.multilogin.velocity.loader.main.MultiLoginVelocityLoader;
import io.netty.util.collection.IntObjectMap;
import lombok.Getter;
import moe.caa.multilogin.core.impl.IPlugin;
import moe.caa.multilogin.core.impl.IServer;
import moe.caa.multilogin.core.logger.LoggerLevel;
import moe.caa.multilogin.core.main.MultiCore;
import moe.caa.multilogin.core.util.ReflectUtil;
import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Supplier;

public class MultiLoginVelocity extends BaseVelocityPlugin implements IPlugin {
    @Getter
    private static MultiLoginVelocity instance;

    @Getter
    private final MultiCore core;
    private final IServer runServer;

    private final ProxyServer server;
    private final Logger logger;
    private final File dataFolder;


    public MultiLoginVelocity(ProxyServer server, Logger logger, File dataFolder) {
        this.server = server;
        this.logger = logger;
        this.dataFolder = dataFolder;
        runServer = new VelocityServer(server);
        instance = this;
        core = new MultiCore(this);
    }

    @Override
    public void initService() throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException {
        MultiLoginEncryptionResponse.init();
        VelocityUserLogin.init();
//        要替换的方向
        StateRegistry.PacketRegistry toReplace = StateRegistry.LOGIN.serverbound;

        Field field_versions = ReflectUtil.handleAccessible(StateRegistry.PacketRegistry.class.getDeclaredField("versions"), true);
//        获取注册Map
        Map<ProtocolVersion, StateRegistry.PacketRegistry.ProtocolRegistry> map = (Map<ProtocolVersion, StateRegistry.PacketRegistry.ProtocolRegistry>) field_versions.get(toReplace);
        for (StateRegistry.PacketRegistry.ProtocolRegistry protocolRegistry : map.values()) {
//            获取packetIdToSupplier Map
            Field field_packetIdToSupplier = ReflectUtil.handleAccessible(StateRegistry.PacketRegistry.ProtocolRegistry.class.getDeclaredField("packetIdToSupplier"), true);
            IntObjectMap<Supplier<? extends MinecraftPacket>> packetIdToSupplier = (IntObjectMap<Supplier<? extends MinecraftPacket>>) field_packetIdToSupplier.get(protocolRegistry);
//            至此 替换完成
            packetIdToSupplier.put(0x01, MultiLoginEncryptionResponse::new);
        }
    }


    @Override
    public void initOther() {
        CommandManager commandManager = server.getCommandManager();
        CommandMeta meta = commandManager.metaBuilder("multilogin")
                .aliases("whitelist")
                .build();
        commandManager.register(meta, new MultiLoginCommand(this));


    }

    @Override
    public void loggerLog(LoggerLevel level, String message, Throwable throwable) {
        if (level == LoggerLevel.ERROR) logger.error(message, throwable);
        else if (level == LoggerLevel.WARN) logger.warn(message, throwable);
        else if (level == LoggerLevel.INFO) logger.info(message, throwable);
        else if (level == LoggerLevel.DEBUG) {
        } else logger.info(message, throwable);
    }

    @Override
    public IServer getRunServer() {
        return runServer;
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public String getPluginVersion() {
        return getServer().getPluginManager().getPlugin("MultiLogin")
                .map(PluginContainer::getDescription)
                .map(PluginDescription::getVersion)
                .get().get();
    }

    public ProxyServer getServer() {
        return server;
    }

    @Override
    public void onInitialize() {
        if (!core.init()) {
//            启动失败关闭
            core.disable();
        }
    }

    @Override
    public void onDisable() {
        core.disable();
    }
}
