package com.example.library.model;

import com.example.library.exception.LibraryException;
import com.example.library.exception.LibraryException.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MyConnectionPool implements ConnectionSource {
  private static final Logger log = LoggerFactory.getLogger(MyConnectionPool.class);

  private static final int VALIDATION_TIMEOUT_SEC = 2;
  private static final long WAIT_SLICE_MS = 100;

  private final DbConfig config;
  private final int maxSize;
  private final long timeoutMs;
  // свободные соединения
  private final BlockingQueue<Connection> idle = new LinkedBlockingQueue<>();
  // сколько соединений создано всего (свободных и выданных), не больше maxSize
  private final AtomicInteger total = new AtomicInteger();
  private volatile boolean closed;

  public MyConnectionPool(DbConfig config) throws LibraryException {
    this.config = config;
    this.maxSize = config.getPoolSize();
    this.timeoutMs = config.getTimeoutMs();

    try {
      // Загрузка класса драйвера регистрирует его в DriverManager
      Class.forName(config.getDriver());
    } catch (ClassNotFoundException e) {
      log.error("JDBC-драйвер {} не найден", config.getDriver());
      throw new LibraryException(ErrorCode.DRIVER_NOT_FOUND,
              "JDBC-драйвер не найден: " + config.getDriver(), e);
    }

    try {
      for (int i = 0; i < config.getInitialSize(); i++) {
        tryReserveSlot();
        idle.add(openNew());
      }
    } catch (LibraryException e) {
      close();
      throw new LibraryException(ErrorCode.POOL_INIT_FAILED,
              "Не удалось создать начальные соединения пула: " + e.getMessage(), e);
    }
    log.info("Пул соединений создан: максимум {}, создано заранее {}", maxSize, idle.size());
  }

  @Override
  public Connection getConnection() throws LibraryException {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    while (true) {
      if (closed) {
        throw new LibraryException(ErrorCode.POOL_CLOSED, "Пул соединений закрыт");
      }

      Connection physical = idle.poll();
      if (physical == null) {
        if (tryReserveSlot()) {
          // место в пуле есть: открываем новое соединение
          return wrap(openNew());
        }
        // все соединения выданы: ждём, пока кто-нибудь вернёт своё
        physical = pollWithWait(deadline);
      }

      if (physical == null) {
        continue;
      }
      if (isValid(physical)) {
        log.debug("Соединение выдано из пула. {}", getStatus());
        return wrap(physical);
      }
      // соединение оборвалось (например, закрыто сервером): выбрасываем и берём другое
      log.warn("Соединение из пула нерабочее, оно будет закрыто и заменено");
      discard(physical);
    }
  }

  @Override
  public String getStatus() {
    return "соединений всего " + total.get() + " из " + maxSize + ", свободных " + idle.size();
  }

  @Override
  public void close() {
    closed = true;
    Connection physical;
    while ((physical = idle.poll()) != null) {
      discard(physical);
    }
    log.info("Пул соединений закрыт");
  }

//проверяет лимит и занимает место (число total растёт). Соединение не открывает
  private boolean tryReserveSlot() {
    int current;
    do {
      current = total.get();
      if (current >= maxSize) {
        return false;
      }
    } while (!total.compareAndSet(current, current + 1));
    return true;
  }

//открывает настоящее соединение с БД
  private Connection openNew() throws LibraryException {
    try {
      Connection connection = DriverManager.getConnection(
              config.getUrl(), config.getUser(), config.getPassword());
      log.debug("Открыто новое соединение с БД");
      return connection;
    } catch (SQLException e) {
      total.decrementAndGet();
      log.error("Не удалось подключиться к БД: SQLState={}, errorCode={}, message={}",
              e.getSQLState(), e.getErrorCode(), e.getMessage());
      throw new LibraryException(ErrorCode.CONNECTION_FAILED,
              "Не удалось подключиться к БД: " + e.getMessage(), e);
    }
  }

  // Ждёт свободное соединение небольшими отрезками, чтобы заметить и освободившееся место в пуле
  private Connection pollWithWait(long deadline) throws LibraryException {
    long left = deadline - System.nanoTime();
    if (left <= 0) {
      log.error("Превышено время ожидания соединения ({} мс). {}", timeoutMs, getStatus());
      throw new LibraryException(ErrorCode.POOL_TIMEOUT,
              "Нет свободных соединений: за " + timeoutMs + " мс ни одно не освободилось ("
                      + getStatus() + ")");
    }
    long sliceMs = Math.min(TimeUnit.NANOSECONDS.toMillis(left) + 1, WAIT_SLICE_MS);
    try {
      return idle.poll(sliceMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LibraryException(ErrorCode.CONNECTION_FAILED,
              "Ожидание соединения было прервано", e);
    }
  }

  private boolean isValid(Connection physical) {
    try {
      return physical.isValid(VALIDATION_TIMEOUT_SEC);
    } catch (SQLException e) {
      return false;
    }
  }

//принимает соединение обратно: сбрасывает транзакцию, кладёт в очередь idle
  private void release(Connection physical) {
    try {
      if (!physical.getAutoCommit()) {
        physical.rollback();
      }
      physical.setAutoCommit(true);
    } catch (SQLException e) {
      log.warn("Не удалось сбросить состояние соединения, оно будет закрыто: {}", e.getMessage());
      discard(physical);
      return;
    }

    if (closed) {
      discard(physical);
      return;
    }
    idle.offer(physical);
    // пул могли закрыть в момент возврата
    if (closed && idle.remove(physical)) {
      discard(physical);
    }
    log.debug("Соединение возвращено в пул. {}", getStatus());
  }

  // Закрывает физическое соединение и освобождает место в пуле
  private void discard(Connection physical) {
    total.decrementAndGet();
    try {
      physical.close();
    } catch (SQLException e) {
      log.warn("Ошибка при закрытии соединения: {}", e.getMessage());
    }
  }

  // Обёртка: close() возвращает соединение в пул, остальные методы идут в настоящее соединение
  private Connection wrap(Connection physical) {
    AtomicBoolean returned = new AtomicBoolean(false);
    return (Connection) Proxy.newProxyInstance(
            MyConnectionPool.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
              String name = method.getName();

              if (method.getDeclaringClass() == Object.class) {
                switch (name) {
                  case "equals":
                    return proxy == args[0];
                  case "hashCode":
                    return System.identityHashCode(proxy);
                  default:
                    return "PooledConnection[" + physical + "]";
                }
              }
              if ("close".equals(name)) {
                // повторный close() не должен вернуть соединение в пул второй раз
                if (returned.compareAndSet(false, true)) {
                  release(physical);
                }
                return null;
              }
              if ("isClosed".equals(name)) {
                return returned.get() || physical.isClosed();
              }
              if (returned.get()) {
                throw new SQLException("Соединение уже возвращено в пул");
              }
              try {
                return method.invoke(physical, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }
}