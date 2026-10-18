package com.example.library;

import com.example.library.dao.BookCopyDao;
import com.example.library.dao.BookDao;
import com.example.library.dao.LoanDao;
import com.example.library.dao.ReaderDao;
import com.example.library.entity.Book;
import com.example.library.entity.BookCopy;
import com.example.library.entity.Reader;
import com.example.library.exception.LibraryException;
import com.example.library.exception.LibraryException.ErrorCode;
import com.example.library.model.ConnectionSource;
import com.example.library.model.DataSource;
import com.example.library.model.DbConfig;
import com.example.library.model.MyConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class LibraryApplication {
  private static final Logger log = LoggerFactory.getLogger(LibraryApplication.class);

  // Шаг демонстрации: любая ошибка внутри него приходит как LibraryException
  @FunctionalInterface
  private interface Step {
    void run() throws LibraryException;
  }

  public static void main(String[] args) {
    SpringApplication.run(LibraryApplication.class, args);
  }

  // Тип пула выбирается в database.properties (pool.type: custom или hikari)
  @Bean(destroyMethod = "close")
  public ConnectionSource connectionSource() throws LibraryException {
    DbConfig config = DbConfig.load();
    if ("hikari".equals(config.getPoolType())) {
      return new DataSource(config);
    }
    return new MyConnectionPool(config);
  }

  @Bean
  public CommandLineRunner testDaos(ConnectionSource source) {
    return args -> {
      step(() -> checkConnection(source));

      System.out.println("\nВЫВОД СОДЕРЖИМОГО ТАБЛИЦ БАЗЫ ДАННЫХ\n");
      String[] tables = {"author", "book", "book_author", "book_copy", "reader", "librarian", "loan"};
      for (String table : tables) {
        step(() -> printTableContent(source, table));
      }

      runDaoDemo(source);
      demonstratePool(source);
    };
  }

  // Единое место вывода ошибок: код ошибки и сообщение из LibraryException
  private static void step(Step action) {
    try {
      action.run();
    } catch (LibraryException e) {
      log.warn("Шаг завершился ошибкой: {}", e.toString());
      System.out.println("   Ошибка [" + e.getCode() + "]: " + e.getMessage());
    }
  }

  private static void checkConnection(ConnectionSource source) throws LibraryException {
    try (Connection con = source.getConnection()) {
      System.out.println("Соединение с БД успешно получено из пула. " + source.getStatus());
    } catch (SQLException e) {
      throw new LibraryException(ErrorCode.CONNECTION_FAILED, "Ошибка при возврате соединения в пул", e);
    }
  }

  private static void runDaoDemo(ConnectionSource source) {
    System.out.println("\nЗАПУСК ДЕМОНСТРАЦИИ DAO\n");

    BookDao bookDao = new BookDao(source);
    BookCopyDao bookCopyDao = new BookCopyDao(source);
    LoanDao loanDao = new LoanDao(source);
    ReaderDao readerDao = new ReaderDao(source);

    // 1. Поиск книг по автору
    System.out.println("1. Поиск книг автора 'Лев Толстой':");
    step(() -> {
      List<Book> books = bookDao.findByAuthor("Лев Толстой");
      if (books.isEmpty()) {
        System.out.println("   Книги не найдены.");
      } else {
        books.forEach(b -> System.out.println("   Найдена книга: " + b.getTitle() + " (" + b.getPublish_year() + ")"));
      }
    });

    // 2. Поиск свободных экземпляров
    System.out.println("\n2. Поиск свободных экземпляров книги 'Война и мир':");
    step(() -> {
      List<BookCopy> freeCopies = bookDao.findFreeCopies("Война и мир");
      if (freeCopies.isEmpty()) {
        System.out.println("   Свободных экземпляров нет.");
      } else {
        freeCopies.forEach(c -> System.out.println("   Свободный экземпляр, инвентарный номер: " + c.getInventoryNumber()));
      }
    });

    // 3. Выдача книги
    System.out.println("\n3. Попытка выдать экземпляр (ID: 1) читателю (ID: 1):");
    step(() -> {
      loanDao.issueBook(1, 1, 1);
      System.out.println("   Книга успешно выдана и записана в таблицу loan.");
    });

    // 4. Поиск должников
    System.out.println("\n4. Поиск читателей с просроченными книгами (> 1 месяца):");
    step(() -> {
      List<Reader> debtors = readerDao.findDebtors();
      if (debtors.isEmpty()) {
        System.out.println("   Должников не найдено.");
      } else {
        debtors.forEach(r -> System.out.println("   Должник: " + r.getFullName() + ", Телефон: " + r.getPhone()));
      }
    });

    // 5. Списание экземпляра
    System.out.println("\n5. Списание поврежденного экземпляра (ID: 2):");
    step(() -> {
      bookCopyDao.writeOff(2);
      System.out.println("   Экземпляр успешно списан.");
    });

    // 6. Ошибка: несуществующий экземпляр (показывает, что ошибки идут через LibraryException)
    System.out.println("\n6. Списание несуществующего экземпляра (ID: 999999):");
    step(() -> bookCopyDao.writeOff(999999));

    System.out.println("\nДЕМОНСТРАЦИЯ DAO ЗАВЕРШЕНА");
  }

  // Много потоков одновременно берут соединения из пула: соединений создаётся не больше размера пула
  private static void demonstratePool(ConnectionSource source) throws InterruptedException {
    System.out.println("\nПРОВЕРКА ПУЛА В НЕСКОЛЬКИХ ПОТОКАХ\n");

    int tasks = 30;
    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger failed = new AtomicInteger();
    ExecutorService executor = Executors.newFixedThreadPool(10);

    for (int i = 0; i < tasks; i++) {
      executor.submit(() -> {
        try (Connection con = source.getConnection()) {
          Thread.sleep(50);
          succeeded.incrementAndGet();
        } catch (LibraryException | SQLException e) {
          failed.incrementAndGet();
          log.warn("Задача не получила соединение: {}", e.getMessage());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
    }
    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);

    System.out.println("Задач выполнено: " + succeeded.get() + ", с ошибкой: " + failed.get());
    System.out.println("Состояние пула: " + source.getStatus());
  }

  private static void printTableContent(ConnectionSource source, String tableName) throws LibraryException {
    String query = "SELECT * FROM " + tableName;
    try (Connection con = source.getConnection();
         Statement stmt = con.createStatement();
         ResultSet rs = stmt.executeQuery(query)) {

      System.out.println("--- ТАБЛИЦА: " + tableName.toUpperCase() + " ---");
      ResultSetMetaData metaData = rs.getMetaData();
      int columnCount = metaData.getColumnCount();

      // Вывод заголовков колонок
      for (int i = 1; i <= columnCount; i++) {
        System.out.printf("%-20s", metaData.getColumnName(i));
      }
      System.out.println();
      System.out.println("-".repeat(columnCount * 20));

      // Вывод данных
      boolean hasData = false;
      while (rs.next()) {
        hasData = true;
        for (int i = 1; i <= columnCount; i++) {
          String value = rs.getString(i);
          System.out.printf("%-20s", (value == null ? "NULL" : value));
        }
        System.out.println();
      }

      if (!hasData) {
        System.out.println("(таблица пуста)");
      }
      System.out.println("\n");

    } catch (SQLException e) {
      log.error("Не удалось прочитать таблицу {}", tableName, e);
      throw new LibraryException(ErrorCode.QUERY_ERROR,
              "Не удалось прочитать таблицу '" + tableName + "' (не существует или нет доступа)", e);
    }
  }
}