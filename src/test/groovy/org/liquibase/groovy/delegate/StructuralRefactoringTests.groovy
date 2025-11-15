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
package org.liquibase.groovy.delegate

import liquibase.change.ColumnConfig
import liquibase.change.core.AddColumnChange
import liquibase.change.core.CreateProcedureChange
import liquibase.change.core.CreateTableChange
import liquibase.change.core.CreateViewChange
import liquibase.change.core.DropColumnChange
import liquibase.change.core.DropProcedureChange
import liquibase.change.core.DropTableChange
import liquibase.change.core.DropViewChange
import liquibase.change.core.MergeColumnChange
import liquibase.change.core.ModifyDataTypeChange
import liquibase.change.core.RenameColumnChange
import liquibase.change.core.RenameTableChange
import liquibase.change.core.RenameViewChange
import liquibase.exception.ChangeLogParseException
import liquibase.parser.groovy.exception.UnrecognizedElement
import org.junit.Test

import static org.junit.Assert.*

/**
 * This is one of several classes that test the creation of refactoring changes for ChangeSets. This
 * particular class tests changes that alter table structure.
 * <p>
 * Since the Groovy DSL parser is meant to act as a pass-through for Liquibase itself, it doesn't
 * do much in the way of error checking.  For example, we aren't concerned with whether or not
 * required attributes are present - we leave that to Liquibase itself.  In general, each change
 * will have 3 kinds of tests:<br>
 * <ol>
 * <li>A test with an empty parameter map, and if supported, an empty closure. This kind of test
 * will make sure that the Groovy parser doesn't introduce any unintended attribute defaults for a
 * change.</li>
 * <li>A test that sets all the attributes known to be supported by Liquibase at this time.  This
 * makes sure that the Groovy parser will send any given groovy attribute to the correct place in
 * Liquibase.  For changes that allow a child closure, this test will include just enough in the
 * closure to make sure it gets processed, and that the right kind of closure is called.</li>
 * <li>Some tests take columns or a where clause in a child closure.  The same closure handles both,
 * but should reject one or the other based on how the closure gets called. These changes will have
 * an additional test with an invalid closure to make sure it sets up the closure properly</li>
 * </ol>
 * <p>
 * Some changes require a little more testing, such as the {@code sql} change that can receive sql
 * as a string, or as a closure, or the {@code delete}change, which is valid both with and without
 * a child closure.
 * <p>
 * We don't worry about testing combinations that don't make sense, such as allowing a createIndex
 * change a closure, but no attributes, since it doesn't make sense to have this kind of change
 * without both a table name and at least one column.  If a user tries it, they will get errors
 * from Liquibase itself.
 *
 * @author Steven C. Saliman
 */
class StructuralRefactoringTests extends ChangeSetTests {

