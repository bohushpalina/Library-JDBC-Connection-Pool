package com.example.library.dao;

import com.example.library.entity.Reader;
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

public class ReaderDao {
  private static final Logger log = LoggerFactory.getLogger(ReaderDao.class);

  // Должники: читатели с невозвращённой книгой, выданной больше месяца назад
  // DISTINCT нужен, чтобы читатель с несколькими просрочками не дублировался
  private static final String SQL_FIND_DEBTORS =
          "SELECT DISTINCT r.id, r.full_name, r.phone, r.address " +
                  "FROM reader r " +
                  "JOIN loan l ON l.reader_id = r.id " +
                  "WHERE l.return_date IS NULL " +                                  // книга не возвращена
                  "AND l.issue_date < DATE_SUB(CURDATE(), INTERVAL 1 MONTH)";      // выдана более месяца назад

  private final ConnectionSource source;

  public ReaderDao(ConnectionSource source) {
    this.source = source;
  }

  // Возвращает читателей-должников: у них есть невозвращённая книга, выданная больше месяца назад
  public List<Reader> findDebtors() throws LibraryException {
    List<Reader> debtors = new ArrayList<>();
    try (Connection con = source.getConnection();
         PreparedStatement ps = con.prepareStatement(SQL_FIND_DEBTORS);
         ResultSet rs = ps.executeQuery()) {

      while (rs.next()) {
        debtors.add(new Reader(
                rs.getInt("id"),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getString("address")));
      }
      log.info("Найдено должников: {}", debtors.size());
    } catch (SQLException e) {
      log.error("Ошибка при поиске должников", e);
      throw new LibraryException(ErrorCode.QUERY_ERROR, "Не удалось получить список должников", e);
    }
    return debtors;
  }
}