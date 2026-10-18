package com.example.library.model;

import com.example.library.exception.LibraryException;

import java.sql.Connection;

public interface ConnectionSource extends AutoCloseable {

  Connection getConnection() throws LibraryException;

  String getStatus();

  @Override
  void close();
}