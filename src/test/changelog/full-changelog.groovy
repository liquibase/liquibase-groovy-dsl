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

// This changelog is designed to have just about anything we can throw at the
// parser to make sure it parses.  It has some things that are hard to do in
// individual unit tests, such as a changelog with a map.
databaseChangeLog(logicalFilePath: '.') {

    preConditions(onFail: 'WARN') {
        and {
            dbms(type: 'mysql')
            runningAs(username: 'root')
            or {
                changeSetExecuted(id: 'precondition-set', author: 'stevesaliman', changeLogFile: 'file')
                columnExists(schemaName: 'animal', tableName: 'monkey_table', columnName: 'id')
                tableExists(schemaName: 'animal', tableName: 'monkey_table')
                viewExists(schemaName: 'animal', viewName: 'ape_view')
                foreignKeyConstraintExists(schemaName: 'animal', foreignKeyName: 'my_key')
                indexExists(schemaName: 'animal', indexName: 'monkey_idx')
                sequenceExists(schemaName: 'animal', sequenceName: 'monkey_seq')
                primaryKeyExists(schemaName: 'animal', primaryKeyName: 'id', tableName: 'monkey_table')
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

    include(file: 'empty-changelog.groovy', relativeToChangelogFile: true)

    includeAll(path: 'include', relativeToChangelogFile: true)

    //TODO figure out what properties are all about
    clobType = 0

    changeSet(id: 'rollback-changeset', author: 'stevesaliman') {
        dropTable(tableName: 'monkey_table')
    }

    changeSet(id: 'first', author: 'stevesaliman', dbms: 'mysql', runAlways: true, runOnChange: false, context: '', runInTransaction: true, failOnError: false) {
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
            dropTable(tableName: 'monkey_table')
        }

        // If rollback takes a map, it identifies the changeset to re-run to do the rollback (this file assumed)
        rollback(changeSetId: 'rollback-changeset', changeSetAuthor: 'stevesaliman')

    }


    changeSet(id: 'add-column', author: 'stevesaliman') {
        addColumn(tableName: 'monkey_table', schemaName: 'animal') {
            column(name: 'birthday', type: 'char', value: 'x', defaultValue: 'default',
                   autoIncrement: false, remarks: 'some comment') {

                // Can put all constraints in one call, or split them up as shown
                constraints(nullable: false, primaryKey: true)
                constraints(unique: true, uniqueConstraintName: 'make_it_unique_yo')
                constraints(foreignKeyName: 'key_to_monkey', references: 'monkey_table')
                constraints(deleteCascade: true)
                constraints(deferrable: true, initiallyDeferred: false)
            }

            // Examples of other value types (only one would apply inside addColumn)
            column(name: 'number_col', type: 'number', valueNumeric: 1, defaultValueNumeric: 2)
            column(name: 'boolean_col', type: 'boolean', valueBoolean: true, defaultValueBoolean: false)
            column(name: 'date_col', type: 'datetime', valueDate: new Date(), defaultValueDate: new Date())
        }
    }


    changeSet(id: 'rename-column', author: 'stevesaliman') {
        renameColumn(schemaName: 'animal', tableName: 'monkey_table',
                     oldColumnName: 'birthday', newColumnName: 'monkey_birthday',
                     columnDataType: 'char')
    }


//  changeSet(id: 'modify-column', author: 'stevesaliman') {
//    modifyColumn(schemaName: 'animal', tableName: '') {
//      column() { }
//    }
//  }


    changeSet(id: 'drop-column', author: 'stevesaliman') {
        dropColumn(schemaName: 'animal', tableName: 'monkey_table', columnName: '')
    }


    changeSet(id: 'alter-sequence', author: 'stevesaliman') {
        alterSequence(sequenceName: 'my_sequence', incrementBy: 1)
    }


    changeSet(id: 'create-table', author: 'stevesaliman') {
        createTable(schemaName: 'animal', tablespace: 'zoo',
                    tableName: 'badger_table', remarks: 'Honey Badger don\'t care') {
            column(name: 'id', type: 'int', autoIncrement: true) {
                constraints(primaryKey: true)
            }
            column(name: 'Description', type: 'varchar(250)')
        }
    }


    changeSet(id: 'rename-table', author: 'stevesaliman') {
        renameTable(schemaName: 'animal', oldTableName: 'badger_table', newTableName: 'badger')
    }


    changeSet(id: 'drop-table', author: 'stevesaliman') {
        dropTable(schemaName: 'animal', tableName: 'badger')
    }


    changeSet(id: 'create-view', author: 'stevesaliman') {
        createView(schemaName: 'animal', viewName: 'monkey_emotion', replaceIfExists: true) {
            "SELECT id, emotion FROM monkey"
        }
    }


    changeSet(id: 'rename-view', author: 'stevesaliman') {
        renameView(schemaName: 'animal', oldViewName: 'monkey_emotion',
                   newViewName: 'monkey_emotion_vw')
    }


    changeSet(id: 'drop-view', author: 'stevesaliman') {
        dropView(schemaName: 'animal', viewName: 'monkey_emotion_vw')
    }


    changeSet(id: 'merge-columns', author: 'stevesaliman') {
        mergeColumns(schemaName: 'animal', tableName: 'monkey_table',
                     column1Name: 'description', column2Name: 'notes',
                     finalColumnName: 'comments', finalColumnType: 'varchar(2000)', joinString: ' ')
    }


    changeSet(id: 'create-procedire', author: 'stevesaliman') {
        createProcedure """
      CREATE OR REPLACE PROCEDURE testMonkey
      IS
      BEGIN
       -- do something with the monkey
      END;
    """
    }


    changeSet(id: 'add-lookup-table', author: 'stevesaliman') {
        addLookupTable(existingTableName: 'monkey_emotion', existingColumnName: 'emotion',
                       newTableName: 'monkey_emotion', newColumnName: 'emotion',
                       constraintName: 'monkey_emotion_fk')
    }


    changeSet(id: 'add-not-null-constraint', author: 'stevesaliman') {
        addNotNullConstraint(tableName: 'monkey_table', columnName: 'id', defaultNullValue: 1)
    }


    changeSet(id: 'drop-not-null-constraint', author: 'stevesaliman') {
        dropNotNullConstraint(schemaName: 'animal', tableName: 'monkey_table',
                columnName: 'id', columnDataType: 'int')
    }


    changeSet(id: 'add-unique-constraint', author: 'stevesaliman') {
        addUniqueConstraint(tableName: 'monkey_table', columnNames: 'name', constraintName: 'name_uk')
    }


    changeSet(id: 'drop-unique-constraint', author: 'stevesaliman') {
        dropUniqueConstraint(schemaName: 'animal', tableName: 'monkey_table', constraintName: 'name_uk')
    }


    changeSet(id: 'create-sequence', author: 'stevesaliman') {
        createSequence(sequenceName: 'monkey_seq', schemaName: 'animal',
                incrementBy: 1, minValue: 1, maxValue: 42, ordered: true, startValue: 1)
    }


    changeSet(id: 'drop-sequence', author: 'stevesaliman') {
        dropSequence(sequenceName: 'monkey_seq')
    }


    changeSet(id: 'add-auto-increment', author: 'stevesaliman') {
        addAutoIncrement(schemaName: 'animal', tableName: 'monkey_table',
                columnName: 'id', columnDataType: 'int')
    }


    changeSet(id: 'add-default-value', author: 'stevesaliman') {
        addDefaultValue(schemaName: 'animal', tableName: 'monkey_table',
                        columnName: 'string_val', defaultValue: 'x')
        addDefaultValue(schemaName: 'animal', tableName: 'monkey_table',
                        columnName: 'num_col', defaultValueNumeric: 1)
        addDefaultValue(schemaName: 'animal', tableName: 'monkey_table',
                        columnName: 'boolean_col', defaultValueBoolean: false)
        addDefaultValue(schemaName: 'animal', tableName: 'monkey_table',
                        columnName: 'date_col', defaultValueDate: new Date())
    }


    changeSet(id: 'drop-default-value', author: 'stevesaliman') {
        dropDefaultValue(schemaName: 'animal', tableName: 'monkey_table', columnName: 'date_col')
    }


    changeSet(id: 'add-foreign-key-constraint', author: 'stevesaliman') {
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


    changeSet(id: 'drop-foreign-key', author: 'stevesaliman') {
        dropForeignKeyConstraint(constraintName: 'monkey_emotion_fk',
                                 baseTableName: 'monkey_table',
                                 baseTableSchemaName: 'animal')
    }


    changeSet(id: 'add-primary-key', author: 'stevesaliman') {
        addPrimaryKey(schemaName: 'animal', tablespace: 'zoo',
                      tableName: 'monkey_table',
                      columnNames: 'id',
                      constraintName: 'monkey_pk')
    }


    changeSet(id: 'drop-primary-key', author: 'stevesaliman') {
        dropPrimaryKey(schemaName: 'animal',
                       tableName: 'monkey_table',
                       constraintName: 'monkey_pk',
                       dropIndex: true)
    }


    changeSet(id: 'insert-data', author: 'stevesaliman') {
        insert(schemaName: 'animal', tableName: 'monkey_table') {
            column(name: 'string_col', value: 'x')
            column(name: 'num_col', valueNumeric: 1)
            column(name: 'date_col', valueDate: new Date())
            column(name: 'boolean_col', valueBoolean: true)
        }
    }


    changeSet(id: 'load-data', author: 'stevesaliman') {
        loadData(schemaName: 'animal', tableName: 'monkey_table', file: 'monkey_data', encoding: 'UTF8|etc') {
            column(name: 'num_col', index: 2, type: 'NUMERIC')
            column(name: 'boolean_col', index: 3, type: 'BOOLEAN')
            column(name: 'date_col', header: 'shipDate', type: 'DATE')
            column(name: 'string_col', index: 5, type: 'STRING')
        }
    }


    changeSet(id: 'load-update-data', author: 'stevesaliman') {
        loadUpdateData(schemaName: 'animal', tableName: 'monkey_table',
                primaryKey: 'id', file: 'monkey_data', encoding: 'UTF-8') {
            column(name: 'num_col', index: 2, type: 'NUMERIC')
            column(name: 'boolean_col', index: 3, type: 'BOOLEAN')
            column(name: 'date_col', header: 'shipDate', type: 'DATE')
            column(name: 'string_col', index: 5, type: 'STRING')
        }
    }


    changeSet(id: 'update', author: 'stevesaliman') {
        update(schemaName: 'animal', tableName: 'monkey') {
            column(name: 'string_col', value: 'x')
            column(name: 'num_col', valueNumeric: 1)
            column(name: 'date_col', valueDate: new Date())
            column(name: 'boolean_col', valueBoolean: true)
            where "species='monkey' AND status='angry'"
        }
    }


    changeSet(id: 'delete-data', author: 'stevesaliman') {
        delete(schemaName: 'animal', tableName: 'monkey_table') {
            where "id=39" // optional
        }
    }


    changeSet(id: 'tag', author: 'stevesaliman') {
        tagDatabase(tag: 'monkey')
    }


    changeSet(id: 'stop', author: 'stevesaliman') {
        stop('Migration stopped because something bad went down')
    }


    changeSet(id: 'create-index', author: 'stevesaliman') {
        createIndex(schemaName: 'animal', tablespace: 'zoo', tableName: 'monkey_table',
                indexName: 'monkey_name_idx', unique: true) {
            column(name: 'name')
            column(name: 'birthday')
        }
    }


    changeSet(id: 'drop-index', author: 'stevesaliman') {
        dropIndex(tableName: 'monkey_table', indexName: 'monkey_name_idx')
    }


    changeSet(id: 'custom-sql', author: 'stevesaliman') {
        sql(stripComments: true, splitStatements: false, endDelimiter: ';') {
            "INSERT INTO ANIMALS (id, species, status) VALUES (1, 'monkey', 'angry')"
        }
    }


    changeSet(id: 'sql-file', author: 'stevesaliman') {
        sqlFile(path: '.', stripComments: true, splitStatements: '', encoding: 'UTF-8', endDelimiter: '')
    }


//  changeSet(id: 'custom-refactoring', author: 'stevesaliman') {
//    customChange(class: 'net.saliman.liquibase.MonkeyRefactoring') {
//      tableName('animal')
//      species('monkey')
//      status('angry')
//    }
//  }


    changeSet(id: 'shell-command', author: 'stevesaliman') {
        executeCommand(executable: '/bin/ls') {
            arg('--monkey')
            arg('--skip:1')
        }
    }

}
