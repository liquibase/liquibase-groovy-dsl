/*
 * Copyright 2011-2024 Tim Berglund and Steven C. Saliman
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

import liquibase.change.AddColumnConfig
import liquibase.change.Change
import liquibase.change.ColumnConfig
import liquibase.change.core.AddColumnChange
import liquibase.change.core.CreateTableChange
import liquibase.change.core.DeleteDataChange
import liquibase.change.core.LoadDataChange
import liquibase.change.core.UpdateDataChange
import liquibase.exception.ChangeLogParseException
import liquibase.statement.DatabaseFunction
import liquibase.statement.SequenceCurrentValueFunction
import liquibase.statement.SequenceNextValueFunction
import org.junit.Test
import static org.junit.Assert.*
import java.sql.Timestamp
import liquibase.change.core.LoadDataColumnConfig
import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.DatabaseChangeLog
import java.text.SimpleDateFormat

/**
 * Test class for the {@link ColumnDelegate}.  As usual, we're only verifying that we can pass
 * things to Liquibase correctly. We check all attributes that are known at this time - note that
 * several are undocumented.
 * <p>
 * The columnDelegate also processes "where" and "whereParam" blocks.  For the most tests, we know
 * nothing got set because we use Liquibase's CreateTableChange, which will throw an exception if
 * we try to set values for "where" and "whereParam" erroneously.
 *
 * @author Steven C. Saliman
 */
class ColumnDelegateTests {
    def sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    /**
     * Build a column with no attributes and no closure to make sure we don't introduce any
     * unintended defaults.
     */
    @Test
    void oneColumnEmptyNoClosure() {
        def delegate = buildColumnDelegate(new CreateTableChange(), ColumnConfig.class, ) {
            column([:])
        }

        def change = delegate.change
        def columns = change.columns
        assertEquals 1, columns.size()
        def column = columns[0]

        assertTrue column instanceof ColumnConfig
        assertNull column.name
        assertNull column.computed
        assertNull column.type
        assertNull column.value
        assertNull column.valueNumeric
        assertNull column.valueBoolean
        assertNull column.valueDate
        assertNull column.valueComputed
        assertNull column.valueSequenceNext
        assertNull column.valueSequenceCurrent
        assertNull column.valueBlobFile
        assertNull column.valueClobFile
        assertNull column.defaultValue
        assertNull column.defaultValueNumeric
        assertNull column.defaultValueDate
        assertNull column.defaultValueBoolean
        assertNull column.defaultValueComputed
        assertNull column.defaultValueSequenceNext
        assertNull column.defaultValueConstraintName
        assertNull column.autoIncrement
        assertNull column.startWith
        assertNull column.incrementBy
        assertNull column.defaultOnNull
        assertNull column.generationType
        assertNull column.remarks
        assertNull column.descending
        assertNull column.constraints
    }

    /**
     * Build a column with no attributes and an empty closure to make sure we don't introduce any
     * unintended defaults.  The main difference between this and the no closure version is that
     * the presence of a closure will cause the column to gain constraints with their defaults.
     */
    @Test
    void oneColumnEmptyWithClosure() {
        def delegate = buildColumnDelegate(new CreateTableChange(), ColumnConfig.class) {
            column([:]) {}
        }

        def change = delegate.change
        def columns = change.columns
        assertEquals 1, columns.size()
        def column = columns[0]

        assertTrue column instanceof ColumnConfig
        assertNull column.name
        assertNull column.computed
        assertNull column.type
        assertNull column.value
        assertNull column.valueNumeric
        assertNull column.valueBoolean
        assertNull column.valueDate
        assertNull column.valueComputed
        assertNull column.valueSequenceNext
        assertNull column.valueSequenceCurrent
        assertNull column.valueBlobFile
        assertNull column.valueClobFile
        assertNull column.defaultValue
        assertNull column.defaultValueNumeric
        assertNull column.defaultValueDate
        assertNull column.defaultValueBoolean
        assertNull column.defaultValueComputed
        assertNull column.defaultValueSequenceNext
        assertNull column.defaultValueConstraintName
        assertNull column.autoIncrement
        assertNull column.startWith
        assertNull column.incrementBy
        assertNull column.defaultOnNull
        assertNull column.generationType
        assertNull column.remarks
        assertNull column.descending
        assertNotNull column.constraints
    }

