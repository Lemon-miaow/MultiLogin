/*
 * Copyleft (c) 2021 ksqeib,CaaMoe. All rights reserved.
 * @author  ksqeib <ksqeib@dalao.ink> <https://github.com/ksqeib445>
 * @author  CaaMoe <miaolio@qq.com> <https://github.com/CaaMoe>
 * @github  https://github.com/CaaMoe/MultiLogin
 *
 * moe.caa.multilogin.core.library.LibraryHandler
 *
 * Use of this source code is governed by the GPLv3 license that can be found via the following link.
 * https://github.com/CaaMoe/MultiLogin/blob/master/LICENSE
 */

package moe.caa.multilogin.core.library;

import moe.caa.multilogin.core.exception.LoadLibraryFailedException;
import moe.caa.multilogin.core.language.LanguageKeys;
import moe.caa.multilogin.core.logger.LoggerLevel;
import moe.caa.multilogin.core.main.MultiCore;
import moe.caa.multilogin.core.util.FileUtil;
import moe.caa.multilogin.core.util.HttpUtil;
import moe.caa.multilogin.core.util.ReflectUtil;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class LibraryHandler {
    private final MultiCore core;
    private Map<String, String> NEED_LIBRARIES = new LinkedHashMap<>();
    //    使用一个线程池 防止少数服务端不给开线程造成无法启动
    private ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    public LibraryHandler(MultiCore core) {
        this.core = core;
    }

    private String genUrl(String name) {
//     例子 方便生成URL   https://repo1.maven.org/maven2/com/zaxxer/HikariCP/4.0.2/HikariCP-4.0.2.jar
        String[] split = name.split(" ");
        StringBuilder sb = new StringBuilder("https://repo1.maven.org/maven2/");
        String[] packetSplit = split[0].split("\\.");
        for (String get : packetSplit) {
            sb.append(get);
            sb.append('/');
        }
        sb.append(split[1]);
        sb.append('/');
        sb.append(split[2]);
        sb.append('/');
        sb.append(split[1]);
        sb.append('-');
        sb.append(split[2]);
        sb.append(".jar");
        return sb.toString();
    }

    private String genJarName(String name) {
        String[] split = name.split(" ");
        return split[1] +
                '-' +
                split[2] +
                ".jar";
    }

    public void init() throws Throwable {
        check();
        File libFolder = new File(core.plugin.getDataFolder(), "libraries");
        FileUtil.createNewFileOrFolder(libFolder, true);
        download(libFolder);
        load(libFolder);
        check();
        String args = NEED_LIBRARIES.keySet().stream().map(this::genJarName).collect(Collectors.joining(", "));
        if (args.length() == 0) return;
//        回收对象
        NEED_LIBRARIES = null;
        throw new LoadLibraryFailedException(LanguageKeys.LIBRARY_LOAD_FAILED.getMessage(core, args));
    }

    private void load(File libFolder) throws Throwable {
        ClassLoader classLoader = core.plugin.getClass().getClassLoader();
        MethodHandle handle = ReflectUtil.super_lookup.unreflect(ReflectUtil.getMethodWithParent(classLoader.getClass(),"addURL",true,URL.class));
        for (Map.Entry<String, String> library : NEED_LIBRARIES.entrySet()) {
            String jarName = genJarName(library.getKey());
            handle.invoke(classLoader, new File(libFolder, jarName).toURI().toURL());
            if (ReflectUtil.getClass(library.getValue()) != null) {
                core.getLogger().log(LoggerLevel.INFO, LanguageKeys.LIBRARY_LOADED.getMessage(core, jarName));
            }
        }
    }

    private void download(File libFolder) {
        for (Map.Entry<String, String> library : NEED_LIBRARIES.entrySet()) {
            String jarName = genJarName(library.getKey());
            File file = new File(libFolder, jarName);
            if (file.exists()) {
                continue;
            }
            String url = genUrl(library.getKey());
            EXECUTOR_SERVICE.submit(new DownloadThread(url, file, jarName));
        }
        EXECUTOR_SERVICE.shutdown();
        while (!EXECUTOR_SERVICE.isTerminated()) ;
//        回收对象
        EXECUTOR_SERVICE = null;
    }

    private void check() {
        NEED_LIBRARIES.clear();

        if (ReflectUtil.getClass("org.slf4j.LoggerFactory") == null) {
            NEED_LIBRARIES.put("org.slf4j slf4j-api 1.7.31", "org.slf4j.LoggerFactory");
        }

        if (ReflectUtil.getClass("com.zaxxer.hikari.HikariDataSource") == null) {
            NEED_LIBRARIES.put("com.zaxxer HikariCP 4.0.3", "com.zaxxer.hikari.HikariDataSource");
        }

        if (ReflectUtil.getClass("com.mysql.cj.jdbc.MysqlDataSource") == null) {
            NEED_LIBRARIES.put("mysql mysql-connector-java 8.0.11", "com.mysql.cj.jdbc.MysqlDataSource");
        }

        if (ReflectUtil.getClass("org.h2.jdbcx.JdbcConnectionPool") == null) {
            NEED_LIBRARIES.put("com.h2database h2 1.4.200", "org.h2.jdbcx.JdbcConnectionPool");
        }

        if (ReflectUtil.getClass("com.google.gson.Gson") == null) {
            NEED_LIBRARIES.put("com.google.code.gson gson 2.8.7", "com.google.gson.Gson");
        }

        if (ReflectUtil.getClass("org.apache.logging.log4j.Level") == null) {
            NEED_LIBRARIES.put("org.apache.logging.log4j log4j-api 2.13.2", "org.apache.logging.log4j.Level");
        }

        if (ReflectUtil.getClass("org.apache.logging.log4j.core.LoggerContext") == null) {
            NEED_LIBRARIES.put("org.apache.logging.log4j log4j-core 2.13.2", "org.apache.logging.log4j.core.LoggerContext");
        }

        if (ReflectUtil.getClass("com.mojang.brigadier.CommandDispatcher") == null) {
            NEED_LIBRARIES.put("com.mojang brigadier 1.0.18", "com.mojang.brigadier.CommandDispatcher");
        }
    }

    //    下载线程
    private class DownloadThread implements Runnable {
        private final String url;
        private final File file;
        private final String jarName;

        private DownloadThread(String url, File file, String jarName) {
            this.url = url;
            this.file = file;
            this.jarName = jarName;
        }

        @Override
        public void run() {
            try {
                core.getLogger().log(LoggerLevel.INFO, LanguageKeys.LIBRARY_DOWNLOADING.getMessage(core, jarName));
                HttpUtil.downloadFile(url, file);
                core.getLogger().log(LoggerLevel.INFO, LanguageKeys.LIBRARY_DOWNLOADED.getMessage(core, file.getAbsolutePath()));
            } catch (Exception exception) {
                core.getLogger().log(LoggerLevel.ERROR, LanguageKeys.LIBRARY_DOWNLOAD_FAILED.getMessage(core, file.getAbsolutePath()), exception);

            }
        }
    }
}