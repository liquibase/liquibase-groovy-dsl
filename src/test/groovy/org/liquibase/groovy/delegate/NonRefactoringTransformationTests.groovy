/*
 * Copyright 2011-2023 Tim Berglund and Steven C. Saliman
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
import liquibase.change.core.DeleteDataChange
import liquibase.change.core.InsertDataChange
import liquibase.change.core.LoadDataChange
import liquibase.change.core.LoadDataColumnConfig
import liquibase.change.core.LoadUpdateDataChange
import liquibase.change.core.OutputChange
import liquibase.change.core.SetColumnRemarksChange
import liquibase.change.core.SetTableRemarksChange
import liquibase.change.core.StopChange
import liquibase.change.core.TagDatabaseChange
import liquibase.change.core.UpdateDataChange
import liquibase.exception.ChangeLogParseException
import liquibase.resource.DirectoryResourceAccessor
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

/**
 * This is one of several classes that test the creation of refactoring changes for ChangeSets. This
 * particular class tests changes that deal with data, such as{@code insert} and {@code delete}.
 * <p>
 * Since the Groovy DSL parser is meant to act as a pass-through for Liquibase itself, it doesn't do
 * much in the way of error checking.  For example, we aren't concerned with whether or not
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
 * a child closure. <p> We don't worry about testing combinations that don't make sense, such as
 * allowing a createIndex change a closure, but no attributes, since it doesn't make sense to have
 * this kind of change without both a table name and at least one column.  If a user tries it, they
 * will get errors from Liquibase itself.
 *
 * @author Steven C. Saliman
 */
class NonRefactoringTransformationTests extends ChangeSetTests {

