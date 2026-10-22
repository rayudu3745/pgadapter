// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.spanner.pgadapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.cloud.spanner.MockSpannerServiceImpl.StatementResult;
import com.google.cloud.spanner.Statement;
import com.google.spanner.v1.ExecuteSqlRequest;
import com.google.spanner.v1.TransactionOptions.IsolationLevel;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IsolationLevelMockServerTest extends AbstractMockServerTest {
  private static final String INSERT = "insert into foo values (1)";

  @BeforeClass
  public static void loadPgJdbcDriver() throws Exception {
    // Make sure the PG JDBC driver is loaded.
    Class.forName("org.postgresql.Driver");
  }

  @BeforeClass
  public static void startMockSpannerAndPgAdapterServers() throws Exception {
    // Start PGAdapter without a default database.
    doStartMockSpannerAndPgAdapterServers(null, builder -> {});

    mockSpanner.putStatementResults(StatementResult.update(Statement.of(INSERT), 1L));
  }

  @After
  public void removeExecutionTimes() {
    mockSpanner.removeAllExecutionTimes();
  }

  /**
   * Creates a JDBC connection string that instructs the PG JDBC driver to use the default simple
   * mode for queries and DML statements.
   */
  private String createUrl(String database) {
    return String.format(
        "jdbc:postgresql://localhost:%d/%s?preferQueryMode=simple",
        pgServer.getLocalPort(), database);
  }

  @Test
  public void testUnspecifiedIsolationLevel() throws SQLException {
    try (Connection connection = DriverManager.getConnection(createUrl("my_db"));
        java.sql.Statement stmt = connection.createStatement()) {
      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("commit");
    }

    assertIsolationLevel(IsolationLevel.ISOLATION_LEVEL_UNSPECIFIED);
  }

  @Test
  public void testDefaultIsolationLevelInUrl() throws SQLException {
    String url =
        createUrl("my_db") + "&options=-c%20default_transaction_isolation='repeatable read'";
    try (Connection connection = DriverManager.getConnection(url);
        java.sql.Statement stmt = connection.createStatement()) {
      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("commit");
    }

    assertIsolationLevel(IsolationLevel.REPEATABLE_READ);
  }

  @Test
  public void testSetDefaultIsolationLevel() throws SQLException {
    try (Connection connection = DriverManager.getConnection(createUrl("my_db"));
        java.sql.Statement stmt = connection.createStatement()) {
      stmt.execute("set default_transaction_isolation='repeatable read'");

      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("commit");
    }

    assertIsolationLevel(IsolationLevel.REPEATABLE_READ);
  }

  @Test
  public void testSetSessionCharacteristics() throws SQLException {
    try (Connection connection = DriverManager.getConnection(createUrl("my_db"));
        java.sql.Statement stmt = connection.createStatement()) {
      stmt.execute("set session characteristics as transaction isolation level repeatable read");

      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("commit");
    }

    assertIsolationLevel(IsolationLevel.REPEATABLE_READ);
  }

  @Test
  public void testSetTransaction() throws SQLException {
    try (Connection connection = DriverManager.getConnection(createUrl("my_db"));
        java.sql.Statement stmt = connection.createStatement()) {
      stmt.execute("begin");
      stmt.execute("set transaction isolation level repeatable read");
      stmt.execute(INSERT);
      stmt.execute("commit");
    }

    assertIsolationLevel(IsolationLevel.REPEATABLE_READ);
  }

  @Test
  public void testShowDefaultIsolationLevel() throws SQLException {
    try (Connection connection = DriverManager.getConnection(createUrl("my_db"));
        java.sql.Statement stmt = connection.createStatement()) {
      assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation());
      for (String sql :
          new String[] {
            "show default_transaction_isolation",
            "show transaction isolation level",
            "show transaction_isolation"
          }) {
        try (ResultSet rs = stmt.executeQuery(sql)) {
          assertTrue(rs.next());
          assertEquals("serializable", rs.getString(1));
          assertFalse(rs.next());
        }
      }
      stmt.execute("set default_transaction_isolation='repeatable read'");
      assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.getTransactionIsolation());
      for (String sql :
          new String[] {
            "show default_transaction_isolation",
            "show transaction isolation level",
            "show transaction_isolation"
          }) {
        try (ResultSet rs = stmt.executeQuery(sql)) {
          assertTrue(rs.next());
          assertEquals("repeatable read", rs.getString(1));
          assertFalse(rs.next());
        }
      }
    }
  }

  @Test
  public void testShowTransactionIsolationInTransaction() throws SQLException {
    try (Connection connection = DriverManager.getConnection(createUrl("my_db"));
        java.sql.Statement stmt = connection.createStatement()) {
      stmt.execute("begin isolation level repeatable read");
      try (ResultSet rs = stmt.executeQuery("show default_transaction_isolation")) {
        assertTrue(rs.next());
        assertEquals("serializable", rs.getString(1));
        assertFalse(rs.next());
      }
      for (String sql :
          new String[] {"show transaction isolation level", "show transaction_isolation"}) {
        try (ResultSet rs = stmt.executeQuery(sql)) {
          assertTrue(rs.next());
          assertEquals("repeatable read", rs.getString(1));
          assertFalse(rs.next());
        }
      }
      stmt.execute("commit");
      for (String sql :
          new String[] {"show transaction isolation level", "show transaction_isolation"}) {
        try (ResultSet rs = stmt.executeQuery(sql)) {
          assertTrue(rs.next());
          assertEquals("serializable", rs.getString(1));
          assertFalse(rs.next());
        }
      }
    }
  }

  @Test
  public void testResetDefaultIsolationLevel() throws SQLException {
    try (Connection connection = DriverManager.getConnection(createUrl("my_db"));
        java.sql.Statement stmt = connection.createStatement()) {
      stmt.execute("set default_transaction_isolation='repeatable read'");
      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("commit");
      assertIsolationLevel(IsolationLevel.REPEATABLE_READ);
      mockSpanner.clearRequests();

      stmt.execute("reset default_transaction_isolation");
      try (ResultSet rs = stmt.executeQuery("show default_transaction_isolation")) {
        assertTrue(rs.next());
        assertEquals("serializable", rs.getString(1));
        assertFalse(rs.next());
      }
      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("commit");
      assertIsolationLevel(IsolationLevel.ISOLATION_LEVEL_UNSPECIFIED);
    }
  }

  @Test
  public void testSetDefaultIsolationLevelToDefaultWithStartupOption() throws SQLException {
    String url =
        createUrl("my_db") + "&options=-c%20default_transaction_isolation='repeatable read'";
    try (Connection connection = DriverManager.getConnection(url);
        java.sql.Statement stmt = connection.createStatement()) {
      stmt.execute("set default_transaction_isolation = 'serializable'");
      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("commit");
      assertIsolationLevel(IsolationLevel.SERIALIZABLE);
      mockSpanner.clearRequests();

      stmt.execute("set default_transaction_isolation = default");
      try (ResultSet rs = stmt.executeQuery("show default_transaction_isolation")) {
        assertTrue(rs.next());
        assertEquals("repeatable read", rs.getString(1));
        assertFalse(rs.next());
      }
      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("commit");
      assertIsolationLevel(IsolationLevel.REPEATABLE_READ);
      mockSpanner.clearRequests();

      // Verify that setting default_transaction_read_only does not clobber
      // default_transaction_isolation.
      stmt.execute("set default_transaction_read_only = off");
      try (ResultSet rs = stmt.executeQuery("show default_transaction_isolation")) {
        assertTrue(rs.next());
        assertEquals("repeatable read", rs.getString(1));
        assertFalse(rs.next());
      }
      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("commit");
      assertIsolationLevel(IsolationLevel.REPEATABLE_READ);
    }
  }

  @Test
  public void testSetAndResetDefaultIsolationLevelInTransaction() throws SQLException {
    try (Connection connection = DriverManager.getConnection(createUrl("my_db"));
        java.sql.Statement stmt = connection.createStatement()) {
      // Setting default_transaction_isolation before the first statement in a transaction must not
      // clobber the active transaction's explicit isolation level.
      stmt.execute("begin isolation level repeatable read");
      stmt.execute("set default_transaction_isolation = 'serializable'");
      try (ResultSet rs = stmt.executeQuery("show default_transaction_isolation")) {
        assertTrue(rs.next());
        assertEquals("serializable", rs.getString(1));
        assertFalse(rs.next());
      }
      for (String sql :
          new String[] {"show transaction isolation level", "show transaction_isolation"}) {
        try (ResultSet rs = stmt.executeQuery(sql)) {
          assertTrue(rs.next());
          assertEquals("repeatable read", rs.getString(1));
          assertFalse(rs.next());
        }
      }
      stmt.execute(INSERT);
      stmt.execute("commit");
      assertIsolationLevel(IsolationLevel.REPEATABLE_READ);
      mockSpanner.clearRequests();

      // Subsequent transaction uses the committed default_transaction_isolation (SERIALIZABLE),
      // and RESET ALL / RESET default_transaction_isolation after a statement in an active
      // transaction must succeed and take effect for the next transaction.
      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("reset default_transaction_isolation");
      stmt.execute("reset all");
      stmt.execute("commit");
      assertIsolationLevel(IsolationLevel.SERIALIZABLE);
      mockSpanner.clearRequests();

      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("commit");
      assertIsolationLevel(IsolationLevel.ISOLATION_LEVEL_UNSPECIFIED);
    }
  }

  @Test
  public void testRollbackDefaultIsolationLevel() throws SQLException {
    try (Connection connection = DriverManager.getConnection(createUrl("my_db"));
        java.sql.Statement stmt = connection.createStatement()) {
      stmt.execute("begin");
      stmt.execute("set default_transaction_isolation = 'repeatable read'");
      try (ResultSet rs = stmt.executeQuery("show default_transaction_isolation")) {
        assertTrue(rs.next());
        assertEquals("repeatable read", rs.getString(1));
        assertFalse(rs.next());
      }
      try (ResultSet rs = stmt.executeQuery("show transaction_isolation")) {
        assertTrue(rs.next());
        assertEquals("serializable", rs.getString(1));
        assertFalse(rs.next());
      }
      stmt.execute("rollback");

      for (String sql :
          new String[] {
            "show default_transaction_isolation",
            "show transaction isolation level",
            "show transaction_isolation"
          }) {
        try (ResultSet rs = stmt.executeQuery(sql)) {
          assertTrue(rs.next());
          assertEquals("serializable", rs.getString(1));
          assertFalse(rs.next());
        }
      }
      stmt.execute("begin");
      stmt.execute(INSERT);
      stmt.execute("commit");
      assertIsolationLevel(IsolationLevel.ISOLATION_LEVEL_UNSPECIFIED);
    }
  }

  private void assertIsolationLevel(IsolationLevel expected) {
    List<ExecuteSqlRequest> requests = mockSpanner.getRequestsOfType(ExecuteSqlRequest.class);
    assertEquals(1, requests.size());
    ExecuteSqlRequest request = requests.get(0);
    assertTrue(request.hasTransaction());
    assertTrue(request.getTransaction().hasBegin());
    assertTrue(request.getTransaction().getBegin().hasReadWrite());
    assertEquals(expected, request.getTransaction().getBegin().getIsolationLevel());
  }
}
