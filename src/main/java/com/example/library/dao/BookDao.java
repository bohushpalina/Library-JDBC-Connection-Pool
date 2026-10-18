package com.example.library.dao;

import com.example.library.entity.Book;
import com.example.library.entity.BookCopy;
import com.example.library.exception.LibraryException;
import com.example.library.exception.LibraryException.ErrorCode;
import com.example.library.model.ConnectionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BookDao {
  private static final Logger log = LoggerFactory.getLogger(BookDao.class);

  // Запрос для поиска свободных экземпляров по названию книги
  private static final String SQL_FIND_FREE_COPIES =
          "SELECT bc.id, bc.book_id, bc.inventory_number " +
                  "FROM book_copy bc " +
                  "JOIN book b ON b.id = bc.book_id " +
                  "WHERE b.title = ? AND (bc.written_off IS NULL OR bc.written_off = false) " +
                  "AND bc.id NOT IN (SELECT copy_id FROM loan WHERE return_date IS NULL)";

  // Запрос для поиска книг по автору
  private static final String SQL_FIND_BY_AUTHOR =
          "SELECT b.id, b.title, b.publisher, b.publish_year " +
                  "FROM book b " +
                  "JOIN book_author ba ON b.id = ba.book_id " +
                  "JOIN author a ON a.id = ba.author_id " +
                  "WHERE a.full_name = ?";

  private final ConnectionSource source;

  public BookDao(ConnectionSource source) {
    this.source = source;
  }

  // Возвращает свободные экземпляры книги по названию: не списанные и не выданные на руки
  public List<BookCopy> findFreeCopies(String title) throws LibraryException {
    List<BookCopy> freeCopies = new ArrayList<>();
    try (Connection con = source.getConnection();
         PreparedStatement ps = con.prepareStatement(SQL_FIND_FREE_COPIES)) {

      ps.setString(1, title);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          freeCopies.add(new BookCopy(
                  rs.getInt("id"),
                  rs.getInt("book_id"),
                  rs.getString("inventory_number")));
        }
      }
      log.info("Найдено {} свободных экземпляров для книги '{}'", freeCopies.size(), title);
    } catch (SQLException e) {
      log.error("Ошибка при поиске свободных экземпляров книги '{}'", title, e);
      throw new LibraryException(ErrorCode.QUERY_ERROR,
              "Не удалось найти свободные экземпляры книги '" + title + "'", e);
    }
    return freeCopies;
  }

  // Возвращает список книг, написанных автором с указанным ФИО
  public List<Book> findByAuthor(String authorName) throws LibraryException {
    List<Book> books = new ArrayList<>();
    try (Connection con = source.getConnection();
         PreparedStatement ps = con.prepareStatement(SQL_FIND_BY_AUTHOR)) {

      ps.setString(1, authorName);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          books.add(new Book(
                  rs.getInt("id"),
                  rs.getString("title"),
                  rs.getString("publisher"),
                  rs.getString("publish_year")));
        }
      }
      log.info("Найдено {} книг для автора '{}'", books.size(), authorName);
    } catch (SQLException e) {
      log.error("Ошибка при поиске книг автора '{}'", authorName, e);
      throw new LibraryException(ErrorCode.QUERY_ERROR,
              "Не удалось найти книги автора '" + authorName + "'", e);
    }
    return books;
  }
}