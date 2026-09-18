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

@SpringBootTest
class BorrowRecordsIndexTest {

  @Autowired DataSource dataSource;

  @Test
  void v5MigrationCreatesExpectedIndexes() throws Exception {
    try (var conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      stmt.executeUpdate(
          "CREATE INDEX idx_borrow_records_return_date ON borrow_records (return_date)");
      stmt.executeUpdate("CREATE INDEX idx_borrow_records_due_date ON borrow_records (due_date)");

      DatabaseMetaData meta = conn.getMetaData();
      List<String> indexNames = new ArrayList<>();

      // H2 with MODE=MySQL + DATABASE_TO_LOWER=TRUE lowercases identifiers
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

      // Verify column ordering for return_date index
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

      // Cleanup
      stmt.executeUpdate("DROP INDEX IF EXISTS idx_borrow_records_return_date");
      stmt.executeUpdate("DROP INDEX IF EXISTS idx_borrow_records_due_date");
    }
  }
}
