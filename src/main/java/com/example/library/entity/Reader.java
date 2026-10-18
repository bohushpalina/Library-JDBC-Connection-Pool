package com.example.library.entity;

public class Reader {
  private int idReader;
  private String fullName;
  private String phone;
  private String address;

  public Reader(int idReader, String fullName, String phone, String address) {
    this.idReader = idReader;
    this.fullName = fullName;
    this.phone = phone;
    this.address = address;
  }

  public int getIdReader() {
    return idReader;
  }

  public void setIdReader(int idReader) {
    this.idReader = idReader;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }
}
