package com.example.library.entity;

public class Librarian {
  private int idLibrarian;
  private String fullName;


  public Librarian(int idLibrarian, String fullName) {
    this.idLibrarian = idLibrarian;
    this.fullName = fullName;
  }

  public int getIdLibrarian() {
    return idLibrarian;
  }

  public void setIdLibrarian(int idLibrarian) {
    this.idLibrarian = idLibrarian;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }
}
