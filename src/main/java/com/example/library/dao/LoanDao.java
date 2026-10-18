package com.example.library.dao;

import com.example.library.exception.LibraryException;
import com.example.library.exception.LibraryException.ErrorCode;
import com.example.library.model.ConnectionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoanDao {
  private static final Logger log = LoggerFactory.getLogger(LoanDao.class);

  // блокируем строку экземпляра до конца транзакции и узнаём, не списан ли он
  // (защита от одновременной выдачи одной книги двум читателям)
  private static final String SQL_LOCK_COPY =
          "SELECT written_off FROM book_copy WHERE id = ? FOR UPDATE";

  // ищем открытую выдачу (return_date IS NULL); если нашлась, экземпляр уже на руках
  private static final String SQL_HAS_OPEN_LOAN =
          "SELECT 1 FROM loan WHERE copy_id = ? AND return_date IS NULL";

  // оформляем выдачу; дата ставится на стороне БД, return_date остаётся NULL (книга выдана)
  private static final String SQL_INSERT_LOAN =
          "INSERT INTO loan (copy_id, reader_id, librarian_id, issue_date) " +
                  "VALUES (?, ?, ?, CURDATE())";

  private final ConnectionSource source;

  public LoanDao(ConnectionSource source) {
    this.source = source;
  }

  // Выдаёт экземпляр читателю в одной транзакции.
  // Экземпляр недоступен: COPY_NOT_AVAILABLE. Ошибка БД: TRANSACTION_ERROR
  public void issueBook(int copyId, int readerId, int librarianId) throws LibraryException {
    try (Connection con = source.getConnection()) {
      con.setAutoCommit(false);
      try {
        if (!isCopyAvailable(con, copyId)) {
          con.rollback();
          log.warn("Экземпляр {} недоступен для выдачи", copyId);
          throw new LibraryException(ErrorCode.COPY_NOT_AVAILABLE,
                  "Экземпляр " + copyId + " недоступен: он списан, уже выдан или не существует");
        }

        try (PreparedStatement ps = con.prepareStatement(SQL_INSERT_LOAN)) {
          ps.setInt(1, copyId);
          ps.setInt(2, readerId);
          ps.setInt(3, librarianId);
          ps.executeUpdate();
        }

        con.commit();
        log.info("Экземпляр {} выдан читателю {} (библиотекарь {})", copyId, readerId, librarianId);
      } catch (SQLException e) {
        rollbackQuietly(con);
        throw e;
      }
    } catch (SQLException e) {
      log.error("Не удалось выдать экземпляр {} читателю {}", copyId, readerId, e);
      throw new LibraryException(ErrorCode.TRANSACTION_ERROR,
              "Не удалось выдать экземпляр " + copyId + " читателю " + readerId, e);
    }
  }

  // Блокирует строку экземпляра и проверяет, что он существует, не списан и не находится на руках
  private boolean isCopyAvailable(Connection con, int copyId) throws SQLException {
    // блокировка строки и проверка, что экземпляр существует и не списан
    try (PreparedStatement ps = con.prepareStatement(SQL_LOCK_COPY)) {
      ps.setInt(1, copyId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next() || rs.getBoolean("written_off")) {
          return false;
        }
      }
    }
    // проверка, что экземпляр не выдан другому читателю
    try (PreparedStatement ps = con.prepareStatement(SQL_HAS_OPEN_LOAN)) {
      ps.setInt(1, copyId);
      try (ResultSet rs = ps.executeQuery()) {
        return !rs.next();
      }
    }
  }

  // Откат без повторного исключения, чтобы не потерять исходную ошибку
  private void rollbackQuietly(Connection con) {
    try {
      con.rollback();
    } catch (SQLException e) {
      log.warn("Не удалось откатить транзакцию", e);
    }
  }
}