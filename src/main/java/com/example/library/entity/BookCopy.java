package com.example.library.entity;

public class BookCopy {
  private int idBookCopy;
  private int bookId;
  private String inventoryNumber;

  public BookCopy(int idBookCopy, int bookId, String inventoryNumber) {
    this.idBookCopy = idBookCopy;
    this.bookId = bookId;
    this.inventoryNumber = inventoryNumber;
  }

  public int getIdBookCopy() {
    return idBookCopy;
  }

  public void setIdBookCopy(int idBookCopy) {
    this.idBookCopy = idBookCopy;
  }

  public int getBookId() {
    return bookId;
  }

  public void setBookId(int bookId) {
    this.bookId = bookId;
  }

  public String getInventoryNumber() {
    return inventoryNumber;
  }

  public void setInventoryNumber(String inventoryNumber) {
    this.inventoryNumber = inventoryNumber;
  }
}