    /**
     * Test creating a column with all currently supported Liquibase attributes.  There are a lot of
     * them, and not all of them are documented. We'd never use all of them at the same time, but
     * we're only concerned with making sure any given attribute is properly passed to Liquibase.
     * Making sure a change is valid from a Liquibase point of view is between Liquibase and the
     * change set author.  Note that care was taken to make sure none of the attribute values match
     * the attribute names.
     */
    @Test
    void oneColumnFull() {
        def dateValue = "2010-11-02 07:52:04"
        def columnDateValue = parseSqlTimestamp(dateValue)
        def defaultDate = "2013-12-31 09:30:04"
        def columnDefaultDate = parseSqlTimestamp(defaultDate)

        def delegate = buildColumnDelegate(new CreateTableChange(), ColumnConfig.class) {
            column(
                    name: 'columnName',
                    computed: true,
                    type: 'timezonetz',
                    value: 'someValue',
                    valueNumeric: 1,
                    valueBoolean: false,
                    valueDate: dateValue,
                    valueComputed: new DatabaseFunction('databaseValue'),
                    valueSequenceNext: new SequenceNextValueFunction('sequenceNext'),
                    valueSequenceCurrent: new SequenceCurrentValueFunction('sequenceCurrent'),
                    valueBlobFile: 'someBlobFile',
                    valueClobFile: 'someClobFile',
                    defaultValue: 'someDefaultValue',
                    defaultValueNumeric: 2,
                    defaultValueDate: defaultDate,
                    defaultValueBoolean: false,
                    defaultValueComputed: new DatabaseFunction("defaultDatabaseValue"),
                    defaultValueSequenceNext: new SequenceNextValueFunction('defaultSequence'),
                    defaultValueConstraintName: 'defaultValueConstraint',
                    autoIncrement: true, // should be the only true.
                    startWith: 3,
                    incrementBy: 4,
                    defaultOnNull: true,
                    generationType: 'someType',
                    remarks: 'No comment',
                    descending: true
            )
        }

        def change = delegate.change
        def columns = change.columns
        assertEquals 1, columns.size()
        def column = columns[0]

        assertTrue column instanceof ColumnConfig
        assertEquals 'columnName', column.name
        assertTrue column.computed
        assertEquals 'timezonetz', column.type
        assertEquals 'someValue', column.value
        assertEquals 1, column.valueNumeric.intValue()
        assertFalse column.valueBoolean
        assertEquals columnDateValue, column.valueDate
        assertEquals 'databaseValue', column.valueComputed.value
        assertEquals 'sequenceNext', column.valueSequenceNext.value
        assertEquals 'sequenceCurrent', column.valueSequenceCurrent.value
        assertEquals 'someBlobFile', column.valueBlobFile
        assertEquals 'someClobFile', column.valueClobFile
        assertEquals 'someDefaultValue', column.defaultValue
        assertEquals 2, column.defaultValueNumeric.intValue()
        assertEquals columnDefaultDate, column.defaultValueDate
        assertFalse column.defaultValueBoolean
        assertEquals 'defaultDatabaseValue', column.defaultValueComputed.value
        assertEquals 'defaultSequence', column.defaultValueSequenceNext.value
        assertEquals 'defaultValueConstraint', column.defaultValueConstraintName
        assertTrue column.autoIncrement
        assertEquals 3G, column.startWith
        assertEquals 4G, column.incrementBy
        assertTrue column.defaultOnNull
        assertEquals 'someType', column.generationType
        assertEquals 'No comment', column.remarks
        assertTrue column.descending
        assertNull column.constraints
    }

