/*
 * Copyright 2011-2022 Tim Berglund and Steven C. Saliman
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

import liquibase.change.core.RenameSequenceChange
import org.junit.Test
import static org.junit.Assert.*

import liquibase.change.core.AddLookupTableChange
import liquibase.change.core.AddNotNullConstraintChange
import liquibase.change.core.AlterSequenceChange;
import liquibase.change.core.DropNotNullConstraintChange
import liquibase.change.core.AddUniqueConstraintChange
import liquibase.change.core.DropUniqueConstraintChange
import liquibase.change.core.CreateSequenceChange
import liquibase.change.core.DropSequenceChange
import liquibase.change.core.AddAutoIncrementChange
import liquibase.change.core.AddDefaultValueChange
import liquibase.change.core.DropDefaultValueChange
import liquibase.statement.DatabaseFunction

/**
 * This is one of several classes that test the creation of refactoring changes for ChangeSets. This
 * particular class tests changes that deal with data quality.
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
 * a child closure.
 * <p>
 * We don't worry about testing combinations that don't make sense, such as allowing a createIndex
 * change a closure, but no attributes, since it doesn't make sense to have this kind of change
 * without both a table name and at least one column.  If a user tries it, they will get errors
 * from Liquibase itself.
 *
 * @author Tim Berglund
 * @author Steven C. Saliman
 */
class DataQualityRefactoringTests extends ChangeSetTests {

