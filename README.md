# Library-JDBC-Connection-Pool

A console library management application written in Java with Spring Boot and MySQL. The project focuses on database access: it contains a custom thread-safe JDBC connection pool, an alternative pool based on HikariCP, DAO classes for working with library data, centralized exception handling, and logging.

## About the Project

Library is a console application for managing a book collection: authors, books, physical book copies, readers, librarians, and book loans.

The application allows users to:

- find books by author;
- find free copies of a book;
- issue a book to a reader;
- list readers who keep books for more than a month;
- write off a book copy;
- view the contents of any database table;
- inspect the state of the connection pool;
- run a multi-threaded test of the connection pool.

The type of connection pool is selected in a configuration file, so the same application can run on the custom pool or on HikariCP without changing the code.

## Main Features

### Console Interface

The user interacts with the application through a text menu:

```
           БИБЛИОТЕКА
1. Найти книги по автору
2. Найти свободные экземпляры книги по названию
3. Выдать книгу читателю
4. Списать экземпляр книги
5. Показать должников
0. Выход
Ваш выбор: 
```

Input handling:

- numeric fields are validated and requested again on invalid input;
- empty strings are rejected;
- destructive operations (write-off) require confirmation;
- end of input (Ctrl+D / Ctrl+Z) closes the application correctly;
- an error in one operation never stops the menu.

### Custom Connection Pool

`MyConnectionPool` is a custom implementation of a connection pool.

- Free connections are stored in a thread-safe `LinkedBlockingQueue` and are reused.
- The number of existing connections is tracked by an `AtomicInteger` and never exceeds the configured maximum. A free slot is reserved with a lock-free compare-and-set loop.
- The pool opens the initial number of connections at startup and creates new ones on demand up to the maximum size.
- If all connections are in use, a caller waits for a free one until the configured timeout expires.
- A connection taken from the queue is checked with `isValid(...)`. A broken connection is closed and replaced.
- The client receives a dynamic proxy (`java.lang.reflect.Proxy`) instead of the real connection. Calling `close()` on the proxy returns the connection to the pool, so DAO classes can safely use try-with-resources.
- The proxy protects the pool from a double `close()` and from using a connection after it has been returned.
- When a connection is returned, an unfinished transaction is rolled back and auto-commit is restored.
- When the pool is closed, free connections are closed immediately, and connections that are still in use are closed when they are returned.

### HikariCP Pool

`DataSource` configures and creates a `HikariDataSource` from the same configuration object. It implements the same interface as the custom pool, so the two implementations are interchangeable.

### Exception Handling

All errors of the data layer are converted into a single checked exception, `LibraryException`, which contains:

- an `ErrorCode`;
- a human-readable message;
- the original cause;
- `sqlState` and `vendorCode` when the cause is an `SQLException`.

Error codes:

| Code | Meaning |
|---|---|
| `CONFIG_ERROR` | invalid or missing configuration |
| `DRIVER_NOT_FOUND` | JDBC driver is not on the classpath |
| `POOL_INIT_FAILED` | the pool could not be created |
| `CONNECTION_FAILED` | a connection could not be opened or obtained |
| `POOL_TIMEOUT` | no free connection within the timeout |
| `POOL_CLOSED` | the pool has already been closed |
| `QUERY_ERROR` | SQL query failed |
| `TRANSACTION_ERROR` | transaction failed |
| `COPY_NOT_FOUND` | book copy does not exist |
| `COPY_NOT_AVAILABLE` | book copy is not available |

The console menu catches `LibraryException` in one place, prints the error code and message, and continues working.

### Logging

The project uses SLF4J.

- `error` — failed connections, timeouts, failed queries;
- `warn` — broken connections that are replaced, failed operations in the menu;
- `info` — pool creation and shutdown, configuration loading;
- `debug` — issuing and returning connections.

Passwords are never written to the log.

## Architecture

```
database.properties
        │
        ▼
   DbConfig                 reads and validates settings
        │
        ▼
 ConnectionSource           common interface
   ├── MyConnectionPool     custom pool   (pool.type=custom)
   └── DataSource           HikariCP pool (pool.type=hikari)
        │
        ▼
   DAO classes              take a connection for one operation and close it
        │
        ▼
   ConsoleMenu              user interaction
```