    /**
     * Try adding more than one column.  We don't need full columns, we just want to make sure we
     * can handle more than one column. This will also let us isolate the booleans a little better.
     */
    @Test
    void twoColumns() {
        def delegate = buildColumnDelegate(new CreateTableChange(), ColumnConfig.class) {
            // first one has only the boolean value set to true
            column(
                    name: 'first',
                    valueBoolean: true,
                    defaultValueBoolean: false,
                    autoIncrement: false
            )
            // the second one has just the default value set to true.
            column(
                    name: 'second',
                    valueBoolean: false,
                    defaultValueBoolean: true,
                    autoIncrement: false
            )
        }

        def change = delegate.change
        def columns = change.columns
        assertEquals 2, columns.size()

        def column = columns[0]
        assertTrue column instanceof ColumnConfig
        assertEquals 'first', column.name
        assertTrue column.valueBoolean
        assertFalse column.defaultValueBoolean
        assertFalse column.autoIncrement
        assertNull column.constraints

        column = columns[1]
        assertTrue column instanceof ColumnConfig
        assertEquals 'second', column.name
        assertFalse column.valueBoolean
        assertTrue column.defaultValueBoolean
        assertFalse column.autoIncrement
        assertNull column.constraints

    }

    /**
     * Try a column that contains a constraint.  We're not concerned with the contents of the
     * constraint, just that the closure could be called, and the contents added to the column.
     */
    @Test
    void columnWithConstraint() {
        def delegate = buildColumnDelegate(new CreateTableChange(), ColumnConfig.class) {
            // first one has only the boolean value set to true
            column(name: 'first', type: 'int') {
                constraints(nullable: false, unique: true)
            }
        }

        def change = delegate.change
        def columns = change.columns
        assertEquals 1, columns.size()
        def column = columns[0]

        assertTrue column instanceof ColumnConfig
        assertEquals 'first', column.name
        assertEquals 'int', column.type
        assertNotNull column.constraints
        assertFalse column.constraints.nullable
        assertTrue column.constraints.unique
    }

