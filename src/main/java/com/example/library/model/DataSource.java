package com.example.library.model;

import com.example.library.exception.LibraryException;
import com.example.library.exception.LibraryException.ErrorCode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/* Настраивает и создаёт пул соединений HikariCP. */
public class DataSource implements ConnectionSource {
  private static final Logger log = LoggerFactory.getLogger(DataSource.class);

  private final HikariDataSource hikari;

  public DataSource(DbConfig config) throws LibraryException {
    try {
      HikariConfig hikariConfig = new HikariConfig();
      hikariConfig.setPoolName("LibraryHikariPool");
      hikariConfig.setJdbcUrl(config.getUrl());
      hikariConfig.setUsername(config.getUser());
      hikariConfig.setPassword(config.getPassword());
      hikariConfig.setDriverClassName(config.getDriver());
      hikariConfig.setMaximumPoolSize(config.getPoolSize());
      hikariConfig.setMinimumIdle(config.getInitialSize());
      hikariConfig.setConnectionTimeout(config.getTimeoutMs());

      this.hikari = new HikariDataSource(hikariConfig);
    } catch (RuntimeException e) {
      log.error("Не удалось создать пул Hikari: {}", e.getMessage());
      throw new LibraryException(ErrorCode.POOL_INIT_FAILED,
              "Не удалось создать пул Hikari: " + e.getMessage(), e);
    }
    log.info("Пул Hikari создан: максимум {}", config.getPoolSize());
  }

  @Override
  public Connection getConnection() throws LibraryException {
    try {
      return hikari.getConnection();
    } catch (SQLException e) {
      log.error("Не удалось получить соединение из Hikari: SQLState={}, errorCode={}, message={}",
              e.getSQLState(), e.getErrorCode(), e.getMessage());
      throw new LibraryException(ErrorCode.CONNECTION_FAILED,
              "Не удалось получить соединение из пула Hikari: " + e.getMessage(), e);
    }
  }

  @Override
  public String getStatus() {
    HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
    if (pool == null) {
      return "пул Hikari не запущен";
    }
    return "соединений всего " + pool.getTotalConnections()
            + ", активных " + pool.getActiveConnections()
            + ", свободных " + pool.getIdleConnections()
            + ", ожидают " + pool.getThreadsAwaitingConnection();
  }

  @Override
  public void close() {
    hikari.close();
    log.info("Пул Hikari закрыт");
  }
}