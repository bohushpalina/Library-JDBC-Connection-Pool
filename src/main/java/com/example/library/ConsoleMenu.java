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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Консольный интерфейс: меню, ввод данных пользователем и вывод результатов. */
public class ConsoleMenu {
  private static final Logger log = LoggerFactory.getLogger(ConsoleMenu.class);

  // Таблицы, которые можно просматривать (фиксированный список, имя из ввода в SQL не попадает)
  private static final String[] TABLES =
          {"author", "book", "book_author", "book_copy", "reader", "librarian", "loan"};

  // Действие меню: любая ошибка внутри приходит как LibraryException
  @FunctionalInterface
  private interface Action {
    void run() throws LibraryException;
  }

  private static class InputEndedException extends RuntimeException {
    InputEndedException() {
      super("Ввод завершён");
    }
  }

  private final ConnectionSource source;
  private final BookDao bookDao;
  private final BookCopyDao bookCopyDao;
  private final LoanDao loanDao;
  private final ReaderDao readerDao;
  private final Scanner in = new Scanner(System.in);

  public ConsoleMenu(ConnectionSource source) {
    this.source = source;
    this.bookDao = new BookDao(source);
    this.bookCopyDao = new BookCopyDao(source);
    this.loanDao = new LoanDao(source);
    this.readerDao = new ReaderDao(source);
  }

  /** Главный цикл меню. Возвращается, когда пользователь выбрал выход. */
  public void run() {
    System.out.println("\n ИНФОРМАЦИОННАЯ СИСТЕМА «БИБЛИОТЕКА» ");
    try {
      while (true) {
        printMenu();
        String choice = readLine("Выберите пункт: ");
        switch (choice) {
          case "1": execute(this::findBooksByAuthor); break;
          case "2": execute(this::findFreeCopies); break;
          case "3": execute(this::issueBook); break;
          case "4": execute(this::findDebtors); break;
          case "5": execute(this::writeOffCopy); break;
          case "6": execute(this::showTable); break;
          case "7": execute(this::showPoolStatus); break;
          case "8": execute(this::demonstratePool); break;
          case "0":
            System.out.println("До свидания!");
            return;
          default:
            System.out.println("Нет такого пункта меню. Введите число от 0 до 8.");
        }
      }
    } catch (InputEndedException e) {
      System.out.println("\nВвод завершён. Выход из программы.");
    }
  }

  private void printMenu() {
    System.out.println();
    System.out.println("----------- МЕНЮ -----------");
    System.out.println("1. Найти книги по автору");
    System.out.println("2. Найти свободные экземпляры книги");
    System.out.println("3. Выдать книгу читателю");
    System.out.println("4. Показать должников (книга более месяца)");
    System.out.println("5. Списать экземпляр");
    System.out.println("6. Просмотреть таблицу БД");
    System.out.println("7. Состояние пула соединений");
    System.out.println("8. Проверка пула в нескольких потоках");
    System.out.println("0. Выход");
    System.out.println("----------------------------");
  }

  // Единое место обработки ошибок: меню не должно падать из-за сбоя одной операции
  private void execute(Action action) {
    try {
      action.run();
    } catch (LibraryException e) {
      log.warn("Операция завершилась ошибкой: {}", e.toString());
      System.out.println("Ошибка [" + e.getCode() + "]: " + e.getMessage());
    } catch (InputEndedException e) {
      throw e;
    } catch (RuntimeException e) {
      log.error("Непредвиденная ошибка", e);
      System.out.println("Непредвиденная ошибка: " + e.getMessage());
    }
  }


  private void findBooksByAuthor() throws LibraryException {
    String author = readNonEmpty("Введите автора (например, Лев Толстой): ");
    List<Book> books = bookDao.findByAuthor(author);
    if (books.isEmpty()) {
      System.out.println("Книги не найдены.");
      return;
    }
    System.out.println("Найдено книг: " + books.size());
    books.forEach(b -> System.out.println("  " + b.getTitle() + " (" + b.getPublish_year() + ")"));
  }

  private void findFreeCopies() throws LibraryException {
    String title = readNonEmpty("Введите название книги: ");
    List<BookCopy> copies = bookDao.findFreeCopies(title);
    if (copies.isEmpty()) {
      System.out.println("Свободных экземпляров нет.");
      return;
    }
    System.out.println("Свободных экземпляров: " + copies.size());
    copies.forEach(c -> System.out.println("  Инвентарный номер: " + c.getInventoryNumber()));
  }

