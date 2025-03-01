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

package org.liquibase.groovy.serialize

import liquibase.change.core.AddColumnChange

import org.junit.Test
import static org.junit.Assert.*

import liquibase.change.ColumnConfig
import liquibase.change.core.RenameColumnChange
import liquibase.change.core.DropColumnChange
import liquibase.change.core.AlterSequenceChange
import liquibase.change.core.CreateTableChange
import liquibase.change.core.RenameTableChange
import liquibase.change.core.DropTableChange
import liquibase.change.core.CreateViewChange
import liquibase.change.core.CreateProcedureChange
import org.junit.Ignore


class StructuralRefactoringSerializerTests extends SerializerTests {

    @Test
    void addMinimalColumnWithoutConstraints() {
        def change = [
                schemaName: 'schema',
                tableName : 'animal',
                columns   : [
                        [
                                name: 'monkey_status',
                                type: 'varchar(98)'
                        ] as ColumnConfig
                ]
        ] as AddColumnChange

        def serializedText = serializer.serialize(change, true)
        def expectedText = """\
addColumn(schemaName: 'schema', tableName: 'animal') {
  column(name: 'monkey_status', type: 'varchar(98)')
}"""
        assertEquals expectedText, serializedText
    }


    @Test
    void renameColumn() {
        def change = [
                tableName     : 'monkey',
                oldColumnName : 'fail',
                newColumnName : 'win',
                columnDataType: 'varchar(9001)'
        ] as RenameColumnChange

        def serializedText = serializer.serialize(change, true)
        def expectedText = "renameColumn(columnDataType: 'varchar(9001)', newColumnName: 'win', oldColumnName: 'fail', tableName: 'monkey')"
        assertEquals expectedText, serializedText
    }


    @Test
    void dropColumn() {
        def change = [
                columnName: 'emotion',
                schemaName: 'schema',
                tableName : 'monkey'
        ] as DropColumnChange

        def serializedText = serializer.serialize(change, true)
        def expectedText = "dropColumn(columnName: 'emotion', schemaName: 'schema', tableName: 'monkey')"
        assertEquals expectedText, serializedText
    }


    @Test
    void alterSequence() {
        def change = [
                sequenceName: 'seq',
                incrementBy : 314
        ] as AlterSequenceChange

        def serializedText = serializer.serialize(change, true)
        def expectedText = "alterSequence(incrementBy: 314, sequenceName: 'seq')"
        assertEquals expectedText, serializedText
    }


    @Test
    void createTable() {
        def change = [
                remarks   : 'angry',
                tableName : 'monkey',
                tablespace: 'oracle_tablespace',
                schemaName: 'schema',
                columns   : [
                        [
                                name: 'status',
                                type: 'varchar(100)'
                        ] as ColumnConfig,
                        [
                                name: 'id',
                                type: 'int'
                        ] as ColumnConfig
                ]
        ] as CreateTableChange

        def serializedText = serializer.serialize(change, true)
        def expectedText = """\
createTable(remarks: 'angry', schemaName: 'schema', tableName: 'monkey', tablespace: 'oracle_tablespace') {
  column(name: 'status', type: 'varchar(100)')
  column(name: 'id', type: 'int')
}"""
        assertEquals expectedText, serializedText
    }


    @Test
    void renameTable() {
        def change = [
                schemaName  : 'schema',
                oldTableName: 'fail_table',
                newTableName: 'win_table'
        ] as RenameTableChange

        def serializedText = serializer.serialize(change, true)
        def expectedText = "renameTable(newTableName: 'win_table', oldTableName: 'fail_table', schemaName: 'schema')"
        assertEquals expectedText, serializedText
    }


    @Test
    void dropTable() {
        def change = [
                schemaName: 'schema',
                tableName : 'fail_table'
        ] as DropTableChange

        def serializedText = serializer.serialize(change, true)
        def expectedText = "dropTable(schemaName: 'schema', tableName: 'fail_table')"
        assertEquals expectedText, serializedText
    }


    @Test
    void createViewWithSql() {
        def change = [
                schemaName     : 'schema',
                viewName       : 'monkey_view',
                replaceIfExists: true,
                selectQuery    : "SELECT * FROM monkey WHERE state='angry'"
        ] as CreateViewChange

        def serializedText = serializer.serialize(change, true)
        def expectedText = """\
createView(replaceIfExists: true, schemaName: 'schema', viewName: 'monkey_view') {
  ''' SELECT * FROM monkey WHERE state='angry' '''
}"""
        assertEquals expectedText, serializedText
    }

    @Test
    void createViewNoSql() {
        def change = [
                schemaName     : 'schema',
                viewName       : 'monkey_view',
                replaceIfExists: true,
                remarks        : 'monkeys!',
                path           : 'monkey_view.sql'
        ] as CreateViewChange

        def serializedText = serializer.serialize(change, true)
        def expectedText = """\
createView(path: 'monkey_view.sql', remarks: 'monkeys!', replaceIfExists: true, schemaName: 'schema', viewName: 'monkey_view')"""
        assertEquals expectedText, serializedText
    }
}