    /**
     * Try creating an addColumn change with no attributes and an empty closure.  Make sure the DSL
     * doesn't try to make any assumptions.  It also validates our assumption that a Liquibase
     * AddColumnChange always has a collection of columns, even if they are empty.
     */
    @Test
    void addColumnEmpty() {
        buildChangeSet {
            addColumn([:]) {}
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddColumnChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNotNull changes[0].resourceAccessor
        def columns = changes[0].columns
        assertNotNull columns
        assertEquals 0, columns.size()
        assertNoOutput()
    }

    /**
     * A where clause is not valid for addColumn, so try one and make sure it gets rejected.
     */
    @Test(expected = ChangeLogParseException)
    void addColumnWithWhereClause() {
        buildChangeSet {
            addColumn(catalogName: 'zoo', schemaName: 'animal', tableName: 'monkey') {
                where "invalid"
            }
        }
    }

    /**
     * Test parsing a createProcedure change when we have an empty map and closure to make sure the
     * DSL doesn't try to set any defaults.
     */
    @Test
    void createProcedureEmpty() {
        buildChangeSet {
            createProcedure([:]) {}
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        //assertNull changes[0].comment
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].procedureName
        assertNull changes[0].procedureText
        assertNull changes[0].dbms
        assertNull changes[0].path
        assertNull changes[0].relativeToChangelogFile
        assertNull changes[0].encoding
        assertNull changes[0].replaceIfExists
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * test parsing a createProcedure change when we have no attributes just the body in a closure.
     * Since the only supported attribute is for comments, this will be common.
     */
    @Test
    void createProcedureClosureOnly() {
        def sql = """\
CREATE OR REPLACE PROCEDURE testMonkey
IS
BEGIN
 -- do something with the monkey
END;"""
        buildChangeSet {
            createProcedure { sql }
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof CreateProcedureChange
        //assertNull changes[0].comment
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].procedureName
        assertEquals sql, changes[0].procedureText
        assertNull changes[0].dbms
        assertNull changes[0].path
        assertNull changes[0].relativeToChangelogFile
        assertNull changes[0].encoding
        assertNull changes[0].replaceIfExists
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a createProcedure change when we have no attributes, just the procedure body as
     * a string.  Since the only supported attribute is for comments, this will be common.
     */
//    @Test
//    void createProcedureSqlOnlyAsString() {
//        def sql = """\
//CREATE OR REPLACE PROCEDURE testMonkey
//IS
//BEGIN
// -- do something with the monkey
//END;"""
//        buildChangeSet {
//            createProcedure sql
//        }
//
//        assertEquals 0, changeSet.rollback.changes.size()
//        def changes = changeSet.changes
//        assertNotNull changes
//        assertEquals 1, changes.size()
//        assertTrue changes[0] instanceof CreateProcedureChange
//        assertNull changes[0].comments
//        assertNull changes[0].catalogName
//        assertNull changes[0].schemaName
//        assertNull changes[0].procedureName
//        assertEquals sql, changes[0].procedureText
//        assertNull changes[0].dbms
//        assertNull changes[0].path
//        assertNull changes[0].relativeToChangelogFile
//        assertNull changes[0].encoding
//        assertNull changes[0].replaceIfExists
//        assertNotNull changes[0].resourceAccessor
//        assertNoOutput()
//    }

    /**
     * Test parsing a createProcedure change when we have both comments and SQL.
     */
    @Test
    void createProcedureFull() {
        def sql = """\
CREATE OR REPLACE PROCEDURE testMonkey
IS
BEGIN
 -- do something with the monkey
END;"""
        buildChangeSet {
            createProcedure(
                   // comment: 'someComments',
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    procedureName: 'procedure',
                    dbms: 'mysql',
                    path: 'mypath',
                    relativeToChangelogFile: false,
                    encoding: 'utf8',
                    replaceIfExists: true) { sql }
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof CreateProcedureChange
        //assertEquals 'someComments', changes[0].comments
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'procedure', changes[0].procedureName
        assertEquals sql, changes[0].procedureText
        assertEquals 'mysql', changes[0].dbms
        assertEquals 'mypath', changes[0].path
        assertFalse changes[0].relativeToChangelogFile
        assertEquals 'utf8', changes[0].encoding
        assertTrue changes[0].replaceIfExists
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a createStoredProcedure change when we have an empty map and closure. This has
     * been de-supported, so expect an error.  This test can be removed when we remove the custom
     * error message in the delegate.
     */
    @Test(expected = ChangeLogParseException)
    void createStoredProcedureEmpty() {
        buildChangeSet {
            createStoredProcedure([:]) {}
        }
    }

    /**
     * test parsing a createStoredProcedure change when we have no attributes just the body in a
     * closure.  This has been de-supported, so expect an error.  This test can be removed when we
     * remove the custom error message in the delegate.
     */
    @Test(expected = ChangeLogParseException)
    void createStoredProcedureClosureOnly() {
        def sql = """\
CREATE OR REPLACE PROCEDURE testMonkey
IS
BEGIN
 -- do something with the monkey
END;"""
        buildChangeSet {
            createStoredProcedure { sql }
        }
    }

    /**
     * Test parsing a createStoredProcedure change when we have no attributes, just the procedure
     * body as a string.  This has been de-supported, so expect an error.  This test can be removed
     * when we remove the custom error message in the delegate.
     */
    @Test(expected = ChangeLogParseException)
    void createStoredProcedureSqlOnlyAsString() {
        def sql = """\
CREATE OR REPLACE PROCEDURE testMonkey
IS
BEGIN
 -- do something with the monkey
END;"""
        buildChangeSet {
            createStoredProcedure sql
        }
    }

    /**
     * Test parsing a createStoredProcedure change when we have both comments and SQL.  This has
     * been de-supported, so expect an error.  This test can be removed when we remove the custom
     * error message in the delegate.
     */
    @Test(expected = ChangeLogParseException)
    void createStoredProcedureFull() {
        def sql = """\
CREATE OR REPLACE PROCEDURE testMonkey
IS
BEGIN
 -- do something with the monkey
END;"""
        buildChangeSet {
            createStoredProcedure(comments: 'someComments') { sql }
        }
    }

    /**
     * Test parsing a createTable change when we have no attributes and an empty closure.  This
     * just makes sure the DSL doesn't add any defaults.  We don't need to support no map or no
     * closure because it makes no sense to have a createTable without at least a name and one
     * column.
     */
    @Test
    void createTableEmpty() {
        buildChangeSet {
            createTable([:]) {}
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof CreateTableChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tablespace
        assertNull changes[0].tableName
        assertNull changes[0].remarks
        assertNull changes[0].tableType
        assertNull changes[0].ifNotExists
        assertNull changes[0].rowDependencies
        assertNotNull changes[0].resourceAccessor

        def columns = changes[0].columns
        assertNotNull columns
        assertEquals 0, columns.size()
        assertNoOutput()
    }

    /**
     * Test parsing a createTable change with all supported attributes and a couple of columns.
     */
    @Test
    void createTableFull() {
        buildChangeSet {
            createTable(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tablespace: 'oracle_tablespace',
                    tableName: 'monkey',
                    remarks: 'angry',
                    tableType: 'rhesus',
                    ifNotExists: true,
                    rowDependencies: false) {
                column(name: 'status', type: 'varchar(100)')
                column(name: 'id', type: 'int')
            }
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof CreateTableChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'oracle_tablespace', changes[0].tablespace
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'angry', changes[0].remarks
        assertEquals 'rhesus', changes[0].tableType
        assertTrue changes[0].ifNotExists
        assertFalse changes[0].rowDependencies
        assertNotNull changes[0].resourceAccessor

        def columns = changes[0].columns
        assertNotNull columns
        assertEquals 2, columns.size()
        assertTrue columns[0] instanceof ColumnConfig
        assertEquals 'status', columns[0].name
        assertEquals 'varchar(100)', columns[0].type
        assertTrue columns[1] instanceof ColumnConfig
        assertEquals 'id', columns[1].name
        assertEquals 'int', columns[1].type
        assertNoOutput()
    }

    /**
     * Test parsing a createTable change with all supported attributes and a couple of columns, but
     * this time, swap the values of the boolean attributes to make sure the last test didn't pass
     * by side effect of default values.
     */
    @Test
    void createTableFullSwapBooleans() {
        buildChangeSet {
            createTable(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tablespace: 'oracle_tablespace',
                    tableName: 'monkey',
                    remarks: 'angry',
                    tableType: 'rhesus',
                    ifNotExists: false,
                    rowDependencies: true) {
                column(name: 'status', type: 'varchar(100)')
                column(name: 'id', type: 'int')
            }
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof CreateTableChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'oracle_tablespace', changes[0].tablespace
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'angry', changes[0].remarks
        assertEquals 'rhesus', changes[0].tableType
        assertFalse changes[0].ifNotExists
        assertTrue changes[0].rowDependencies
        assertNotNull changes[0].resourceAccessor

        def columns = changes[0].columns
        assertNotNull columns
        assertEquals 2, columns.size()
        assertTrue columns[0] instanceof ColumnConfig
        assertEquals 'status', columns[0].name
        assertEquals 'varchar(100)', columns[0].type
        assertTrue columns[1] instanceof ColumnConfig
        assertEquals 'id', columns[1].name
        assertEquals 'int', columns[1].type
        assertNoOutput()
    }

    /**
     * A where clause is not valid for createTable, so try one and make sure it gets rejected.
     */
    @Test(expected = ChangeLogParseException)
    void createTableWithWhereClause() {
        buildChangeSet {
            createTable(catalogName: 'zoo', schemaName: 'animal', tableName: 'monkey') {
                where "invalid"
            }
        }
    }

    /**
     * Test parsing a createView change with an empty attribute map and an empty closure to make
     * sure the DSL doesn't introduce any defaults.
     */
    @Test
    void createViewEmpty() {
        buildChangeSet {
            createView([:]) {}
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof CreateViewChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].viewName
        assertNull changes[0].remarks
        assertNull changes[0].replaceIfExists
        assertNull changes[0].fullDefinition
        assertNull changes[0].path
        assertNull changes[0].encoding
        assertNull changes[0].relativeToChangelogFile
        assertNull changes[0].selectQuery
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropColumn change with no attributes and and empty closure.  This just makes
     * sure the DSL doesn't introduce any unexpected defaults.
     */
    @Test
    void dropColumnEmpty() {
        buildChangeSet {
            dropColumn([:]) {}
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropColumnChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].columnName
        assertEquals 0, changes[0].columns.size()
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }


    /**
     * Test parsing a dropColumn change when we have an invalid method in the closure. This is not
     * allowed and should be caught by the parser.
     */
    @Test(expected = UnrecognizedElement)
    void dropColumnWithWhere() {
        buildChangeSet {
            dropColumn(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnName: 'emotion') {
                where(name: 'emotion')
            }
        }
    }

    /**
     * Test the dropProcedure changeSet with no attributes to make sure the DSL doesn't try to set
     * any defaults.
     */
    @Test
    void dropProcedureEmpty() {
        buildChangeSet {
            dropProcedure([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropProcedureChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].procedureName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropTable change with no attributes to make sure the DSL doesn't introduce
     * any unexpected changes.
     */
    @Test
    void dropTableEmpty() {
        buildChangeSet {
            dropTable([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropTableChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].cascadeConstraints
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropView change with no attributes to make sure the DSL doesn't introduce any
     * unexpected defaults.
     */
    @Test
    void dropViewEmpty() {
        buildChangeSet {
            dropView([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropViewChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].viewName
        assertNull changes[0].ifExists
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a mergeColumn change when there are no attributes to make sure the DSL doesn't
     * introduce unintended defaults.
     */
    @Test
    void mergeColumnsEmpty() {
        buildChangeSet {
            mergeColumns([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof MergeColumnChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].column1Name
        assertNull changes[0].column2Name
        assertNull changes[0].finalColumnName
        assertNull changes[0].finalColumnType
        assertNull changes[0].joinString
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a mergeColumn change when there are no attributes to make sure the DSL doesn't
     * introduce unintended defaults.
     */
    @Test
    void modifyDataTypeEmpty() {
        buildChangeSet {
            modifyDataType([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof ModifyDataTypeChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].columnName
        assertNull changes[0].newDataType
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a renameColumn change when we have no attributes to make sure the DSL doesn't
     * introduce any unintended defaults.
     */
    @Test
    void renameColumnEmpty() {
        buildChangeSet {
            renameColumn([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof RenameColumnChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].oldColumnName
        assertNull changes[0].newColumnName
        assertNull changes[0].columnDataType
        assertNull changes[0].remarks
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }


    /**
     * Test parsing a renameTable change when we have no attributes to make sure we don't get any
     * unintended defautls.
     */
    @Test
    void renameTableEmpty() {
        buildChangeSet {
            renameTable([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof RenameTableChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].oldTableName
        assertNull changes[0].newTableName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a renameView change when we have no attributes to make sure we don't get any
     * unintended defaults.
     */
    @Test
    void renameViewEmpty() {
        buildChangeSet {
            renameView([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof RenameViewChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].oldViewName
        assertNull changes[0].newViewName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

}

