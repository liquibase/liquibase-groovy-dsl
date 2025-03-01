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

import liquibase.change.core.AddForeignKeyConstraintChange
import liquibase.change.core.AddPrimaryKeyChange
import liquibase.change.core.DropAllForeignKeyConstraintsChange
import liquibase.change.core.DropForeignKeyConstraintChange
import liquibase.change.core.DropPrimaryKeyChange
import liquibase.exception.ChangeLogParseException
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

/**
 * This is one of several classes that test the creation of refactoring changes for ChangeSets. This
 * particular class tests changes that deal with referential integrity.
 * <p>
 * Since the Groovy DSL parser is meant to act as a pass-through for Liquibase itself, it doesn't do
 * much in the way of error checking.  For example, we aren't concerned with whether or not required
 * attributes are present - we leave that to Liquibase itself.  In general, each change will have 3
 * kinds of tests:<br>
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
class ReferentialIntegrityRefactoringTests extends ChangeSetTests {

    /**
     * Try creating an AddForeignKeyConstraint change with an invalid attribute.
     */
    @Test(expected = ChangeLogParseException)
    void addForeignKeyConstraintInvalid() {
        buildChangeSet {
            addForeignKeyConstraint(
                    constraintName: 'fk_monkey_emotion',
                    baseTableCatalogName: 'base_catalog',
                    baseTableSchemaName: 'base_schema',
                    baseTableName: 'monkey',
                    baseColumnNames: 'emotion_id',
                    referencedTableCatalogName: 'referenced_catalog',
                    referencedTableSchemaName: 'referenced_schema',
                    referencedTableName: 'emotions',
                    referencedColumnNames: 'id',
                    deferrable: true,
                    initiallyDeferred: false,
                    onDelete: 'RESTRICT',
                    onUpdate: 'CASCADE',
                    validate: false,
                    invalidAttribute: 'invalid'
            )
        }
    }

    /**
     * Build an addForeignKeyConstraint with no attributes to make sure the DSL doesn't make up
     * defaults.
     */
    @Test
    void addForeignKeyConstraintEmpty() {
        buildChangeSet {
            addForeignKeyConstraint([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddForeignKeyConstraintChange
        assertNull changes[0].constraintName
        assertNull changes[0].baseTableCatalogName
        assertNull changes[0].baseTableSchemaName
        assertNull changes[0].baseTableName
        assertNull changes[0].baseColumnNames
        assertNull changes[0].referencedTableCatalogName
        assertNull changes[0].referencedTableSchemaName
        assertNull changes[0].referencedTableName
        assertNull changes[0].referencedColumnNames
        assertNull changes[0].deferrable
        assertNull changes[0].initiallyDeferred
        assertNull changes[0].onDelete
        assertNull changes[0].onUpdate
        assertNull changes[0].validate
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Make an addForeignKeyConstraint with all attributes set to make sure the right values go to
     * the right places.  This also tests proper handling of the RESTRICT and CASCADE values for the
     * foreign key type.
     */
    @Test
    void addForeignKeyConstraintFull() {
        buildChangeSet {
            addForeignKeyConstraint(
                    constraintName: 'fk_monkey_emotion',
                    baseTableCatalogName: 'base_catalog',
                    baseTableSchemaName: 'base_schema',
                    baseColumnNames: 'emotion_id',
                    baseTableName: 'monkey',
                    referencedTableCatalogName: 'referenced_catalog',
                    referencedTableSchemaName: 'referenced_schema',
                    referencedTableName: 'emotions',
                    referencedColumnNames: 'id',
                    deferrable: true,
                    initiallyDeferred: false,
                    onDelete: 'RESTRICT',
                    onUpdate: 'CASCADE',
                    validate: false

            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddForeignKeyConstraintChange
        assertEquals 'fk_monkey_emotion', changes[0].constraintName
        assertEquals 'base_catalog', changes[0].baseTableCatalogName
        assertEquals 'base_schema', changes[0].baseTableSchemaName
        assertEquals 'monkey', changes[0].baseTableName
        assertEquals 'emotion_id', changes[0].baseColumnNames
        assertEquals 'referenced_catalog', changes[0].referencedTableCatalogName
        assertEquals 'referenced_schema', changes[0].referencedTableSchemaName
        assertEquals 'emotions', changes[0].referencedTableName
        assertEquals 'id', changes[0].referencedColumnNames
        assertTrue changes[0].deferrable
        assertFalse changes[0].initiallyDeferred
        assertEquals 'RESTRICT', changes[0].onDelete
        assertEquals 'CASCADE', changes[0].onUpdate
        assertFalse changes[0].validate
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Liquibase has a deprecated, though still documented, attribute
     * {@code referencedUniqueColumn}, which is currently ignored by Liquibase, so let's make sure
     * we get a deprecation warning for it.  This test also validates proper handling of the SET
     * DEFAULT and SET NULL cascade types.
     */
    @Test
    void addForeignKeyConstraintWithReferencesUniqueColumnProperty() {
        buildChangeSet {
            addForeignKeyConstraint(
                    constraintName: 'fk_monkey_emotion',
                    baseTableCatalogName: 'base_catalog',
                    baseTableSchemaName: 'base_schema',
                    baseTableName: 'monkey',
                    baseColumnNames: 'emotion_id',
                    referencedTableCatalogName: 'referenced_catalog',
                    referencedTableSchemaName: 'referenced_schema',
                    referencedTableName: 'emotions',
                    referencedColumnNames: 'id',
                    referencesUniqueColumn: 'true',
                    deferrable: false,
                    initiallyDeferred: true,
                    onDelete: 'SET DEFAULT',
                    onUpdate: 'SET NULL',
                    validate: false
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddForeignKeyConstraintChange
        assertEquals 'fk_monkey_emotion', changes[0].constraintName
        assertEquals 'base_catalog', changes[0].baseTableCatalogName
        assertEquals 'base_schema', changes[0].baseTableSchemaName
        assertEquals 'monkey', changes[0].baseTableName
        assertEquals 'emotion_id', changes[0].baseColumnNames
        assertEquals 'referenced_catalog', changes[0].referencedTableCatalogName
        assertEquals 'referenced_schema', changes[0].referencedTableSchemaName
        assertEquals 'emotions', changes[0].referencedTableName
        assertEquals 'id', changes[0].referencedColumnNames
        assertFalse changes[0].deferrable
        assertTrue changes[0].initiallyDeferred
        assertEquals 'SET DEFAULT', changes[0].onDelete // set by deleteCascade: true
        assertEquals 'SET NULL', changes[0].onUpdate
        assertFalse changes[0].validate
        assertNotNull changes[0].resourceAccessor
        assertPrinted("addForeignKeyConstraint's referencesUniqueColumn parameter has been deprecated")
    }

    /**
     * Liquibase has deprecated, though still documented, attribute
     * {@code referencedUniqueColumn}, which is currently ignored by Liquibase, so let's make sure
     * we get a deprecation warning for it.  This test also validates proper handling of the SET
     * DEFAULT and SET NULL cascade types, as well as the validate attribute.
     */
    @Test
    void addForeignKeyConstraintWithWithNoActionType() {
        buildChangeSet {
            addForeignKeyConstraint(
                    constraintName: 'fk_monkey_emotion',
                    baseTableCatalogName: 'base_catalog',
                    baseTableSchemaName: 'base_schema',
                    baseTableName: 'monkey',
                    baseColumnNames: 'emotion_id',
                    referencedTableCatalogName: 'referenced_catalog',
                    referencedTableSchemaName: 'referenced_schema',
                    referencedTableName: 'emotions',
                    referencedColumnNames: 'id',
                    deferrable: false,
                    initiallyDeferred: false,
                    onDelete: 'NO ACTION',
                    onUpdate: 'NO ACTION',
                    validate: true
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddForeignKeyConstraintChange
        assertEquals 'fk_monkey_emotion', changes[0].constraintName
        assertEquals 'base_catalog', changes[0].baseTableCatalogName
        assertEquals 'base_schema', changes[0].baseTableSchemaName
        assertEquals 'monkey', changes[0].baseTableName
        assertEquals 'emotion_id', changes[0].baseColumnNames
        assertEquals 'referenced_catalog', changes[0].referencedTableCatalogName
        assertEquals 'referenced_schema', changes[0].referencedTableSchemaName
        assertEquals 'emotions', changes[0].referencedTableName
        assertEquals 'id', changes[0].referencedColumnNames
        assertFalse changes[0].deferrable
        assertFalse changes[0].initiallyDeferred
        assertEquals 'NO ACTION', changes[0].onDelete
        assertEquals 'NO ACTION', changes[0].onUpdate
        assertTrue changes[0].validate
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Make sure we can properly handle the boolean {@code deleteCascade} attribute.
     */
    @Test
    void addForeignKeyConstraintWithWithBooleanCascade() {
        buildChangeSet {
            addForeignKeyConstraint(
                    constraintName: 'fk_monkey_emotion',
                    baseTableCatalogName: 'base_catalog',
                    baseTableSchemaName: 'base_schema',
                    baseTableName: 'monkey',
                    baseColumnNames: 'emotion_id',
                    referencedTableCatalogName: 'referenced_catalog',
                    referencedTableSchemaName: 'referenced_schema',
                    referencedTableName: 'emotions',
                    referencedColumnNames: 'id',
                    deferrable: false,
                    initiallyDeferred: true,
                    deleteCascade: true,
                    onUpdate: 'NO ACTION',
                    validate: false
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddForeignKeyConstraintChange
        assertEquals 'fk_monkey_emotion', changes[0].constraintName
        assertEquals 'base_catalog', changes[0].baseTableCatalogName
        assertEquals 'base_schema', changes[0].baseTableSchemaName
        assertEquals 'monkey', changes[0].baseTableName
        assertEquals 'emotion_id', changes[0].baseColumnNames
        assertEquals 'referenced_catalog', changes[0].referencedTableCatalogName
        assertEquals 'referenced_schema', changes[0].referencedTableSchemaName
        assertEquals 'emotions', changes[0].referencedTableName
        assertEquals 'id', changes[0].referencedColumnNames
        assertFalse changes[0].deferrable
        assertTrue changes[0].initiallyDeferred
        assertEquals 'CASCADE', changes[0].onDelete // set by deleteCascade: true
        assertEquals 'NO ACTION', changes[0].onUpdate
        assertFalse changes[0].validate
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an addPrimaryKey change with no attributes to make sure the DSL doesn't make up
     * any defaults.
     */
    @Test
    void addPrimaryKeyEmpty() {
        buildChangeSet {
            addPrimaryKey([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddPrimaryKeyChange
        assertNull changes[0].constraintName
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].tablespace
        assertNull changes[0].columnNames
        assertNull changes[0].clustered
        assertNull changes[0].forIndexCatalogName
        assertNull changes[0].forIndexSchemaName
        assertNull changes[0].forIndexName
        assertNull changes[0].validate
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing an addPrimaryKey change with all supported attributes set.
     */
    @Test
    void addPrimaryKeyFull() {
        buildChangeSet {
            addPrimaryKey(
                    constraintName: 'pk_monkey',
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    columnNames: 'id',
                    tablespace: 'tablespace',
                    clustered: true,
                    forIndexCatalogName: 'index_catalog',
                    forIndexSchemaName: 'index_schema',
                    forIndexName: 'pk_monkey_idx',
                    validate: true
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof AddPrimaryKeyChange
        assertEquals 'pk_monkey', changes[0].constraintName
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'tablespace', changes[0].tablespace
        assertEquals 'id', changes[0].columnNames
        assertTrue changes[0].clustered
        assertEquals 'index_catalog', changes[0].forIndexCatalogName
        assertEquals 'index_schema', changes[0].forIndexSchemaName
        assertEquals 'pk_monkey_idx', changes[0].forIndexName
        assertTrue changes[0].validate
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropAllForeignKeyConstraints change with no attributes to make sure the DSL
     * doesn't introduce any defaults..
     */
    @Test
    void dropAllForeignKeyConstraintsEmpty() {
        buildChangeSet {
            dropAllForeignKeyConstraints([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropAllForeignKeyConstraintsChange
        assertNull changes[0].baseTableCatalogName
        assertNull changes[0].baseTableSchemaName
        assertNull changes[0].baseTableName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropAllForeignKeyConstraints change with all supported attributes.
     */
    @Test
    void dropAllForeignKeyConstraintsFull() {
        buildChangeSet {
            dropAllForeignKeyConstraints(
                    baseTableCatalogName: 'catalog',
                    baseTableSchemaName: 'schema',
                    baseTableName: 'monkey'
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropAllForeignKeyConstraintsChange
        assertEquals 'catalog', changes[0].baseTableCatalogName
        assertEquals 'schema', changes[0].baseTableSchemaName
        assertEquals 'monkey', changes[0].baseTableName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropForeignKeyConstraint change with no attributes to make sure the DSL
     * doesn't introduce unexpected defaults.
     */
    @Test
    void dropForeignKeyConstraintEmpty() {
        buildChangeSet {
            dropForeignKeyConstraint([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropForeignKeyConstraintChange
        assertNull changes[0].baseTableCatalogName
        assertNull changes[0].baseTableSchemaName
        assertNull changes[0].baseTableName
        assertNull changes[0].constraintName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropForeignKeyConstraint with all supported options.
     */
    @Test
    void dropForeignKeyConstraintFull() {
        buildChangeSet {
            dropForeignKeyConstraint(
                    baseTableCatalogName: 'catalog',
                    baseTableSchemaName: 'schema',
                    baseTableName: 'monkey',
                    constraintName: 'fk_monkey_emotion'
            )
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropForeignKeyConstraintChange
        assertEquals 'catalog', changes[0].baseTableCatalogName
        assertEquals 'schema', changes[0].baseTableSchemaName
        assertEquals 'monkey', changes[0].baseTableName
        assertEquals 'fk_monkey_emotion', changes[0].constraintName
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropPrimaryKey change with no attributes to make sure the DSL doesn't
     * introduce any unexpected defaults.
     */
    @Test
    void dropPrimaryKeyEmpty() {
        buildChangeSet {
            dropPrimaryKey([:])
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropPrimaryKeyChange
        assertNull changes[0].catalogName
        assertNull changes[0].schemaName
        assertNull changes[0].tableName
        assertNull changes[0].constraintName
        assertNull changes[0].dropIndex
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }

    /**
     * Test parsing a dropPrimaryKey change with all supported attributes.
     */
    @Test
    void dropPrimaryKeyFull() {
        buildChangeSet {
            dropPrimaryKey(
                    catalogName: 'catalog',
                    schemaName: 'schema',
                    tableName: 'monkey',
                    constraintName: 'pk_monkey',
                    dropIndex: true)
        }

        assertEquals 0, changeSet.rollback.changes.size()
        def changes = changeSet.changes
        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof DropPrimaryKeyChange
        assertEquals 'catalog', changes[0].catalogName
        assertEquals 'schema', changes[0].schemaName
        assertEquals 'monkey', changes[0].tableName
        assertEquals 'pk_monkey', changes[0].constraintName
        assertTrue changes[0].dropIndex
        assertNotNull changes[0].resourceAccessor
        assertNoOutput()
    }
}
