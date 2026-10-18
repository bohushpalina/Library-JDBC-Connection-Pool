package com.example.library.entity;

public class Book {
  private int idBook;
  private String title;
  private String publisher;
  private String publishYear;

  public Book(int idBook, String title, String publisher, String publish_year) {
    this.idBook = idBook;
    this.title = title;
    this.publisher = publisher;
    this.publishYear = publish_year;
  }

  public int getIdBook() {
    return idBook;
  }

  public void setIdBook(int idBook) {
    this.idBook = idBook;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getPublisher() {
    return publisher;
  }

  public void setPublisher(String publisher) {
    this.publisher = publisher;
  }

  public String getPublish_year() {
    return publishYear;
  }

  public void setPublish_year(String publish_year) {
    this.publishYear = publish_year;
  }
}