    /**
     * Test the addAutoIncrement changeSet with no attributes to make sure the DSL doesn't try to
     * set any defaults.
     */
    @Test
    void addAutoIncrementEmpty() {
        buildChangeSet {
            addAutoIncrement([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddAutoIncrementChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].columnName
        assertNull changes[0].columnDataType
        assertNull changes[0].startWith
        assertNull changes[0].incrementBy
        assertNull changes[0].defaultOnNull // The change uses an Object Boolean.
        assertNull changes[0].generationType
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test the addAutoIncrement change set.
     */
    @Test
    void addAutoIncrementFull() {
        buildChangeSet {
            addAutoIncrement(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnName: 'angry',
                    columnDataType: 'boolean',
                    startWith: 10,
                    incrementBy: 5,
                    defaultOnNull: true,
                    generationType: 'magic'
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddAutoIncrementChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'angry', changes[0].columnName
        assertEquals 'boolean', changes[0].columnDataType
        assertEquals 10G, changes[0].startWith
        assertEquals 5G, changes[0].incrementBy
        assertTrue changes[0].defaultOnNull
        assertEquals 'magic', changes[0].generationType
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Validate the creation of an addDefaultValue change when there are no attributes set.  Make
     * sure the DSL didn't make up values.
     */
    @Test
    void addDefaultValueEmpty() {
        buildChangeSet {
            addDefaultValue([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddDefaultValueChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].columnName
        assertNull changes[0].columnDataType
        assertNull changes[0].defaultValue
        assertNull changes[0].defaultValueBoolean
        // it's an Object, so it can be null
        assertNull changes[0].defaultValueComputed
        assertNull changes[0].defaultValueDate
        assertNull changes[0].defaultValueNumeric
        assertNull changes[0].defaultValueSequenceNext
        assertNull changes[0].defaultValueConstraintName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test the creation of an addDefaultValue change when all attributes are set.  Remember, the
     * DSL doesn't do any validation - Liquibase does.  We only care that the DSL sets the proper
     * values in the Liquibase object from the attribute map.
     */
    @Test
    void addDefaultValueFull() {
        buildChangeSet {
            addDefaultValue(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnName: 'strength',
                    columnDataType: 'int',
                    defaultValue: 'extremely',
                    defaultValueBoolean: true,
                    defaultValueComputed: 'max',
                    defaultValueDate: '20101109T130400Z',
                    defaultValueNumeric: '2.718281828459045',
                    defaultValueSequenceNext: 'sequence',
                    defaultValueConstraintName: 'monkey_strength_default'
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddDefaultValueChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'strength', changes[0].columnName
        assertEquals 'int', changes[0].columnDataType
        assertEquals 'extremely', changes[0].defaultValue
        assertTrue changes[0].defaultValueBoolean
        assertEquals new DatabaseFunction('max'), changes[0].defaultValueComputed
        assertEquals '20101109T130400Z', changes[0].defaultValueDate
        assertEquals '2.718281828459045', changes[0].defaultValueNumeric
        assertEquals 'sequence', changes[0].defaultValueSequenceNext.value
        assertEquals 'monkey_strength_default', changes[0].defaultValueConstraintName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Parse an addLookupTable change with no attributes to make sure the DSL doesn't make up any
     * defaults.
     */
    @Test
    void addLookupTableEmpty() {
        buildChangeSet {
            addLookupTable([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddLookupTableChange
        assertNull changes[0].existingTableCatalogName
        assertNull changes[0].existingTableSchemaName
        assertNull changes[0].existingTableName
        assertNull changes[0].existingColumnName
        assertNull changes[0].newTableCatalogName
        assertNull changes[0].newTableSchemaName
        assertNull changes[0].newTableName
        assertNull changes[0].newColumnName
        assertNull changes[0].newColumnDataType
        assertNull changes[0].constraintName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Parse an addLookupTable change with all supported attributes set.
     */
    @Test
    void addLookupTableFull() {
        buildChangeSet {
            addLookupTable(
                    existingTableCatalogName: 'old_catalog',
                    existingTableSchemaName: 'old_schema',
                    existingTableName: 'monkey',
                    existingColumnName: 'emotion',
                    newTableCatalogName: 'new_catalog',
                    newTableSchemaName: 'new_schema',
                    newTableName: 'monkey_emotion',
                    newColumnName: 'emotion_display',
                    newColumnDataType: 'varchar(50)',
                    constraintName: 'fk_monkey_emotion'
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddLookupTableChange
        assertEquals 'old_catalog', changes[0].existingTableCatalogName
        assertEquals 'old_schema', changes[0].existingTableSchemaName
        assertEquals 'monkey', changes[0].existingTableName
        assertEquals 'emotion', changes[0].existingColumnName
        assertEquals 'new_catalog', changes[0].newTableCatalogName
        assertEquals 'new_schema', changes[0].newTableSchemaName
        assertEquals 'monkey_emotion', changes[0].newTableName
        assertEquals 'emotion_display', changes[0].newColumnName
        assertEquals 'varchar(50)', changes[0].newColumnDataType
        assertEquals 'fk_monkey_emotion', changes[0].constraintName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Parse an addNotNullConstraint with no attributes to make sure the DSL doesn't make up any
     * defaults.
     */
    @Test
    void addNotNullConstraintEmpty() {
        buildChangeSet {
            addNotNullConstraint([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddNotNullConstraintChange
        assertNull changes[0].constraintName
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].columnName
        assertNull changes[0].defaultNullValue
        assertNull changes[0].columnDataType
        assertNull changes[0].validate
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Parse an addNotNullConstraint with all supported options set.
     */
    @Test
    void addNotNullConstraintFull() {
        buildChangeSet {
            addNotNullConstraint(
                    constraintName: 'monkey_emotion_nn',
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnName: 'emotion',
                    defaultNullValue: 'angry',
                    columnDataType: 'varchar(75)',
                    validate: true
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddNotNullConstraintChange
        assertEquals 'monkey_emotion_nn', changes[0].constraintName
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'emotion', changes[0].columnName
        assertEquals 'angry', changes[0].defaultNullValue
        assertEquals 'varchar(75)', changes[0].columnDataType
        assertTrue changes[0].validate
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an addUniqueConstraint change with no attributes to make sure the DSL doesn't
     * create any default values.
     */
    @Test
    void addUniqueConstraintEmpty() {
        buildChangeSet {
            addUniqueConstraint([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddUniqueConstraintChange
        assertNull changes[0].tablespace
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].columnNames
        assertNull changes[0].constraintName
        assertNull changes[0].deferrable
        assertNull changes[0].initiallyDeferred
        assertNull changes[0].disabled
        assertNull changes[0].forIndexCatalogName
        assertNull changes[0].forIndexSchemaName
        assertNull changes[0].forIndexName
        assertNull changes[0].validate
        assertNull changes[0].clustered
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an addUniqueConstraint change when we have all supported options.  There are 5
     * booleans here, so to isolate the attributes, this test will only set deferrable to true.
     */
    @Test
    void addUniqueConstraintFullDeferrable() {
        buildChangeSet {
            addUniqueConstraint(
                    tablespace: 'tablespace',
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnNames: 'species, emotion',
                    constraintName: 'unique_constraint',
                    deferrable: true,
                    initiallyDeferred: false,
                    disabled: false,
                    forIndexCatalogName: 'index_catalog',
                    forIndexSchemaName: 'index_schema',
                    forIndexName: 'unique_constraint_idx',
                    validate: false,
                    clustered: false
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddUniqueConstraintChange
        assertEquals 'tablespace', changes[0].tablespace
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'species, emotion', changes[0].columnNames
        assertEquals 'unique_constraint', changes[0].constraintName
        assertTrue changes[0].deferrable
        assertFalse changes[0].initiallyDeferred
        assertFalse changes[0].disabled
        assertEquals 'index_catalog', changes[0].forIndexCatalogName
        assertEquals 'index_schema', changes[0].forIndexSchemaName
        assertEquals 'unique_constraint_idx', changes[0].forIndexName
        assertFalse changes[0].validate
        assertFalse changes[0].clustered
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an addUniqueConstraint change when we have all supported options.  There are 5
     * booleans here, so to isolate the attributes, this test will only set initiallyDeferred to
     * true.
     */
    @Test
    void addUniqueConstraintFullDeferred() {
        buildChangeSet {
            addUniqueConstraint(
                    tablespace: 'tablespace',
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnNames: 'species, emotion',
                    constraintName: 'unique_constraint',
                    deferrable: false,
                    initiallyDeferred: true,
                    disabled: false,
                    forIndexCatalogName: 'index_catalog',
                    forIndexSchemaName: 'index_schema',
                    forIndexName: 'unique_constraint_idx',
                    validate: false,
                    clustered: false
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddUniqueConstraintChange
        assertEquals 'tablespace', changes[0].tablespace
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'species, emotion', changes[0].columnNames
        assertEquals 'unique_constraint', changes[0].constraintName
        assertFalse changes[0].deferrable
        assertTrue changes[0].initiallyDeferred
        assertFalse changes[0].disabled
        assertEquals 'index_catalog', changes[0].forIndexCatalogName
        assertEquals 'index_schema', changes[0].forIndexSchemaName
        assertEquals 'unique_constraint_idx', changes[0].forIndexName
        assertFalse changes[0].validate
        assertFalse changes[0].clustered
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an addUniqueConstraint change when we have all supported options.  There are 5
     * booleans here, so to isolate the attributes, this test will only set deferrable to true.
     */
    @Test
    void addUniqueConstraintFullDisabled() {
        buildChangeSet {
            addUniqueConstraint(
                    tablespace: 'tablespace',
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnNames: 'species, emotion',
                    constraintName: 'unique_constraint',
                    deferrable: false,
                    initiallyDeferred: false,
                    disabled: true,
                    forIndexCatalogName: 'index_catalog',
                    forIndexSchemaName: 'index_schema',
                    forIndexName: 'unique_constraint_idx',
                    validate: false,
                    clustered: false
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddUniqueConstraintChange
        assertEquals 'tablespace', changes[0].tablespace
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'species, emotion', changes[0].columnNames
        assertEquals 'unique_constraint', changes[0].constraintName
        assertFalse changes[0].deferrable
        assertFalse changes[0].initiallyDeferred
        assertTrue changes[0].disabled
        assertEquals 'index_catalog', changes[0].forIndexCatalogName
        assertEquals 'index_schema', changes[0].forIndexSchemaName
        assertEquals 'unique_constraint_idx', changes[0].forIndexName
        assertFalse changes[0].validate
        assertFalse changes[0].clustered
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an addUniqueConstraint change when we have all supported options.  There are 5
     * booleans here, so to isolate the attributes, this test will only set validate to true.
     */
    @Test
    void addUniqueConstraintFullValidate() {
        buildChangeSet {
            addUniqueConstraint(
                    tablespace: 'tablespace',
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnNames: 'species, emotion',
                    constraintName: 'unique_constraint',
                    deferrable: false,
                    initiallyDeferred: false,
                    disabled: false,
                    forIndexCatalogName: 'index_catalog',
                    forIndexSchemaName: 'index_schema',
                    forIndexName: 'unique_constraint_idx',
                    validate: true,
                    clustered: false
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddUniqueConstraintChange
        assertEquals 'tablespace', changes[0].tablespace
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'species, emotion', changes[0].columnNames
        assertEquals 'unique_constraint', changes[0].constraintName
        assertFalse changes[0].deferrable
        assertFalse changes[0].initiallyDeferred
        assertFalse changes[0].disabled
        assertEquals 'index_catalog', changes[0].forIndexCatalogName
        assertEquals 'index_schema', changes[0].forIndexSchemaName
        assertEquals 'unique_constraint_idx', changes[0].forIndexName
        assertTrue changes[0].validate
        assertFalse changes[0].clustered
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an addUniqueConstraint change when we have all supported options.  There are 5
     * booleans here, so to isolate the attributes, this test will only set clustered to true.
     */
    @Test
    void addUniqueConstraintFullClustered() {
        buildChangeSet {
            addUniqueConstraint(
                    tablespace: 'tablespace',
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnNames: 'species, emotion',
                    constraintName: 'unique_constraint',
                    deferrable: false,
                    initiallyDeferred: false,
                    disabled: false,
                    forIndexCatalogName: 'index_catalog',
                    forIndexSchemaName: 'index_schema',
                    forIndexName: 'unique_constraint_idx',
                    validate: false,
                    clustered: true
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddUniqueConstraintChange
        assertEquals 'tablespace', changes[0].tablespace
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'species, emotion', changes[0].columnNames
        assertEquals 'unique_constraint', changes[0].constraintName
        assertFalse changes[0].deferrable
        assertFalse changes[0].initiallyDeferred
        assertFalse changes[0].disabled
        assertEquals 'index_catalog', changes[0].forIndexCatalogName
        assertEquals 'index_schema', changes[0].forIndexSchemaName
        assertEquals 'unique_constraint_idx', changes[0].forIndexName
        assertFalse changes[0].validate
        assertTrue changes[0].clustered
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an alterSequence change with no attributes to make sure the DSL doesn't create
     * default values
     */
    @Test
    void alterSequenceEmpty() {
        buildChangeSet {
            alterSequence([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AlterSequenceChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].sequenceName
        assertNull changes[0].incrementBy
        assertNull changes[0].minValue
        assertNull changes[0].maxValue
        assertNull changes[0].ordered // it is an Object and can be null.
        assertNull changes[0].cacheSize
        assertNull changes[0].cycle
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an alterSequence change with all supported attributes present.
     */
    @Test
    void alterSequenceFull() {
        buildChangeSet {
            alterSequence(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    sequenceName: 'seq',
                    incrementBy: 314,
                    minValue: 300,
                    maxValue: 400,
                    ordered: true,
                    cacheSize: 10,
                    cycle: true,
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AlterSequenceChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'seq', changes[0].sequenceName
        assertEquals 314G, changes[0].incrementBy
        assertEquals 300G, changes[0].minValue
        assertEquals 400G, changes[0].maxValue
        assertTrue changes[0].ordered
        assertEquals 10G, changes[0].cacheSize
        assertTrue changes[0].cycle
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a createSequence change with no attributes to make sure the
     * DSL doesn't create any defaults.
     */
    @Test
    void createSequenceEmpty() {
        buildChangeSet {
            createSequence([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof CreateSequenceChange
        assertNull changes[0].sequenceName
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].startValue
        assertNull changes[0].incrementBy
        assertNull changes[0].minValue
        assertNull changes[0].maxValue
        assertNull changes[0].ordered
        assertNull changes[0].cycle
        assertNull changes[0].cacheSize
        assertNull changes[0].dataType
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a createSequence change with all attributes present to make sure they all go to
     * the right place.
     */
    @Test
    void createSequenceFull() {
        buildChangeSet {
            createSequence(
                    catalogName: 'catalog',
                    sequenceName: 'sequence',
                    schemaName: 'schema',
                    startValue: 8,
                    incrementBy: 42,
                    minValue: 7,
                    maxValue: 6.023E24,
                    ordered: true,
                    cycle: false,
                    cacheSize: 314,
                    dataType: 'Number'
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof CreateSequenceChange
        assertEquals 'sequence', changes[0].sequenceName
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 42G, changes[0].incrementBy
        assertEquals 7G, changes[0].minValue
        assertEquals 6023000000000000000000000, changes[0].maxValue
        assertEquals 8G, changes[0].startValue
        assertTrue changes[0].ordered
        assertFalse changes[0].cycle
        assertEquals 314G, changes[0].cacheSize
        assertEquals 'Number', changes[0].dataType
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropDefaultValue change with no attributes to make sure the DSL doesn't
     * introduce any unexpected defaults.
     */
    @Test
    void dropDefaultValueEmpty() {
        buildChangeSet {
            dropDefaultValue([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropDefaultValueChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].columnName
        assertNull changes[0].columnDataType
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropDefaultValue change with all supported attributes
     */
    @Test
    void dropDefaultValueFull() {
        buildChangeSet {
            dropDefaultValue(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnName: 'emotion',
                    columnDataType: 'varchar'
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropDefaultValueChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'emotion', changes[0].columnName
        assertEquals 'varchar', changes[0].columnDataType
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropNotNullConstraint change with no attributes to make sure the DSL doesn't
     * introduce unexpected defaults.
     */
    @Test
    void dropNotNullConstraintEmpty() {
        buildChangeSet {
            dropNotNullConstraint([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropNotNullConstraintChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].columnName
        assertNull changes[0].columnDataType
        assertNull changes[0].constraintName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropNotNullConstraint with all supported attributes.
     */
    @Test
    void dropNotNullConstraintFull() {
        buildChangeSet {
            dropNotNullConstraint(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnName: 'emotion',
                    columnDataType: 'varchar(75)',
                    constraintName: 'nn_monkey_emotion')
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropNotNullConstraintChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'emotion', changes[0].columnName
        assertEquals 'varchar(75)', changes[0].columnDataType
        assertEquals 'nn_monkey_emotion', changes[0].constraintName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropSequence change with no attributes to make sure the DSL doesn't introduce
     * unexpected defaults.
     */
    @Test
    void dropSequenceEmpty() {
        buildChangeSet {
            dropSequence([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropSequenceChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].sequenceName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropSequence change with all supported attributes.
     */
    @Test
    void dropSequenceFull() {
        buildChangeSet {
            dropSequence(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    sequenceName: 'sequence'
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropSequenceChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'sequence', changes[0].sequenceName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropUniqueConstraint change with no attributes to make sure the DSL doesn't
     * introduce any unexpected defaults.
     */
    @Test
    void dropUniqueConstraintEmpty() {
        buildChangeSet {
            dropUniqueConstraint([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropUniqueConstraintChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].constraintName
        assertNull changes[0].uniqueColumns
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropUniqueConstraint change with all supported options
     */
    @Test
    void dropUniqueConstraintFull() {
        buildChangeSet {
            dropUniqueConstraint(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'table',
                    constraintName: 'unique_constraint',
                    uniqueColumns: 'unique_column'
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropUniqueConstraintChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'table', changes[0].tableName
        assertEquals 'unique_constraint', changes[0].constraintName
        assertEquals 'unique_column', changes[0].uniqueColumns
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a renameSequence change with no attributes to make sure the DSL doesn't create
     * any defaults.
     */
    @Test
    void renameSequenceEmpty() {
        buildChangeSet {
            renameSequence([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof RenameSequenceChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].oldSequenceName
        assertNull changes[0].newSequenceName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a renameSequence change with all attributes present to make sure they all go to
     * the right place.
     */
    @Test
    void renameSequenceFull() {
        buildChangeSet {
            renameSequence(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    oldSequenceName: 'old_sequence',
                    newSequenceName: 'new_sequence'
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof RenameSequenceChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'old_sequence', changes[0].oldSequenceName
        assertEquals 'new_sequence', changes[0].newSequenceName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }
}

