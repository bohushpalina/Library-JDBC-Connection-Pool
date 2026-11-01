package com.example.library;

import com.example.library.exception.LibraryException;
import com.example.library.model.ConnectionSource;
import com.example.library.model.DataSource;
import com.example.library.model.DbConfig;
import com.example.library.model.MyConnectionPool;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LibraryApplication {

  public static void main(String[] args) {
    SpringApplication.run(LibraryApplication.class, args);
  }

  @Bean(destroyMethod = "close")
  public ConnectionSource connectionSource() throws LibraryException {
    DbConfig config = DbConfig.load();
    if ("hikari".equals(config.getPoolType())) {
      return new DataSource(config);
    }
    return new MyConnectionPool(config);
  }


  @Bean
  public CommandLineRunner consoleRunner(ConnectionSource source) {
    return args -> new ConsoleMenu(source).run();
  }
}
