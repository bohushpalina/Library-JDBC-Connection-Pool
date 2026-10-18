package com.example.library.dao;

import com.example.library.exception.LibraryException;
import com.example.library.exception.LibraryException.ErrorCode;
import com.example.library.model.ConnectionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BookCopyDao {
  private static final Logger log = LoggerFactory.getLogger(BookCopyDao.class);

  // Списание: помечаем конкретный экземпляр списанным, строку не удаляем (сохраняется история выдач)
  private static final String SQL_WRITE_OFF =
          "UPDATE book_copy SET written_off = true WHERE id = ?";

  private final ConnectionSource source;

  public BookCopyDao(ConnectionSource source) {
    this.source = source;
  }

  // Списывает экземпляр книги по id; если такого экземпляра нет, выбрасывает COPY_NOT_FOUND
  public void writeOff(int copyId) throws LibraryException {
    try (Connection con = source.getConnection();
         PreparedStatement ps = con.prepareStatement(SQL_WRITE_OFF)) {

      ps.setInt(1, copyId);
      int rowsAffected = ps.executeUpdate();

      if (rowsAffected == 0) {
        log.warn("Экземпляр книги с ID {} не найден для списания", copyId);
        throw new LibraryException(ErrorCode.COPY_NOT_FOUND,
                "Экземпляр книги с ID " + copyId + " не найден");
      }
      log.info("Экземпляр книги с ID {} успешно списан", copyId);
    } catch (SQLException e) {
      log.error("Ошибка при списании экземпляра книги {}", copyId, e);
      throw new LibraryException(ErrorCode.QUERY_ERROR,
              "Не удалось списать экземпляр книги " + copyId, e);
    }
  }
}