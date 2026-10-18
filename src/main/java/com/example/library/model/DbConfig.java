package com.example.library.model;

import com.example.library.exception.LibraryException;
import com.example.library.exception.LibraryException.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Параметры подключения и пула из файла ресурсов database.properties. */
public final class DbConfig {
  private static final Logger log = LoggerFactory.getLogger(DbConfig.class);

  private static final String PROPERTIES_FILE = "database.properties";

  private final String url;
  private final String user;
  private final String password;
  private final String driver;
  private final String poolType;
  private final int poolSize;
  private final int initialSize;
  private final long timeoutMs;

  private DbConfig(String url, String user, String password, String driver,
                   String poolType, int poolSize, int initialSize, long timeoutMs) {
    this.url = url;
    this.user = user;
    this.password = password;
    this.driver = driver;
    this.poolType = poolType;
    this.poolSize = poolSize;
    this.initialSize = initialSize;
    this.timeoutMs = timeoutMs;
  }

  public static DbConfig load() throws LibraryException {
    log.info("Загрузка конфигурации БД из {}", PROPERTIES_FILE);
    Properties props = new Properties();
    try (InputStream in = DbConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
      if (in == null) {
        throw new LibraryException(ErrorCode.CONFIG_ERROR,
                "Файл " + PROPERTIES_FILE + " не найден в classpath");
      }
      props.load(in);
    } catch (IOException e) {
      throw new LibraryException(ErrorCode.CONFIG_ERROR,
              "Не удалось прочитать файл " + PROPERTIES_FILE, e);
    }
    return fromProperties(props);
  }

  public static DbConfig fromProperties(Properties props) throws LibraryException {
    String url = required(props, "db.url");
    String user = required(props, "db.user");
    String password = trim(props.getProperty("db.password", ""));
    String driver = trim(props.getProperty("db.driver", "com.mysql.cj.jdbc.Driver"));
    String poolType = trim(props.getProperty("pool.type", "custom")).toLowerCase();
    int poolSize = intValue(props, "pool.size", 5);
    int initialSize = intValue(props, "pool.initial.size", 2);
    long timeoutMs = intValue(props, "pool.timeout.ms", 5000);

    if (!poolType.equals("custom") && !poolType.equals("hikari")) {
      throw new LibraryException(ErrorCode.CONFIG_ERROR,
              "pool.type должен быть custom или hikari, а указано: " + poolType);
    }
    if (poolSize < 1) {
      throw new LibraryException(ErrorCode.CONFIG_ERROR, "pool.size должен быть не меньше 1");
    }
    if (initialSize < 0 || initialSize > poolSize) {
      throw new LibraryException(ErrorCode.CONFIG_ERROR,
              "pool.initial.size должен быть от 0 до pool.size");
    }
    if (timeoutMs <= 0) {
      throw new LibraryException(ErrorCode.CONFIG_ERROR, "pool.timeout.ms должен быть больше 0");
    }

    log.info("Конфигурация загружена: url={}, user={}, pool.type={}, pool.size={}",
            url, user, poolType, poolSize);
    return new DbConfig(url, user, password, driver, poolType, poolSize, initialSize, timeoutMs);
  }

  private static String required(Properties props, String key) throws LibraryException {
    String value = trim(props.getProperty(key));
    if (value == null || value.isEmpty()) {
      throw new LibraryException(ErrorCode.CONFIG_ERROR,
              "В " + PROPERTIES_FILE + " не задан параметр " + key);
    }
    return value;
  }

  private static int intValue(Properties props, String key, int defaultValue) throws LibraryException {
    String value = trim(props.getProperty(key));
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new LibraryException(ErrorCode.CONFIG_ERROR,
              "Параметр " + key + " должен быть целым числом, а указано: " + value, e);
    }
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  public String getUrl() {
    return url;
  }

  public String getUser() {
    return user;
  }

  public String getPassword() {
    return password;
  }

  public String getDriver() {
    return driver;
  }

  public String getPoolType() {
    return poolType;
  }

  public int getPoolSize() {
    return poolSize;
  }

  public int getInitialSize() {
    return initialSize;
  }

  public long getTimeoutMs() {
    return timeoutMs;
  }
}