    /**
     * Test parsing a delete change with no attributes and no where clause.  This just makes sure
     * the DSL doesn't introduce any unexpected defaults.
     */
    @Test
    void deleteDataEmpty() {
        buildChangeSet {
            delete([:]) {}
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DeleteDataChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].where
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a delete change when we have all attributes and a where clause
     */
    @Test
    void deleteDataFull() {
        buildChangeSet {
            delete(catalogName: 'catalog', schemaName: 'schema', tableName: 'monkey') {
                where "emotion='angry' AND active=true"
            }
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DeleteDataChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals "emotion='angry' AND active=true", changes[0].where
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a delete change without a closure. This just means we have no "where" clause,
     * and should be supported.
     */
    @Test
    void deleteDataNoWhereClause() {
        buildChangeSet {
            delete(catalogName: 'catalog', schemaName: 'schema', tableName: 'monkey')
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DeleteDataChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertNull changes[0].where
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a delete change when we have columns in the closure.  This is not allowed and
     * should be caught by the parser.
     */
    @Test(expected = ChangeLogParseException)
    void deleteDataWithColumns() {
        buildChangeSet {
            delete(catalogName: 'catalog', schemaName: 'schema', tableName: 'monkey') {
                column(name: 'emotion')
            }
        }
    }

    /**
     * Process the "empty" change.  This doesn't do anything more than verify that we can have one
     * without blowing up.  Note that
     */
    @Test
    void emptyChange() {
        buildChangeSet {
            empty()
        }
    }

    /**
     * Test parsing an insert change with no attributes and no columns to make sure the DSL doesn't
     * introduce any unexpected defaults.
     */
    @Test
    void insertEmpty() {
        buildChangeSet {
            insert([:]) {}
        }

        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof InsertDataChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].dbms
        assertEquals 0, changes[0].columns.size
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an insert when we have all supported attributes and some columns.  We don't need
     * to worry about columns without attributes or attributes without columns because those
     * scenarios don't make any sense.
     */
    @Test
    void insertFull() {
        def now = '2010-11-02 07:52:04'
        def sqlNow = parseSqlTimestamp(now)
        buildChangeSet {
            insert(catalogName: 'catalog',
                   schemaName: 'schema',
                   tableName: 'monkey',
                   dbms: 'oracle, db2') {
                column(name: 'id', valueNumeric: 502)
                column(name: 'emotion', value: 'angry')
                column(name: 'last_updated', valueDate: now)
                column(name: 'active', valueBoolean: true)
            }
        }

        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof InsertDataChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'oracle, db2', changes[0].dbms
        assertNotNull changes[0].resourceAccessor
        def columns = changes[0].columns
        assertNotNull columns
        assertTrue columns.every { column -> column instanceof ColumnConfig }
        assertEquals 4, columns.size()
        assertEquals 'id', columns[0].name
        assertEquals 502, columns[0].valueNumeric.intValue()
        assertEquals 'emotion', columns[1].name
        assertEquals 'angry', columns[1].value
        assertEquals 'last_updated', columns[2].name
        assertEquals sqlNow, columns[2].valueDate
        assertEquals 'active', columns[3].name
        assertTrue columns[3].valueBoolean
        assertNoOutput()
    }

    /**
     * The insert change allows columns, but not a where clause.  Try an insert
     * with a where clause to make sure it is properly rejected.
     */
    @Test(expected = ChangeLogParseException)
    void insertWithWhereClause() {
        buildChangeSet {
            insert(catalogName: 'catalog',
                   schemaName: 'schema',
                   tableName: 'monkey',
                   dbms: 'oracle, db2') {
                where "invalid"
            }
        }
    }

    /**
     * Test parsing a loadData change when the attribute map and column closure are both empty.  We
     * don't need to worry about the map or the closure being missing because that kind of change
     * doesn't make sense.  In this case, Liquibase itself has defaults for the separator and quote
     * chars, which is what we check in the test.
     */
    @Test
    void loadDataEmpty() {
        buildChangeSet {
            loadData([:]) {}
        }

        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof LoadDataChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].file
        assertNull changes[0].relativeToChangelogFile
        assertNull changes[0].usePreparedStatements
        assertNull changes[0].encoding
        assertEquals ",", changes[0].separator
        assertEquals '"', changes[0].quotchar
        assertEquals "#", changes[0].commentLineStartsWith
        assertNotNull changes[0].resourceAccessor
        def columns = changes[0].columns
        assertNotNull columns
        assertEquals 0, columns.size()
        assertNoOutput()
    }

    /**
     * Test parsing a loadDataChange with all supported attributes and a few columns.  We're not too
     * concerned with the column contents, just make sure we get them, including the extra
     * attributes that are supported for columns in a loadData change.  For this test, we want a
     * separator, quotchar, and commentLineStartsWith that are different from the Liquibase
     * defaults, so we'll go with semi-colon separated, single quoted, and double dash comments.
     * For this test we'll only set the first of the two booleans to true.
     */
    @Test
    void loadDataFullRelative() {
        buildChangeSet {
            loadData(catalogName: 'catalog',
                     schemaName: 'schema',
                     tableName: 'monkey',
                     file: 'data.csv',
                     relativeToChangelogFile: true,
                     usePreparedStatements: false,
                     encoding: 'UTF-8',
                     separator: ';',
                     quotchar: "'",
                     commentLineStartsWith: "--") {
                column(name: 'id', index: 1, header: 'id_header')
                column(name: 'emotion', index: 2, header: 'emotion_header')
            }
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof LoadDataChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'data.csv', changes[0].file
        assertTrue changes[0].relativeToChangelogFile
        assertFalse changes[0].usePreparedStatements
        assertEquals 'UTF-8', changes[0].encoding
        assertEquals ';', changes[0].separator
        assertEquals "'", changes[0].quotchar
        assertEquals "--", changes[0].commentLineStartsWith
        assertNotNull changes[0].resourceAccessor
        def columns = changes[0].columns
        assertNotNull columns
        assertTrue columns.every { column -> column instanceof LoadDataColumnConfig }
        assertEquals 2, columns.size()
        assertEquals 'id', columns[0].name
        assertEquals 1, columns[0].index
        assertEquals 'id_header', columns[0].header
        assertEquals 'emotion', columns[1].name
        assertEquals 2, columns[1].index
        assertEquals 'emotion_header', columns[1].header
        assertNoOutput()
    }

    /**
     * Test parsing a loadDataChange with all supported attributes and a few columns.  We're not too
     * concerned with the column contents, just make sure we get them, including the extra
     * attributes that are supported for columns in a loadData change.  For this test, we want a
     * separator, quotchar, and commentLineStartsWith that are different from the Liquibase
     * defaults, so we'll go with semi-colon separated, single quoted, and double dash comments.
     * For this test we'll only set the second of the two booleans to true.
     */
    @Test
    void loadDataFullPrepared() {
        buildChangeSet {
            loadData(catalogName: 'catalog',
                     schemaName: 'schema',
                     tableName: 'monkey',
                     file: 'data.csv',
                     relativeToChangelogFile: false,
                     usePreparedStatements: true,
                     encoding: 'UTF-8',
                     separator: ';',
                     quotchar: "'",
                     commentLineStartsWith: "--") {
                column(name: 'id', index: 1, header: 'id_header')
                column(name: 'emotion', index: 2, header: 'emotion_header')
            }
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof LoadDataChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'data.csv', changes[0].file
        assertFalse changes[0].relativeToChangelogFile
        assertTrue changes[0].usePreparedStatements
        assertEquals 'UTF-8', changes[0].encoding
        assertEquals ';', changes[0].separator
        assertEquals "'", changes[0].quotchar
        assertEquals "--", changes[0].commentLineStartsWith
        assertNotNull changes[0].resourceAccessor
        def columns = changes[0].columns
        assertNotNull columns
        assertTrue columns.every { column -> column instanceof LoadDataColumnConfig }
        assertEquals 2, columns.size()
        assertEquals 'id', columns[0].name
        assertEquals 1, columns[0].index
        assertEquals 'id_header', columns[0].header
        assertEquals 'emotion', columns[1].name
        assertEquals 2, columns[1].index
        assertEquals 'emotion_header', columns[1].header
        assertNoOutput()
    }

    /**
     * Test parsing a loadData change when the file name is actually a File object.  This was
     * deprecated, so make sure we get the expected error. This test can be removed when we stop
     * explicitly checking this condition in the delegate.
     */
    @Test(expected = ChangeLogParseException)
    void loadDataFullWithFile() {
        buildChangeSet {
            loadData(catalogName: 'catalog',
                     schemaName: 'schema',
                     tableName: 'monkey',
                     file: new File('data.csv'),
                     encoding: 'UTF-8',
                     separator: ';',
                     quotchar: '"') {
                column(name: 'id')
                column(name: 'emotion')
            }
        }
    }

    /**
     * LoadData changes allow columns but not a where clause, so try one that has a where clause to
     * make sure it is properly rejected.
     */
    @Test(expected = ChangeLogParseException)
    void loadDataWithWhereClause() {
        buildChangeSet {
            loadData(catalogName: 'catalog',
                     schemaName: 'schema',
                     tableName: 'monkey',
                     file: 'data.csv',
                     encoding: 'UTF-8',
                     separator: ';',
                     quotchar: "'") {
                where "invalid"
            }
        }
    }

    /**
     * Test parsing a loadData change when the attribute map and column closure are both empty.  We
     * don't need to worry about the map or the closure being missing because that kind of change
     * doesn't make sense.  In this case, Liquibase itself has defaults for the separator and quote
     * chars, which is what we check in the test.
     */
    @Test
    void loadUpdateDataEmpty() {
        buildChangeSet {
            loadUpdateData([:]) {}
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof LoadUpdateDataChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].file
        assertNull changes[0].relativeToChangelogFile
        assertNull changes[0].encoding
        assertEquals ",", changes[0].separator
        assertEquals '"', changes[0].quotchar
        assertNull changes[0].primaryKey
        assertFalse changes[0].onlyUpdate // False is the Lioquibase default
        assertNotNull changes[0].resourceAccessor
        def columns = changes[0].columns
        assertNotNull columns
        assertEquals 0, columns.size()
        assertNoOutput()
    }

    /**
     * Test parsing a loadDataChange with all supported attributes and a few columns.  We're not too
     * concerned with the column contents, just make sure we get them.  For this test, we want a
     * separator and quotchar that is different from the Liquibase defaults, so we'll go with
     * semi-colon separated and single quoted
     */
    @Test
    void loadUpdateDataFull() {
        buildChangeSet {
            loadUpdateData(catalogName: 'catalog',
                           schemaName: 'schema',
                           tableName: 'monkey',
                           file: 'data.csv',
                           relativeToChangelogFile: true,
                           encoding: 'UTF-8',
                           separator: ';',
                           quotchar: "'",
                           primaryKey: 'id',
                           onlyUpdate: true) {
                column(name: 'id')
                column(name: 'emotion')
            }
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof LoadUpdateDataChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'data.csv', changes[0].file
        assertTrue changes[0].relativeToChangelogFile
        assertEquals 'UTF-8', changes[0].encoding
        assertEquals ';', changes[0].separator
        assertEquals "'", changes[0].quotchar
        assertEquals 'id', changes[0].primaryKey
        assertTrue changes[0].onlyUpdate
        assertNotNull changes[0].resourceAccessor
        def columns = changes[0].columns
        assertNotNull columns
        assertTrue columns.every { column -> column instanceof LoadDataColumnConfig }
        assertEquals 2, columns.size()
        assertEquals 'id', columns[0].name
        assertEquals 'emotion', columns[1].name
        assertNoOutput()
    }

    /**
     * Test parsing a loadData change when the file name is actually a File object.  This was
     * removed from the delegate, so make sure we get the expected exception.  This test can be
     * removed when we stop looking for this condition in the delegate.
     */
    @Test(expected = ChangeLogParseException)
    void loadUpdateDataFullWithFile() {
        buildChangeSet {
            loadUpdateData(catalogName: 'catalog',
                           schemaName: 'schema',
                           tableName: 'monkey',
                           file: new File('data.csv'),
                           encoding: 'UTF-8',
                           separator: ';',
                           quotchar: '"') {
                column(name: 'id')
                column(name: 'emotion')
            }
        }
    }

    /**
     * LoadUpdateData changes allow columns but not a where clause, so try one that has a where
     * clause to make sure it is properly rejected.
     */
    @Test(expected = ChangeLogParseException)
    void loadUpdateDataWithWhereClause() {
        buildChangeSet {
            loadUpdateData(catalogName: 'catalog',
                           schemaName: 'schema',
                           tableName: 'monkey',
                           file: 'data.csv',
                           encoding: 'UTF-8',
                           separator: ';',
                           quotchar: "'") {
                where "invalid"
            }
        }
    }

    /**
     * Test an empty output change. Note that we do not expect an empty target. This is because of
     * the workaround for issue #26 where we explicitly set a default value to avoid a Liquibase
     * bug.
     */
    @Test
    void outputEmpty() {
        buildChangeSet {
            output([:])
        }

        assertEquals 1, changeSet.changes.size()
        assertTrue changeSet.changes[0] instanceof OutputChange
        assertNull changeSet.changes[0].message
        assertEquals "STDERR", changeSet.changes[0].target
        assertNotNull changeSet.changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test an output change with all supported properties
     */
    @Test
    void outputFull() {
        buildChangeSet {
            output([message: 'some helpful message',
                    target : 'STDOUT'])
        }

        assertEquals 1, changeSet.changes.size()
        assertTrue changeSet.changes[0] instanceof OutputChange
        assertEquals 'some helpful message', changeSet.changes[0].message
        assertEquals 'STDOUT', changeSet.changes[0].target
        assertNotNull changeSet.changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test an empty setColumnRemarks change
     */
    @Test
    void setColumnRemarksEmpty() {
        buildChangeSet {
            setColumnRemarks([:])
        }
    }

    /**
     * Test an output change with all supported properties
     */
    @Test
    void setColumnRemarksFull() {
        buildChangeSet {
            setColumnRemarks(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnName: 'emotion',
                    remarks: 'some helpful message',
                    columnDataType: 'varchar(100)'
            )
        }

        assertEquals 1, changeSet.changes.size()
        assertTrue changeSet.changes[0] instanceof SetColumnRemarksChange
        assertEquals 'catalog', changeSet.changes[0].catalogName
        assertEquals 'schema', changeSet.changes[0].schemaName
        assertEquals 'monkey', changeSet.changes[0].tableName
        assertEquals 'emotion', changeSet.changes[0].columnName
        assertEquals 'some helpful message', changeSet.changes[0].remarks
        assertEquals 'varchar(100)', changeSet.changes[0].columnDataType
        assertNotNull changeSet.changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test an empty setColumnRemarks change
     */
    @Test
    void setTableRemarksEmpty() {
        buildChangeSet {
            setTableRemarks([:])
        }
    }

    /**
     * Test an output change with all supported properties
     */
    @Test
    void setTableRemarksFull() {
        buildChangeSet {
            setTableRemarks(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    remarks: 'some helpful message'
            )
        }

        assertEquals 1, changeSet.changes.size()
        assertTrue changeSet.changes[0] instanceof SetTableRemarksChange
        assertEquals 'catalog', changeSet.changes[0].catalogName
        assertEquals 'schema', changeSet.changes[0].schemaName
        assertEquals 'monkey', changeSet.changes[0].tableName
        assertEquals 'some helpful message', changeSet.changes[0].remarks
        assertNotNull changeSet.changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a stop change with an empty parameter map.  In this case, we expect Liquibase to
     * give us a default message.
     */
    @Test
    void stopEmpty() {
        buildChangeSet {
            stop([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof StopChange
        assertEquals 'Stop command in changelog file', changes[0].message
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a stop change when the message is in the attributes.
     */
    @Test
    void stopMessageInAttributes() {
        buildChangeSet {
            stop(message: 'Stop the refactoring. Just...stop.')
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof StopChange
        assertEquals 'Stop the refactoring. Just...stop.', changes[0].message
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a stop change when the message is not in an attribute.
     */
    @Test
    void stopMessageIsArgument() {
        buildChangeSet {
            stop 'Stop the refactoring. Just...stop.'
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof StopChange
        assertEquals 'Stop the refactoring. Just...stop.', changes[0].message
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a tagDatabase change when we have no attributes to make sure the DSL doesn't
     * introduce any defaults.
     */
    @Test
    void tagDatabaseEmpty() {
        buildChangeSet {
            tagDatabase([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof TagDatabaseChange
        assertNull changes[0].tag
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a tagDatabase change when we have all supported attributes.
     */
    @Test
    void tagDatabaseNameInAttributes() {
        buildChangeSet {
            tagDatabase(tag: 'monkey')
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof TagDatabaseChange
        assertEquals 'monkey', changes[0].tag
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a tagDatabase change when the name is not in an attribute.
     */
    @Test
    void tagDatabaseNameIsArgument() {
        buildChangeSet {
            tagDatabase 'monkey'
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof TagDatabaseChange
        assertEquals 'monkey', changes[0].tag
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * test parsing an updateData change with no attributes and no closure to make sure the DSL is
     * not adding any unintended defaults.
     */
    @Test
    void updateDataEmpty() {
        buildChangeSet {
            update([:]) {}
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof UpdateDataChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].where
        def columns = changes[0].columns
        assertNotNull columns
        assertEquals 0, columns.size()
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an updateData change when we have all supported attributes, and a couple of
     * columns, but no where clause.  This should not cause an issue, since it is legal to update
     * all rows in a table. As always, we don't care about the contents of the columns.
     */
    @Test
    void updateDataNoWhere() {
        buildChangeSet {
            update(catalogName: 'catalog', schemaName: 'schema', tableName: 'monkey') {
                column(name: 'rfid_tag')
                column(name: 'emotion')
                column(name: 'last_updated')
                column(name: 'active')
            }
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof UpdateDataChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertNull changes[0].where
        assertNotNull changes[0].resourceAccessor
        def columns = changes[0].columns
        assertNotNull columns
        assertTrue columns.every { column -> column instanceof ColumnConfig }
        assertEquals 4, columns.size()
        assertEquals 'rfid_tag', columns[0].name
        assertEquals 'emotion', columns[1].name
        assertEquals 'last_updated', columns[2].name
        assertEquals 'active', columns[3].name
        assertNoOutput()
    }

    /**
     * Test parsing an updateData change when we have attributes, columns and a where clause.  We
     * won't test a where and no columns because that change doesn't make sense, and will be
     * rejected by Liquibase itself.
     */
    @Test
    void updateDataFull() {
        buildChangeSet {
            update(catalogName: 'catalog', schemaName: 'schema', tableName: 'monkey') {
                column(name: 'rfid_tag')
                column(name: 'emotion')
                column(name: 'last_updated')
                column(name: 'active')
                where "id=882"
            }
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof UpdateDataChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'id=882', changes[0].where
        assertNotNull changes[0].resourceAccessor
        def columns = changes[0].columns
        assertNotNull columns
        assertTrue columns.every { column -> column instanceof ColumnConfig }
        assertEquals 4, columns.size()
        assertEquals 'rfid_tag', columns[0].name
        assertEquals 'emotion', columns[1].name
        assertEquals 'last_updated', columns[2].name
        assertEquals 'active', columns[3].name
        assertNoOutput()
    }
}