  private void issueBook() throws LibraryException {
    int copyId = readPositiveInt("ID экземпляра: ");
    int readerId = readPositiveInt("ID читателя: ");
    int librarianId = readPositiveInt("ID библиотекаря: ");
    loanDao.issueBook(copyId, readerId, librarianId);
    System.out.println("Книга успешно выдана и записана в таблицу loan.");
  }

  private void findDebtors() throws LibraryException {
    List<Reader> debtors = readerDao.findDebtors();
    if (debtors.isEmpty()) {
      System.out.println("Должников не найдено.");
      return;
    }
    System.out.println("Должников: " + debtors.size());
    debtors.forEach(r -> System.out.println("  " + r.getFullName() + ", телефон: " + r.getPhone()));
  }

  private void writeOffCopy() throws LibraryException {
    int copyId = readPositiveInt("ID экземпляра для списания: ");
    if (!confirm("Списать экземпляр " + copyId + "?")) {
      System.out.println("Операция отменена.");
      return;
    }
    bookCopyDao.writeOff(copyId);
    System.out.println("Экземпляр успешно списан.");
  }

  private void showTable() throws LibraryException {
    System.out.println("Доступные таблицы:");
    for (int i = 0; i < TABLES.length; i++) {
      System.out.println("  " + (i + 1) + ". " + TABLES[i]);
    }
    int number = readPositiveInt("Номер таблицы: ");
    if (number > TABLES.length) {
      System.out.println("Нет таблицы с номером " + number + ".");
      return;
    }
    printTableContent(TABLES[number - 1]);
  }

  private void showPoolStatus() {
    System.out.println("Состояние пула: " + source.getStatus());
  }

  // Много потоков одновременно берут соединения: создаётся не больше размера пула
  private void demonstratePool() {
    int tasks = 30;
    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger failed = new AtomicInteger();
    ExecutorService executor = Executors.newFixedThreadPool(10);

    System.out.println("Запуск " + tasks + " задач в 10 потоках...");
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
    try {
      executor.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
      System.out.println("Ожидание было прервано.");
      return;
    }

    System.out.println("Задач выполнено: " + succeeded.get() + ", с ошибкой: " + failed.get());
    System.out.println("Состояние пула: " + source.getStatus());
  }

  private void printTableContent(String tableName) throws LibraryException {
    String query = "SELECT * FROM " + tableName;
    try (Connection con = source.getConnection();
         Statement stmt = con.createStatement();
         ResultSet rs = stmt.executeQuery(query)) {

      System.out.println("--- ТАБЛИЦА: " + tableName.toUpperCase() + " ---");
      ResultSetMetaData metaData = rs.getMetaData();
      int columnCount = metaData.getColumnCount();

      for (int i = 1; i <= columnCount; i++) {
        System.out.printf("%-20s", metaData.getColumnName(i));
      }
      System.out.println();
      System.out.println("-".repeat(columnCount * 20));

      boolean hasData = false;
      while (rs.next()) {
        hasData = true;
        for (int i = 1; i <= columnCount; i++) {
          String value = rs.getString(i);
          System.out.printf("%-20s", value == null ? "NULL" : value);
        }
        System.out.println();
      }
      if (!hasData) {
        System.out.println("(таблица пуста)");
      }
    } catch (SQLException e) {
      log.error("Не удалось прочитать таблицу {}", tableName, e);
      throw new LibraryException(ErrorCode.QUERY_ERROR,
              "Не удалось прочитать таблицу '" + tableName + "' (не существует или нет доступа)", e);
    }
  }

  private String readLine(String prompt) {
    System.out.print(prompt);
    if (!in.hasNextLine()) {
      throw new InputEndedException();
    }
    return in.nextLine().trim();
  }

  // Повторяет запрос, пока пользователь не введёт непустую строку
  private String readNonEmpty(String prompt) {
    while (true) {
      String value = readLine(prompt);
      if (!value.isEmpty()) {
        return value;
      }
      System.out.println("Значение не может быть пустым.");
    }
  }

  // Повторяет запрос, пока пользователь не введёт целое число больше нуля
  private int readPositiveInt(String prompt) {
    while (true) {
      String value = readLine(prompt);
      try {
        int number = Integer.parseInt(value);
        if (number > 0) {
          return number;
        }
        System.out.println("Число должно быть больше нуля.");
      } catch (NumberFormatException e) {
        System.out.println("Нужно ввести целое число, а введено: " + value);
      }
    }
  }

  private boolean confirm(String question) {
    String answer = readLine(question + " (да/нет): ").toLowerCase();
    return answer.equals("да") || answer.equals("д") || answer.equals("y") || answer.equals("yes");
  }
}
