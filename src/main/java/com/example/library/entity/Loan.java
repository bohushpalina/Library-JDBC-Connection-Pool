package com.example.library.entity;

import java.text.DateFormat;

public class Loan {
  private int idLoan;
  private int idBookCopy;
  private int idReader;
  private int idLibrarian;
  private DateFormat issue_date;
  private DateFormat return_date;

  public Loan(int idBookCopy, int idReader, int idLibrarian, DateFormat issue_date, DateFormat return_date, int idLoan) {
    this.idBookCopy = idBookCopy;
    this.idReader = idReader;
    this.idLibrarian = idLibrarian;
    this.issue_date = issue_date;
    this.return_date = return_date;
    this.idLoan = idLoan;
  }

  public int getIdLoan() {
    return idLoan;
  }

  public void setIdLoan(int idLoan) {
    this.idLoan = idLoan;
  }

  public int getIdBookCopy() {
    return idBookCopy;
  }

  public void setIdBookCopy(int idBookCopy) {
    this.idBookCopy = idBookCopy;
  }

  public int getIdReader() {
    return idReader;
  }

  public void setIdReader(int idReader) {
    this.idReader = idReader;
  }

  public int getIdLibrarian() {
    return idLibrarian;
  }

  public void setIdLibrarian(int idLibrarian) {
    this.idLibrarian = idLibrarian;
  }

  public DateFormat getIssue_date() {
    return issue_date;
  }

  public void setIssue_date(DateFormat issue_date) {
    this.issue_date = issue_date;
  }

  public DateFormat getReturn_date() {
    return return_date;
  }

  public void setReturn_date(DateFormat return_date) {
    this.return_date = return_date;
  }
}
