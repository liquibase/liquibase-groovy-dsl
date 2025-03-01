/*
 * Copyright 2011-2025 Tim Berglund and Steven C. Saliman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.liquibase.groovy.helper


import liquibase.change.AbstractChange
import liquibase.change.ChangeMetaData
import liquibase.change.ChangeWithColumns
import liquibase.change.ColumnConfig
import liquibase.change.DatabaseChange
import liquibase.database.Database
import liquibase.statement.SqlStatement
import liquibase.statement.core.RawSqlStatement

/**
 * A change that does nothing, this is used to test loading of dynamic extensions.  Note that this
 * must be in the liquibase.ext package for Liquibase to find it.
 */
@DatabaseChange(name = "extensionChange",
        description = "Sample extension change to loading of extension",
        priority = ChangeMetaData.PRIORITY_DEFAULT, appliesTo = "table")
class ExtensionChange extends AbstractChange implements ChangeWithColumns<ColumnConfig> {

    private String name;

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    @Override
    String getConfirmationMessage() {
        return null
    }

    @Override
    public SqlStatement[] generateStatements(Database database) {
        [new RawSqlStatement("SELECT count(*) FROM monkey")]
    }

    @Override
    void addColumn(ColumnConfig column) {

    }

    @Override
    List<ColumnConfig> getColumns() {
        return null
    }

    @Override
    void setColumns(List<ColumnConfig> columns) {

    }
}