The pool is created once by Spring as a bean and injected into the console runner. All DAO classes share the same pool instance. Spring closes the pool automatically when the application stops.

### Components

- `LibraryApplication` — application entry point; creates the pool and starts the console menu;
- `ConsoleMenu` — console interface: menu, input validation, output;
- `DbConfig` — immutable configuration object loaded from `database.properties`;
- `ConnectionSource` — interface with `getConnection()`, `getStatus()`, and `close()`;
- `MyConnectionPool` — custom thread-safe pool;
- `DataSource` — HikariCP adapter;
- `BookDao`, `BookCopyDao`, `ReaderDao`, `LoanDao` — data access classes;
- `LibraryException` — single exception type with error codes.

## Configuration

The settings are stored in `src/main/resources/database.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/library?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
db.user=your_user
db.password=your_password
db.driver=com.mysql.cj.jdbc.Driver
pool.type=custom
pool.size=5
pool.initial.size=2
pool.timeout.ms=5000
```

| Parameter | Default | Description |
|---|---|---|
| `db.url` | required | JDBC URL of the database |
| `db.user` | required | database user |
| `db.password` | empty | database password |
| `db.driver` | `com.mysql.cj.jdbc.Driver` | JDBC driver class |
| `pool.type` | `custom` | `custom` or `hikari` |
| `pool.size` | 5 | maximum number of connections |
| `pool.initial.size` | 2 | connections opened at startup (from 0 to `pool.size`) |
| `pool.timeout.ms` | 5000 | maximum wait for a free connection |

To use HikariCP, set:

```properties
pool.type=hikari
```

Invalid values stop the application at startup with a clear `CONFIG_ERROR` message.

> Do not commit real credentials. Keep `database.properties` out of version control and publish a template, for example `database.properties.example`, instead.

## Database Structure

The database contains the following tables:

```
author
book
book_author
book_copy
reader
librarian
loan
```

The database must be created and filled before the application is started.

## Technology Stack

The project uses:

- Java
- Spring Boot
- JDBC
- MySQL
- HikariCP
- SLF4J
- Maven

## Running the Application

### Prerequisites

- JDK installed;
- a running MySQL server with the `library` database;
- `database.properties` configured with your own credentials.

### Start

```
mvn spring-boot:run
```

The console menu appears after the application starts.

### Build

```
mvn clean package
java -jar target/<artifact-name>.jar
```

### Useful Settings

To keep the console clean, add to `application.properties`:

```properties
spring.main.banner-mode=off
logging.level.root=WARN
```

If Cyrillic input is not recognized on Windows, switch the console to UTF-8 before starting:

```
chcp 65001
```

## Testing the Pool

Menu item 8 starts 30 tasks in 10 threads while the pool contains at most 5 connections. Expected result with default settings:

```
Tasks completed: 30, failed: 0
Pool state: connections total 5 of 5, free 5
```

This confirms that:

- the pool never creates more connections than the configured maximum;
- extra threads wait for a free connection instead of failing;
- every connection is returned to the pool.

Menu item 7 shows the current pool state at any time.

## Project Structure

A simplified project structure looks like this:

```
src/main/
├── java/com/example/library/
│   ├── LibraryApplication.java
│   ├── ConsoleMenu.java
│   ├── dao/
│   │   ├── BookDao.java
│   │   ├── BookCopyDao.java
│   │   ├── LoanDao.java
│   │   └── ReaderDao.java
│   ├── entity/
│   │   ├── Book.java
│   │   ├── BookCopy.java
│   │   └── Reader.java
│   ├── exception/
│   │   └── LibraryException.java
│   └── model/
│       ├── ConnectionSource.java
│       ├── DbConfig.java
│       ├── MyConnectionPool.java
│       └── DataSource.java
└── resources/
    └── database.properties
```

## Project Goal

The main goal of the project is to demonstrate:

- working with a relational database through JDBC;
- implementing a custom thread-safe connection pool;
- using a proxy to return connections to the pool on `close()`;
- configuring and using HikariCP;
- separating data access from the user interface with DAO classes;
- structured exception handling with error codes;
- logging with SLF4J;
- a console user interface.
