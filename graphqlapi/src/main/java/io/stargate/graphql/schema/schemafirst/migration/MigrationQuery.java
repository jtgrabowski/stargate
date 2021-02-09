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

import io.stargate.db.query.builder.AbstractBound;

/** A DDL query to be executed as part of a migration. */
public class MigrationQuery implements Comparable<MigrationQuery> {

  private final AbstractBound<?> query;
  private final String description;
  private final boolean isAddColumn;

  /** @param isAddColumn whether the query is an {@code ALTER TABLE/TYPE... ADD COLUMN} */
  public MigrationQuery(AbstractBound<?> query, String description, boolean isAddColumn) {
    this.query = query;
    this.description = description;
    this.isAddColumn = isAddColumn;
  }

  public AbstractBound<?> getQuery() {
    return query;
  }

  public String getDescription() {
    return description;
  }

  @Override
  public int compareTo(MigrationQuery that) {
    // `ALTER... ADD COLUMN` queries can fail if the column previously existed with a different
    // type, and we have no way to detect that on an existing schema.
    // So we want to execute them all first, so that if something goes wrong we'll have made as few
    // changes to the schema as possible.
    if (this.isAddColumn && !that.isAddColumn) {
      return -1;
    } else if (!this.isAddColumn && that.isAddColumn) {
      return 1;
    } else {
      return 0;
    }
  }
}