    /**
     * Test creating an "addColumn" column with all currently supported Liquibase attributes. An
     * "addColumn" column is the same as a normal column, but adds 3 new attributes for use in an
     * "addColumn" change.  Let's repeat the{@link #oneColumnFull()} test, but change the type of
     * column to create to make sure we can set the 3 new attributes.  This is the only "addColumn"
     * test we'll have since there is not any code in the Delegate itself that does anything
     * different for "addColumn" columns.  It makes a different type of object because the caller
     * tells it to.
     */
    @Test
    void oneAddColumnFull() {
        def dateValue = "2010-11-02 07:52:04"
        def columnDateValue = parseSqlTimestamp(dateValue)
        def defaultDate = "2013-12-31 09:30:04"
        def columnDefaultDate = parseSqlTimestamp(defaultDate)

        def delegate = buildColumnDelegate(new AddColumnChange(), AddColumnConfig.class) {
            column(
                    name: 'columnName',
                    computed: false,
                    type: 'varchar(30)',
                    value: 'someValue',
                    valueNumeric: 1,
                    valueBoolean: false,
                    valueDate: dateValue,
                    valueComputed: new DatabaseFunction('databaseValue'),
                    valueSequenceNext: new SequenceNextValueFunction('sequenceNext'),
                    valueSequenceCurrent: new SequenceCurrentValueFunction('sequenceCurrent'),
                    valueBlobFile: 'someBlobFile',
                    valueClobFile: 'someClobFile',
                    defaultValue: 'someDefaultValue',
                    defaultValueNumeric: 2,
                    defaultValueDate: defaultDate,
                    defaultValueBoolean: false,
                    defaultValueComputed: new DatabaseFunction("defaultDatabaseValue"),
                    defaultValueSequenceNext: new SequenceNextValueFunction('defaultSequence'),
                    defaultValueConstraintName: 'defaultValueConstraint',
                    autoIncrement: true, // should be the only true.
                    startWith: 3,
                    incrementBy: 4,
                    defaultOnNull: true,
                    generationType: 'someType',
                    remarks: 'No comment',
                    descending: false,
                    beforeColumn: 'before',
                    afterColumn: 'after',
                    position: 5
            )
        }

        def change = delegate.change
        def columns = change.columns
        assertEquals 1, columns.size()
        def column = columns[0]

        assertTrue column instanceof AddColumnConfig
        assertEquals 'columnName', column.name
        assertFalse column.computed
        assertEquals 'varchar(30)', column.type
        assertEquals 'someValue', column.value
        assertEquals 1, column.valueNumeric.intValue()
        assertFalse column.valueBoolean
        assertEquals columnDateValue, column.valueDate
        assertEquals 'databaseValue', column.valueComputed.value
        assertEquals 'sequenceNext', column.valueSequenceNext.value
        assertEquals 'sequenceCurrent', column.valueSequenceCurrent.value
        assertEquals 'someBlobFile', column.valueBlobFile
        assertEquals 'someClobFile', column.valueClobFile
        assertEquals 'someDefaultValue', column.defaultValue
        assertEquals 2, column.defaultValueNumeric.intValue()
        assertEquals columnDefaultDate, column.defaultValueDate
        assertFalse column.defaultValueBoolean
        assertEquals 'defaultDatabaseValue', column.defaultValueComputed.value
        assertEquals 'defaultSequence', column.defaultValueSequenceNext.value
        assertEquals 'defaultValueConstraint', column.defaultValueConstraintName
        assertTrue column.autoIncrement
        assertEquals 3G, column.startWith
        assertEquals 4G, column.incrementBy
        assertTrue column.defaultOnNull
        assertEquals 'someType', column.generationType
        assertEquals 'No comment', column.remarks
        assertFalse column.descending
        assertEquals 'before', column.beforeColumn
        assertEquals 'after', column.afterColumn
        assertEquals 5, column.position
        assertNull column.constraints
    }

    /**
     * Test creating a "loadData" column with all currently supported Liquibase attributes. A
     * "loadData" column is the same as a normal column, but adds 2 new attributes.  Let's repeat
     * the {@link #oneColumnFull()} test, but change the type of column to create to make sure we
     * can set the 2 new attributes.  This is the only "loadData" test we'll have since there is
     * not any code in the Delegate itself that does anything different for "loadData" columns.  It
     * makes a different type of object because the caller tells it to.
     */
    @Test
    void oneLoadDataColumnFull() {
        def dateValue = "2010-11-02 07:52:04"
        def columnDateValue = parseSqlTimestamp(dateValue)
        def defaultDate = "2013-12-31 09:30:04"
        def columnDefaultDate = parseSqlTimestamp(defaultDate)

        def delegate = buildColumnDelegate(new LoadDataChange(), LoadDataColumnConfig.class) {
            column(
                    name: 'columnName',
                    computed: true,
                    type: 'STRING',  // As of LB 4.4.0, this needs to match an enum value.
                    value: 'someValue',
                    valueNumeric: 1,
                    valueBoolean: false,
                    valueDate: dateValue,
                    valueComputed: new DatabaseFunction('databaseValue'),
                    valueSequenceNext: new SequenceNextValueFunction('sequenceNext'),
                    valueSequenceCurrent: new SequenceCurrentValueFunction('sequenceCurrent'),
                    valueBlobFile: 'someBlobFile',
                    valueClobFile: 'someClobFile',
                    defaultValue: 'someDefaultValue',
                    defaultValueNumeric: 2,
                    defaultValueDate: defaultDate,
                    defaultValueBoolean: false,
                    defaultValueComputed: new DatabaseFunction("defaultDatabaseValue"),
                    defaultValueSequenceNext: new SequenceNextValueFunction('defaultSequence'),
                    defaultValueConstraintName: 'defaultValueConstraint',
                    autoIncrement: true, // should be the only true.
                    startWith: 3,
                    incrementBy: 4,
                    defaultOnNull: true,
                    generationType: 'someType',
                    remarks: 'No comment',
                    descending: false,
                    header: 'columnHeader',
                    index: 5,
                    allowUpdate: false
            )
        }

        def change = delegate.change
        def columns = change.columns
        assertEquals 1, columns.size()
        def column = columns[0]

        assertTrue column instanceof LoadDataColumnConfig
        assertEquals 'columnName', column.name
        assertTrue column.computed
        assertEquals 'STRING', column.type
        assertEquals 'someValue', column.value
        assertEquals 1, column.valueNumeric.intValue()
        assertFalse column.valueBoolean
        assertEquals columnDateValue, column.valueDate
        assertEquals 'databaseValue', column.valueComputed.value
        assertEquals 'sequenceNext', column.valueSequenceNext.value
        assertEquals 'sequenceCurrent', column.valueSequenceCurrent.value
        assertEquals 'someBlobFile', column.valueBlobFile
        assertEquals 'someClobFile', column.valueClobFile
        assertEquals 'someDefaultValue', column.defaultValue
        assertEquals 2, column.defaultValueNumeric.intValue()
        assertEquals columnDefaultDate, column.defaultValueDate
        assertFalse column.defaultValueBoolean
        assertEquals 'defaultDatabaseValue', column.defaultValueComputed.value
        assertEquals 'defaultSequence', column.defaultValueSequenceNext.value
        assertEquals 'defaultValueConstraint', column.defaultValueConstraintName
        assertTrue column.autoIncrement
        assertEquals 3G, column.startWith
        assertEquals 4G, column.incrementBy
        assertTrue column.defaultOnNull
        assertEquals 'someType', column.generationType
        assertEquals 'No comment', column.remarks
        assertFalse column.descending
        assertEquals 'columnHeader', column.header
        assertEquals 5, column.index
        assertFalse column.allowUpdate
        assertNull column.constraints
    }

