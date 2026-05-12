package com.minisql.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 全局配置读取工具类 (单例)
 * 用于统一加载 classpath 下的 application.properties 文件
 */
public class ConfigReader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigReader.class);
    private static final Properties properties = new Properties();

    // 静态代码块，类加载时自动读取配置文件
    static {
        try (InputStream in = ConfigReader.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                logger.error("未找到配置文件: application.properties，请检查 minisql-common/src/main/resources 目录！");
                throw new RuntimeException("application.properties not found");
            }
            properties.load(in);
            logger.info("成功加载全局配置文件 application.properties");
        } catch (IOException e) {
            logger.error("加载 application.properties 失败！", e);
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }

    /**
     * 获取字符串类型的配置项
     */
    public static String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * 获取整数类型的配置项
     */
    public static int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                logger.warn("配置项 {} 的值 '{}' 无法转换为整数，将使用默认值 {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * 获取带前缀的所有原始 Properties 对象（供特殊场景使用）
     */
    public static Properties getProperties() {
        return properties;
    }
}