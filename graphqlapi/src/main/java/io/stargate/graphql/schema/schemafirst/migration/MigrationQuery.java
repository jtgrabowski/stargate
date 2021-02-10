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
public class MigrationQuery {

  private final AbstractBound<?> query;
  private final String description;

  public MigrationQuery(AbstractBound<?> query, String description) {
    this.query = query;
    this.description = description;
  }

  public AbstractBound<?> getQuery() {
    return query;
  }

  public String getDescription() {
    return description;
  }
}
