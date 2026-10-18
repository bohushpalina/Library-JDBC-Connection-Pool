package com.example.library.entity;

public class Author {
  private int idAuthor;
  private String fullName;

  public Author(int idAuthor, String fullName) {
    this.idAuthor = idAuthor;
    this.fullName = fullName;
  }

  public int getIdAuthor() {
    return idAuthor;
  }

  public void setIdAuthor(int idAuthor) {
    this.idAuthor = idAuthor;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }
}
