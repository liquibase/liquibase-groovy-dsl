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
@groovy.transform.BaseScript(GroovyScript)
import liquibase.GroovyScript
import static liquibase.database.ObjectQuotingStrategy.*
import static liquibase.database.ColumnParentTypeEnum.*
import static liquibase.changelog.ChangeSet.ValidationFailOption.*
import static liquibase.precondition.core.PreconditionContainer.OnSqlOutputOption.*
import liquibase.precondition.core.PreconditionContainer.ErrorOption
import liquibase.precondition.core.PreconditionContainer.FailOption

// This changelog is designed to have just about anything we can throw at the
// parser to make sure it parses. It uses the new positional argument handling
databaseChangeLog(logicalFilePath: '.') {
    property 'prop', 'p1'

    preConditions( FailOption.WARN) {
        and {
            dbms( 'mysql')
            runningAs( 'root')
            or {
                changeSetExecuted( 'precondition-set', 'stevesaliman', 'file')
                columnExists('id', 'monkey_table', 'animal' )
                tableExists('monkey_table', 'animal')
                viewExists('ape_view', 'animal' )
                foreignKeyConstraintExists('my_key', schemaName: 'animal')
                indexExists('monkey_idx', schemaName: 'animal' )
                sequenceExists('monkey_seq', 'animal' )
                primaryKeyExists('id', 'monkey_table','animal' )
                sqlCheck(expectedResult: '0') {
                    "SELECT COUNT(1) FROM monkey WHERE status='angry'"
                }
                customPrecondition(className: '') {
                    tableName('our_table')
                    count(42)
                }
            }
        }
    }

    include( 'empty-changelog.groovy', true, errorIfMissing: false)

    // TODO: Add ehdsWithFilter and depth to this.
    includeAll( 'include', true, errorIfMissingOrEmpty: false)

    //TODO figure out what properties are all about
    clobType = 0

    changeSet('rollback-changeset','stevesaliman') {
        dropTable( 'monkey_table')
    }

    changeSet('first', 'stevesaliman', false, '', true, dbms: 'mysql', runInTransaction: true, failOnError: false) {
        // Comments supported through Groovy
        comment "Liquibase can be aware of this comment"

        preConditions {
            // just like changelog preconditions
        }

        validCheckSum 'd0763edaa9d9bd2a9516280e9044d885'

        // If rollback takes a string, it's just the SQL to execute
        rollback "DROP TABLE monkey_table"
        rollback """
      UPDATE monkey_table SET emotion='angry' WHERE status='PENDING';
      ALTER TABLE monkey_table DROP COLUMN angry;
    """

        // If rollback takes a closure, it's more Liquibase builder (a changeSet?)
        rollback {
            dropTable'monkey_table'
        }

        // If rollback takes a map, it identifies the changeset to re-run to do the rollback (this file assumed)
        rollback(  'rollback-changeset', 'stevesaliman')
    }


    changeSet( 'addColumn', 'stevesaliman') {
        addColumn( 'monkey_table', 'animal') {
            column( 'birthday', 'char', value: 'x', defaultValue: 'default',
                   autoIncrement: false, remarks: 'some comment') {

                // Can put all constraints in one call, or split them up as shown
                constraints(nullable: false, primaryKey: true)
                constraints(unique: true, uniqueConstraintName: 'make_it_unique_yo')
                constraints(foreignKeyName: 'key_to_monkey', references: 'monkey_table')
                constraints(deleteCascade: true)
                constraints(deferrable: true, initiallyDeferred: false)
            }

            // Examples of other value types (only one would apply inside addColumn)
            column( 'number_col','number', valueNumeric: 1, defaultValueNumeric: 2)
            column( 'boolean_col', 'boolean', valueBoolean: true, defaultValueBoolean: false)
            column( 'date_col', 'datetime', valueDate: new Date(), defaultValueDate: new Date())
        }
    }


    changeSet('renameColumn', 'stevesaliman') {
        renameColumn('birthday', 'monkey_birthday',
                'monkey_table','animal',
                     columnDataType: 'char')
    }


//  changeSet(id: 'modify-column', author: 'stevesaliman') {
//    modifyColumn(schemaName: 'animal', tableName: '') {
//      column() { }
//    }
//  }


    changeSet( 'dropColumn', 'stevesaliman') {
        dropColumn('col1', 'monkey_table', 'animal' )
    }


    changeSet( 'alterSequence', 'stevesaliman') {
        alterSequence('my_sequence', incrementBy: 1)
    }


    changeSet('createTable', 'stevesaliman') {
        createTable('badger_table', true, 'animal', tablespace: 'zoo',
                    remarks: 'Honey Badger don\'t care') {
            column( 'id', 'int', autoIncrement: true) {
                constraints(primaryKey: true)
            }
            column 'Description','varchar(250)'
        }
    }


    changeSet( 'renameTable', '') {
        renameTable( 'badger_table', 'badger', 'animal')
    }


    changeSet( 'dropTable', '') {
        dropTable('badger', true,'animal')
    }


    changeSet('createView', '') {
        createView( 'monkey_emotion', true, schemaName: 'animal') {
            "SELECT id, emotion FROM monkey"
        }
    }


    changeSet( 'renameView', '') {
        renameView('monkey_emotion', 'monkey_emotion_vw', 'animal')
    }


    changeSet( 'dropView','') {
        dropView('monkey_emotion_vw', schemaName:'animal')
    }


    changeSet( 'mergeColumns', '') {
        mergeColumns('description',' ', 'notes','comments',
                'varchar(2000)', 'monkey_table', 'animal')
    }


    changeSet('createProcedure', '') {
        createProcedure """
      CREATE OR REPLACE PROCEDURE testMonkey
      IS
      BEGIN
       -- do something with the monkey
      END;
    """
    }


    changeSet('addLookupTable',  '') {
        addLookupTable(existingTableName: 'monkey_emotion', existingColumnName: 'emotion',
                       newTableName: 'monkey_emotion', newColumnName: 'emotion',
                       constraintName: 'monkey_emotion_fk')
    }


    changeSet( 'addNotNullConstraint', '') {
        addNotNullConstraint('id', 'monkey_table', defaultNullValue: 1)
    }


    changeSet( 'dropNotNullConstraint', '') {
        dropNotNullConstraint(schemaName: 'animal', tableName: 'monkey_table',
                columnName: 'id', columnDataType: 'int')
    }


    changeSet( 'addUniqueConstraint',  '') {
        addUniqueConstraint('name', 'monkey_table', constraintName: 'name_uk')
    }


    changeSet( 'dropUniqueConstraint',  '') {
        dropUniqueConstraint('name_uk', 'monkey_table', schemaName: 'animal' )
    }


    changeSet('createSequence', '') {
        createSequence( 'monkey_seq', 1, 2, 0, 42,  schemaName: 'animal', ordered: true)
    }


    changeSet( 'dropSequence', '') {
        dropSequence('monkey_seq')
    }


    changeSet( 'addAutoIncrement', '') {
        addAutoIncrement('id',  'monkey_table', 'int', schemaName: 'animal')
    }


    changeSet( 'addDefaultValue', 'stevesaliman') {
        addDefaultValue(  'string_val', 'monkey_table', 'x',
                schemaName:'animal')
        addDefaultValue(schemaName: 'animal', 'num_col', 'monkey_table', defaultValueNumeric: 1)
        addDefaultValue(schemaName: 'animal', 'boolean_col', 'monkey_table', defaultValueBoolean: false)
        addDefaultValue(schemaName: 'animal', 'date_col','monkey_table', defaultValueDate: new Date())
    }


    changeSet( 'dropDefaultValue', 'stevesaliman') {
        dropDefaultValue('date_col', 'monkey_table', 'animal' )
    }


    changeSet(id: 'addForeignKeyConstraint', author: 'stevesaliman') {
        addForeignKeyConstraint(constraintName: 'monkey_emotion_fk',
                                baseTableName: 'monkey_table',
                                baseTableSchemaName: 'animal',
                                baseColumnNames: 'emotion_id',
                                referencedTableName: 'emotion',
                                referencedTableSchemaName: 'animal',
                                referencedColumnNames: 'id',
                                deferrable: true,
                                initiallyDeferred: false,
                                deleteCascade: true,
                                onDelete: 'CASCADE|SET NULL|SET DEFAULT|RESTRICT|NO ACTION',
                                onUpdate: 'CASCADE|SET NULL|SET DEFAULT|RESTRICT|NO ACTION')
    }


    changeSet( 'dropForeignKeyConstraint', 'stevesaliman') {
        dropForeignKeyConstraint('monkey_emotion_fk',
                                 'monkey_table',
                                 'animal')
    }


    changeSet( 'addPrimaryKey', 'stevesaliman') {
        addPrimaryKey('id','monkey_table', 'animal', tablespace: 'zoo')
    }


    changeSet( 'dropPrimaryKey' ) {
        dropPrimaryKey('monkey_pk','monkey_table', 'animal', dropIndex: true)
    }

    changeSet( 'insert') {
        insert( 'monkey_table', 'animal') {
            column 'string_col', 'x'
            column  'num_col', valueNumeric: 1
            column 'date_col', valueDate: new Date()
            column 'boolean_col', valueBoolean: true
        }
    }


    changeSet( 'loadData') {
        loadData('monkey_table', 'monkey_data.csv', true, 'UTF8', schemaName: 'animal') {
            column 'num_col', index: 2, type: 'NUMERIC'
            column 'boolean_col', index: 3, type: 'BOOLEAN'
            column 'date_col', header: 'shipDate', type: 'DATE'
            column 'string_col', index: 5, type: 'STRING'
        }
    }


    changeSet( 'loadUpdateData') {
        loadUpdateData('monkey_table', 'id', 'monkey_data.csv', true, 'UTF-8', schemaName: 'animal') {
            column(name: 'num_col', index: 2, type: 'NUMERIC')
            column(name: 'boolean_col', index: 3, type: 'BOOLEAN')
            column(name: 'date_col', header: 'shipDate', type: 'DATE')
            column(name: 'string_col', index: 5, type: 'STRING')
        }
    }

    changeSet('update', 'stevesaliman') {
        update('monkey', 'animal') {
            column 'string_col', 'x'
            column 'num_col', valueNumeric: 1
            column 'date_col', valueDate: new Date()
            column 'boolean_col', valueBoolean: true
            where "species='monkey' AND status='angry'"
        }
    }

    changeSet('delete', '') {
        delete('monkey_table', 'animal' )
    }

    changeSet('delete', '') {
        delete('monkey_table', 'animal' ) {
            where "id=39" // optional
        }
    }

    changeSet( 'tag','stevesaliman') {
        tagDatabase('monkey')
    }


    changeSet('stop', 'stevesaliman') {
        stop('Migration stopped because something bad went down')
    }


    changeSet('createIndex', 'stevesaliman') {
        createIndex('monkey_name_idx', 'monkey_table', 'animal', tablespace: 'zoo',
                 unique: true) {
            column 'name'
            column 'birthday' // included: true
        }
    }


    changeSet('dropIndex','stevesaliman') {
        dropIndex('monkey_name_idx', 'monkey_table')
    }


    changeSet( 'sql', 'stevesaliman') {
        sql( true, false, ';') {
            "INSERT INTO ANIMALS (id, species, status) VALUES (1, 'monkey', 'angry')"
        }
    }


    changeSet( 'sqlFile', 'stevesaliman') {
        sqlFile('.', true, splitStatements: '', encoding: 'UTF-8', endDelimiter: '')
    }


//  changeSet(id: 'custom-refactoring', author: 'stevesaliman') {
//    customChange(class: 'net.saliman.liquibase.MonkeyRefactoring') {
//      tableName('animal')
//      species('monkey')
//      status('angry')
//    }
//  }


    changeSet( 'executeCommand', 'stevesaliman') {
        executeCommand( '/bin/ls') {
            arg('--monkey')
            arg('--skip:1')
        }
    }

}
