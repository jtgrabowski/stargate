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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.GraphqlErrorException;
import io.stargate.db.datastore.DataStore;
import io.stargate.db.schema.Schema;
import io.stargate.db.schema.SchemaEntity;
import io.stargate.db.schema.Table;
import io.stargate.db.schema.UserDefinedType;
import io.stargate.graphql.schema.schemafirst.migration.CassandraSchemaHelper.Difference;
import io.stargate.graphql.schema.schemafirst.processor.EntityMappingModel;
import io.stargate.graphql.schema.schemafirst.processor.MappingModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class CassandraMigrator {

  private final DataStore dataStore;
  private final MappingModel mappingModel;
  private final MigrationStrategy strategy;

  public CassandraMigrator(
      DataStore dataStore, MappingModel mappingModel, MigrationStrategy strategy) {
    this.dataStore = dataStore;
    this.mappingModel = mappingModel;
    this.strategy = strategy;
  }

  /** @throws GraphqlErrorException if the CQL data model can't be migrated */
  public List<MigrationQuery> compute() {

    Schema schema = dataStore.schema();

    SortedSet<MigrationQuery> queries = new TreeSet<>();
    List<String> errors = new ArrayList<>();

    for (EntityMappingModel entity : mappingModel.getEntities().values()) {
      switch (entity.getTarget()) {
        case TABLE:
          Table expectedTable = entity.getTableCqlSchema();
          Table actualTable = schema.keyspace(entity.getKeyspaceName()).table(entity.getCqlName());
          compute(
              expectedTable,
              actualTable,
              CassandraSchemaHelper::compare,
              CassandraSchemaHelper::buildCreateQuery,
              CassandraSchemaHelper::buildDropQuery,
              CassandraSchemaHelper::buildAddColumnQuery,
              "table",
              queries,
              errors);
          break;
        case UDT:
          UserDefinedType expectedType = entity.getUdtCqlSchema();
          UserDefinedType actualType =
              schema.keyspace(entity.getKeyspaceName()).userDefinedType(entity.getCqlName());
          compute(
              expectedType,
              actualType,
              CassandraSchemaHelper::compare,
              CassandraSchemaHelper::buildCreateQuery,
              CassandraSchemaHelper::buildDropQuery,
              CassandraSchemaHelper::buildAddColumnQuery,
              "UDT",
              queries,
              errors);
          break;
        default:
          throw new AssertionError("Unexpected target " + entity.getTarget());
      }
    }
    if (!errors.isEmpty()) {
      throw GraphqlErrorException.newErrorException()
          .message(
              String.format(
                  "The schema you provided can't be mapped to the current CQL data model."
                      + "Consider using a different migration strategy (current: %s). "
                      + "See details in `extensions.migrationErrors` below.",
                  strategy))
          .extensions(ImmutableMap.of("migrationErrors", errors))
          .build();
    }
    return ImmutableList.copyOf(queries);
  }

  private <T extends SchemaEntity> void compute(
      T expected,
      T actual,
      BiFunction<T, T, List<Difference>> comparator,
      BiFunction<T, DataStore, MigrationQuery> createBuilder,
      BiFunction<T, DataStore, MigrationQuery> dropBuilder,
      CassandraSchemaHelper.AddColumnBuilder<T> addColumnBuilder,
      String entityType,
      Set<MigrationQuery> queries,
      List<String> errors) {
    if (strategy == MigrationStrategy.DROP_AND_RECREATE_ALL) {
      // We don't need to compare the schema, just override everything.
      queries.add(dropBuilder.apply(expected, dataStore));
      queries.add(createBuilder.apply(expected, dataStore));
    } else if (actual == null) {
      // The table/UDT does not exist
      switch (strategy) {
        case USE_EXISTING:
          errors.add(String.format("Missing %s %s", entityType, expected.name()));
          break;
        case ADD_MISSING_TABLES:
        case ADD_MISSING_TABLES_AND_COLUMNS:
        case DROP_AND_RECREATE_IF_MISMATCH:
          queries.add(createBuilder.apply(expected, dataStore));
          break;
        default:
          throw new AssertionError("Unexpected strategy " + strategy);
      }
    } else {
      // The table/UDT exists, compare with our model
      List<Difference> differences = comparator.apply(expected, actual);
      if (!differences.isEmpty()) {
        switch (strategy) {
          case USE_EXISTING:
          case ADD_MISSING_TABLES:
            errors.addAll(
                differences.stream().map(Difference::toString).collect(Collectors.toList()));
            break;
          case ADD_MISSING_TABLES_AND_COLUMNS:
            List<Difference> blockers =
                differences.stream().filter(d -> !isAddableColumn(d)).collect(Collectors.toList());
            if (blockers.isEmpty()) {
              for (Difference difference : differences) {
                assert isAddableColumn(difference);
                queries.add(addColumnBuilder.build(expected, difference.getColumn(), dataStore));
              }
            } else {
              errors.addAll(
                  blockers.stream().map(Difference::toString).collect(Collectors.toList()));
            }
            break;
          case DROP_AND_RECREATE_IF_MISMATCH:
            queries.add(dropBuilder.apply(expected, dataStore));
            queries.add(createBuilder.apply(expected, dataStore));
            break;
          default:
            throw new AssertionError("Unexpected strategy " + strategy);
        }
      }
    }
  }

  private boolean isAddableColumn(Difference difference) {
    return difference.getType() == CassandraSchemaHelper.DifferenceType.MISSING_COLUMN
        && !difference.getColumn().isPartitionKey()
        && !difference.getColumn().isClusteringKey();
  }
}
