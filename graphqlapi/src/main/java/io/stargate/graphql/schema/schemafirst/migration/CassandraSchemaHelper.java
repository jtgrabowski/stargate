/*
 * Copyright The Stargate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.stargate.graphql.schema.schemafirst.migration;

import io.stargate.db.datastore.DataStore;
import io.stargate.db.schema.Column;
import io.stargate.db.schema.Table;
import io.stargate.db.schema.UserDefinedType;
import java.util.ArrayList;
import java.util.List;

public class CassandraSchemaHelper {

  /** @return a list of differences, or empty if the tables match. */
  public static List<Difference> compare(Table expectedTable, Table actualTable) {

    // Note: we deliberately avoid comparing the keyspaces, they might differ because of keyspace
    // decoration.

    String tableName = expectedTable.name();

    if (!tableName.equals(actualTable.name())) {
      throw new IllegalArgumentException(
          "This should only be called for tables with the same name");
    }

    List<Difference> differences = new ArrayList<>();

    for (Column expectedColumn : expectedTable.columns()) {
      Column actualColumn = actualTable.column(expectedColumn.name());
      compareColumn(expectedColumn, actualColumn, differences);
    }
    return differences;
  }

  private static void compareColumn(
      Column expectedColumn, Column actualColumn, List<Difference> differences) {

    Column.ColumnType columnType = expectedColumn.type();
    assert columnType != null;

    if (actualColumn == null) {
      String description = null;
      if (expectedColumn.isPartitionKey()) {
        description = "it can't be added because it is marked as a partition key";
      } else if (expectedColumn.isClusteringKey()) {
        description = "it can't be added because it is marked as a clustering column";
      }
      differences.add(new Difference(expectedColumn, DifferenceType.MISSING_COLUMN, description));
    } else if (!columnType.equals(actualColumn.type())) {
      differences.add(
          new Difference(
              expectedColumn,
              DifferenceType.WRONG_TYPE,
              String.format(
                  "expected %s, found %s",
                  columnType.cqlDefinition(), actualColumn.type().cqlDefinition())));
    } else if (expectedColumn.kind() != actualColumn.kind()) {
      differences.add(
          new Difference(
              expectedColumn,
              DifferenceType.WRONG_KIND,
              String.format("expected %s, found %s", expectedColumn.kind(), actualColumn.kind())));
    } else if (expectedColumn.kind() == Column.Kind.Clustering
        && expectedColumn.order() != actualColumn.order()) {
      differences.add(
          new Difference(
              expectedColumn,
              DifferenceType.WRONG_CLUSTERING_ORDER,
              String.format(
                  "expected %s, found %s", expectedColumn.order(), actualColumn.order())));
    }
  }

  public static MigrationQuery buildCreateQuery(Table table, DataStore dataStore) {
    return new MigrationQuery(
        dataStore
            .queryBuilder()
            .create()
            .table(table.keyspace(), table.name())
            .column(table.columns())
            .build()
            .bind(),
        "Create table " + table.name());
  }

  public static MigrationQuery buildDropQuery(Table table, DataStore dataStore) {
    return new MigrationQuery(
        dataStore
            .queryBuilder()
            .drop()
            .table(table.keyspace(), table.name())
            .ifExists()
            .build()
            .bind(),
        "Drop table " + table.name());
  }

  public static MigrationQuery buildAddColumnQuery(
      Table table, Column column, DataStore dataStore) {
    return new MigrationQuery(
        dataStore
            .queryBuilder()
            .alter()
            .table(table.keyspace(), table.name())
            .addColumn(column)
            .build()
            .bind(),
        String.format("Add table column %s.%s", table.name(), column.name()));
  }

  /** @return a list of differences, or empty if the UDTs match. */
  public static List<Difference> compare(UserDefinedType expectedType, UserDefinedType actualType) {

    // Note: we deliberately avoid comparing the keyspaces, they might differ because of keyspace
    // decoration.

    String typeName = expectedType.name();

    if (!typeName.equals(actualType.name())) {
      throw new IllegalArgumentException("This should only be called for UDTs with the same name");
    }

    List<Difference> differences = new ArrayList<>();
    for (Column expectedColumn : expectedType.columns()) {
      Column actualColumn = actualType.columnMap().get(expectedColumn.name());
      compareColumn(expectedColumn, actualColumn, differences);
    }
    return differences;
  }

  public static MigrationQuery buildCreateQuery(UserDefinedType type, DataStore dataStore) {
    return new MigrationQuery(
        dataStore.queryBuilder().create().type(type.keyspace(), type).build().bind(),
        "Create UDT " + type.name());
  }

  public static MigrationQuery buildDropQuery(UserDefinedType type, DataStore dataStore) {
    return new MigrationQuery(
        dataStore.queryBuilder().drop().type(type.keyspace(), type).ifExists().build().bind(),
        "Drop UDT " + type.name());
  }

  public static MigrationQuery buildAddColumnQuery(
      UserDefinedType type, Column column, DataStore dataStore) {
    return new MigrationQuery(
        dataStore
            .queryBuilder()
            .alter()
            .type(type.keyspace(), type)
            .addColumn(column)
            .build()
            .bind(),
        String.format("Add UDT field %s.%s", type.name(), column.name()));
  }

  public enum DifferenceType {
    MISSING_COLUMN,
    WRONG_TYPE,
    WRONG_KIND,
    WRONG_CLUSTERING_ORDER,
  }

  public static class Difference {

    private final Column column;
    private final DifferenceType type;
    private final String description;

    public Difference(Column column, DifferenceType type, String description) {
      this.column = column;
      this.type = type;
      this.description = description;
    }

    public Column getColumn() {
      return column;
    }

    public DifferenceType getType() {
      return type;
    }

    public String toGraphqlMessage() {
      return String.format(
          "[%s] %s.%s%s",
          type, column.table(), column.name(), description == null ? "" : ": " + description);
    }
  }

  /**
   * Functional interface to capture the signatures of {@link #buildAddColumnQuery(Table, Column,
   * DataStore)} and {@link #buildAddColumnQuery(UserDefinedType, Column, DataStore)}.
   */
  @FunctionalInterface
  public interface AddColumnBuilder<T> {
    MigrationQuery build(T type, Column column, DataStore dataStore);
  }

  private CassandraSchemaHelper() {}
}