    /**
     * Test a column closure that has a where clause.  For this test, we need a class that supports
     * where clauses.
     */
    @Test
    void columnClosureCanContainWhereClause() {
        def delegate = buildColumnDelegate(new UpdateDataChange(), ColumnConfig.class) {
            column(name: 'monkey', type: 'VARCHAR(50)')
            where "emotion='angry'"
        }

        def change = delegate.change
        def columns = change.columns
        assertEquals 1, columns.size()
        def column = columns[0]

        assertTrue column instanceof ColumnConfig
        assertEquals 'monkey', column.name
        assertEquals "emotion='angry'", change.where
        assertEquals 0, change.whereParams.size()
    }

    /**
     * Test a column closure that has a where clause.  For this test, we need a class that supports
     * where clauses.  This test is the same as the last one, except we'll add some whereParams to
     * the mix.
     */
    @Test
    void columnClosureCanContainWhereClauseWithParams() {
        def delegate = buildColumnDelegate(new UpdateDataChange(), ColumnConfig.class) {
            column(name: 'monkey', type: 'VARCHAR(50)')
            where "emotion=':emotion' and day=':monday'"
            whereParams {
                param(name: 'emotion', value: 'angry')
                param(name: 'day', value: 'monday')
            }
        }

        def change = delegate.change
        def columns = change.columns
        assertEquals 1, columns.size()
        def column = columns[0]

        assertTrue column instanceof ColumnConfig
        assertEquals 'monkey', column.name
        assertEquals "emotion=':emotion' and day=':monday'", change.where

        assertEquals 2, change.whereParams.size()

        column = change.whereParams[0]
        assertEquals "emotion", column.name
        assertEquals "angry", column.value
    }

