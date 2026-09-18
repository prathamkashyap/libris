package com.example.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:flyway-verify-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.flyway.enabled=true"
    })
class BorrowRecordsIndexTest {

  @Autowired DataSource dataSource;

  @Test
  void flywayAppliedV5AndIndexesExist() throws Exception {
    try (var conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      // 1. Verify Flyway migration history contains V5
      try (ResultSet rs =
          stmt.executeQuery(
              "SELECT version, description FROM flyway_schema_history WHERE version = '5'")) {
        assertThat(rs.next()).as("flyway_schema_history must contain version 5").isTrue();
        assertThat(rs.getString("version")).isEqualTo("5");
      }

      // 2. Verify physical indexes exist via JDBC metadata
      DatabaseMetaData meta = conn.getMetaData();
      List<String> indexNames = new ArrayList<>();

      try (ResultSet rs = meta.getIndexInfo(null, null, "borrow_records", false, false)) {
        while (rs.next()) {
          String name = rs.getString("INDEX_NAME");
          if (name != null && name.startsWith("idx_borrow_records_")) {
            indexNames.add(name.toLowerCase());
          }
        }
      }

      assertThat(indexNames)
          .as("V5 migration indexes must exist on borrow_records")
          .contains("idx_borrow_records_return_date", "idx_borrow_records_due_date");

      // 3. Verify each index maps to the correct column
      try (ResultSet rs = meta.getIndexInfo(null, null, "borrow_records", false, false)) {
        while (rs.next()) {
          String name = rs.getString("INDEX_NAME");
          if (name != null && name.toLowerCase().equals("idx_borrow_records_return_date")) {
            assertThat(rs.getString("COLUMN_NAME").toLowerCase())
                .as("return_date index must be on return_date column")
                .isEqualTo("return_date");
          }
          if (name != null && name.toLowerCase().equals("idx_borrow_records_due_date")) {
            assertThat(rs.getString("COLUMN_NAME").toLowerCase())
                .as("due_date index must be on due_date column")
                .isEqualTo("due_date");
          }
        }
      }
    }
  }
}
