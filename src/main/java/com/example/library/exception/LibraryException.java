package com.example.library.exception;

import java.sql.SQLException;

public class LibraryException extends Exception {
  private static final long serialVersionUID = 1L;

  public enum ErrorCode {
    CONFIG_ERROR("Ошибка конфигурации"),
    DRIVER_NOT_FOUND("JDBC-драйвер не найден"),
    POOL_INIT_FAILED("Не удалось создать пул соединений"),
    CONNECTION_FAILED("Не удалось получить соединение с БД"),
    POOL_TIMEOUT("Нет свободных соединений в пуле"),
    POOL_CLOSED("Пул соединений закрыт"),
    QUERY_ERROR("Ошибка выполнения SQL-запроса"),
    TRANSACTION_ERROR("Ошибка транзакции"),
    COPY_NOT_FOUND("Экземпляр книги не найден"),
    COPY_NOT_AVAILABLE("Экземпляр книги недоступен");

    private final String description;

    ErrorCode(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  private final ErrorCode code;
  private final String sqlState;
  private final int vendorCode;

  public LibraryException(ErrorCode code, String message) {
    this(code, message, null);
  }

  public LibraryException(ErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
    if (cause instanceof SQLException) {
      SQLException sql = (SQLException) cause;
      this.sqlState = sql.getSQLState();
      this.vendorCode = sql.getErrorCode();
    } else {
      this.sqlState = null;
      this.vendorCode = 0;
    }
  }

  public ErrorCode getCode() {
    return code;
  }

  public String getSqlState() {
    return sqlState;
  }

  public int getVendorCode() {
    return vendorCode;
  }

  @Override
  public String toString() {
    return "LibraryException[" + code + "]: " + getMessage();
  }
}