    /**
     * {@code delete} changes will have a where clause, but no actual columns.  Make sure we can
     * handle this.  We'll also use this test to put every documented attribute of a whereParam to
     * make sure we handle them all
     */
    @Test
    void columnClosureIsJustWhereClauseWithParams() {
        def dateValue = "2010-11-02 07:52:04"
        def columnDateValue = parseSqlTimestamp(dateValue)

        def delegate = buildColumnDelegate(new DeleteDataChange(), ColumnConfig.class) {
            where "emotion=':emotion'"
            whereParams{
                param(name: 'emotion',
                      value: 'angry',
                      valueNumeric: 1,
                      valueBoolean: false,
                      valueDate: dateValue,
                      valueComputed: new DatabaseFunction('databaseValue'),
                      valueSequenceNext: new SequenceNextValueFunction('sequenceNext'),
                      valueSequenceCurrent: new SequenceCurrentValueFunction('sequenceCurrent')
                )
            }
        }

        def change = delegate.change
        assertEquals "emotion=':emotion'", change.where
        assertEquals 1, change.whereParams.size()

        def column = change.whereParams[0]
        assertEquals "emotion", column.name
        assertEquals "angry", column.value
        assertEquals 1, column.valueNumeric.intValue()
        assertFalse column.valueBoolean
        assertEquals columnDateValue, column.valueDate
        assertEquals 'databaseValue', column.valueComputed.value
        assertEquals 'sequenceNext', column.valueSequenceNext.value
        assertEquals 'sequenceCurrent', column.valueSequenceCurrent.value
    }

    /**
     * Try using a "where" clause in a change that doesn't support them, like the CreateTableChange.
     * Expect a ChangeLogParseException.
     */
    @Test(expected = ChangeLogParseException)
    void invalidUseOfWhere() {
        buildColumnDelegate(new CreateTableChange(), ColumnConfig.class) {
            column(name: 'monkey', type: 'VARCHAR(50)')
            where "emotion='angry'"
        }

    }

    /**
     * Try using a "whereParams" clause in a change that doesn't support them, like the
     * CreateTableChange. Expect a ChangeLogParseException.
     */
    @Test(expected = ChangeLogParseException)
    void invalidUseOfWhereParams() {
        buildColumnDelegate(new CreateTableChange(), ColumnConfig.class) {
            column(name: 'monkey', type: 'VARCHAR(50)')
            whereParams {
                param(name: 'emotion', value: 'angry')
            }
        }

    }

    /**
     * Try an invalid method in the closure to make sure we get our ChangeLogParseException instead
     * of the standard MissingMethodException.
     */
    @Test(expected = ChangeLogParseException)
    void invalidMethodInClosure() {
        buildColumnDelegate(new CreateTableChange(), ColumnConfig.class) {
            table(name: 'monkey')
        }
    }

    /**
     * Try building a column when it contains an invalid attribute.  Do we get a
     * ChangeLogParseException, which will have our pretty message? We try to trick the system by
     * using what is a valid "loadData" column attribute on a normal ColumnConfig.
     */
    @Test(expected = ChangeLogParseException)
    void columnWithInvalidAttribute() {
        buildColumnDelegate(new CreateTableChange(), ColumnConfig.class) {
            column(header: 'invalid')
        }
    }

    /**
     * helper method to build and execute a ColumnDelegate.
     * @param change the change that will be modified by the delegate.
     * @param columnConfigClass the type of ColumnConfig used by the change.
     * @param closure the closure to execute with our column attributes.
     * @return the new delegate.
     */
    private def buildColumnDelegate(Change change, Class columnConfigClass, Closure closure) {
        def changelog = new DatabaseChangeLog()
        changelog.changeLogParameters = new ChangeLogParameters()
        def columnDelegate = new ColumnDelegate(
                columnConfigClass: columnConfigClass,
                databaseChangeLog: changelog,
                changeSetId: 'test-change-set',
                changeName: 'test-change',
                change: change
        )
        closure.delegate = columnDelegate
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()

        return columnDelegate
    }

    /**
     * Helper method to parse a string into a date.
     * @param dateTimeString the string to parse
     * @return the parsed string
     */
    private Timestamp parseSqlTimestamp(dateTimeString) {
        new Timestamp(sdf.parse(dateTimeString).time)
    }
}
