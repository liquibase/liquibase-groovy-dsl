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
package org.liquibase.groovy.serialize

import liquibase.change.core.AddForeignKeyConstraintChange
import liquibase.change.core.DropTableChange
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.precondition.PreconditionFactory
import liquibase.precondition.core.PreconditionContainer
import org.junit.Test

import static org.junit.Assert.assertEquals


class ChangeSetSerializerTests extends SerializerTests {

    @Test
    void serializeSimpleChangeSet() {
        def changeSet = new ChangeSet(
                'drop-table',
                'stevesaliman',
                false,
                false,
                '.',
                null,
                null,
                true,
                new DatabaseChangeLog())
        changeSet.addChange([schemaName: 'schema', tableName: 'monkey'] as DropTableChange)

        def serializedText = serializer.serialize(changeSet, true)
        def expectedText = """\
changeSet(id: 'drop-table', author: 'stevesaliman') {
  dropTable(schemaName: 'schema', tableName: 'monkey')
}"""
        assertEquals expectedText, serializedText
    }

    @Test
    void serializeCompleteChangeSet() {
        def comment = 'This is a Liquibase comment by Steve "Steve" Saliman'
        def changeSet = new ChangeSet(
                'drop-table',
                'stevesaliman',
                true,
                true,
                '.',
                'dev, staging',
                'mysql, oracle',
                true,
                new DatabaseChangeLog())
        changeSet.addChange([schemaName: 'schema', tableName: 'monkey'] as DropTableChange)
        changeSet.addChange([constraintName: 'fk_monkey_emotion', baseTableName: 'monkey', baseTableSchemaName: 'base_schema', baseColumnNames: 'emotion_id', referencedTableName: 'emotions', referencedTableSchemaName: 'referenced_schema', referencedColumnNames: 'id', deferrable: true, initiallyDeferred: true, onDelete: 'CASCADE', onUpdate: 'CASCADE'] as AddForeignKeyConstraintChange)
        changeSet.comments = comment

        def serializedText = serializer.serialize(changeSet, true)
        def expectedText = """\
changeSet(id: 'drop-table', author: 'stevesaliman', runAlways: true, runOnChange: true, context: 'dev,staging', dbms: 'oracle,mysql') {
  comment '${comment}'
  dropTable(schemaName: 'schema', tableName: 'monkey')
  addForeignKeyConstraint(baseColumnNames: 'emotion_id', baseTableName: 'monkey', baseTableSchemaName: 'base_schema', constraintName: 'fk_monkey_emotion', deferrable: true, initiallyDeferred: true, onDelete: 'CASCADE', onUpdate: 'CASCADE', referencedColumnNames: 'id', referencedTableName: 'emotions', referencedTableSchemaName: 'referenced_schema')
}"""
        assertEquals expectedText.toString(), serializedText
    }

    @Test
    void serializeIncludeSingleQuoteCommentChangeSet() {
        def comment = "This is a Liquibase comment by Steve 'Steve' Saliman"
        def changeSet = new ChangeSet(
                'drop-table',
                'stevesaliman',
                true,
                true,
                '.',
                'dev, staging',
                'mysql, oracle',
                true,
                new DatabaseChangeLog())
        changeSet.addChange([schemaName: 'schema', tableName: 'monkey'] as DropTableChange)
        changeSet.addChange([constraintName: 'fk_monkey_emotion', baseTableName: 'monkey', baseTableSchemaName: 'base_schema', baseColumnNames: 'emotion_id', referencedTableName: 'emotions', referencedTableSchemaName: 'referenced_schema', referencedColumnNames: 'id', deferrable: true, initiallyDeferred: true, onDelete: 'CASCADE', onUpdate: 'CASCADE'] as AddForeignKeyConstraintChange)
        changeSet.comments = comment

        def serializedText = serializer.serialize(changeSet, true)
        def expectedText = """\
changeSet(id: 'drop-table', author: 'stevesaliman', runAlways: true, runOnChange: true, context: 'dev,staging', dbms: 'oracle,mysql') {
  comment 'This is a Liquibase comment by Steve \\'Steve\\' Saliman'
  dropTable(schemaName: 'schema', tableName: 'monkey')
  addForeignKeyConstraint(baseColumnNames: 'emotion_id', baseTableName: 'monkey', baseTableSchemaName: 'base_schema', constraintName: 'fk_monkey_emotion', deferrable: true, initiallyDeferred: true, onDelete: 'CASCADE', onUpdate: 'CASCADE', referencedColumnNames: 'id', referencedTableName: 'emotions', referencedTableSchemaName: 'referenced_schema')
}"""
        assertEquals expectedText.toString(), serializedText
    }

    @Test
    void serializeIncludeNewlineCommentChangeSet() {
        def comment = 'This is a Liquibase comment\n by Steve "Steve" Saliman'
        def changeSet = new ChangeSet(
                'drop-table',
                'stevesaliman',
                true,
                true,
                '.',
                'dev, staging',
                'mysql, oracle',
                true,
                new DatabaseChangeLog())
        changeSet.addChange([schemaName: 'schema', tableName: 'monkey'] as DropTableChange)
        changeSet.addChange([constraintName: 'fk_monkey_emotion', baseTableName: 'monkey', baseTableSchemaName: 'base_schema', baseColumnNames: 'emotion_id', referencedTableName: 'emotions', referencedTableSchemaName: 'referenced_schema', referencedColumnNames: 'id', deferrable: true, initiallyDeferred: true, onDelete: 'CASCADE', onUpdate: 'CASCADE'] as AddForeignKeyConstraintChange)
        changeSet.comments = comment

        def serializedText = serializer.serialize(changeSet, true)
        def expectedText = """\
changeSet(id: 'drop-table', author: 'stevesaliman', runAlways: true, runOnChange: true, context: 'dev,staging', dbms: 'oracle,mysql') {
  comment 'This is a Liquibase comment\\n by Steve "Steve" Saliman'
  dropTable(schemaName: 'schema', tableName: 'monkey')
  addForeignKeyConstraint(baseColumnNames: 'emotion_id', baseTableName: 'monkey', baseTableSchemaName: 'base_schema', constraintName: 'fk_monkey_emotion', deferrable: true, initiallyDeferred: true, onDelete: 'CASCADE', onUpdate: 'CASCADE', referencedColumnNames: 'id', referencedTableName: 'emotions', referencedTableSchemaName: 'referenced_schema')
}"""
        assertEquals expectedText.toString(), serializedText
    }

    @Test
    void serializeIncludesPreconditionsChangeSet() {
        def comment = "This is a Liquibase comment by John smith"
        def changeSet = new ChangeSet(
                'drop-table',
                "John Smith",
                true,
                true,
                '.',
                'dev, staging',
                'mysql, oracle',
                true,
                new DatabaseChangeLog())
        changeSet.addChange([schemaName: 'schema', tableName: 'monkey'] as DropTableChange)
        changeSet.addChange([constraintName: 'fk_monkey_emotion', baseTableName: 'monkey', baseTableSchemaName: 'base_schema', baseColumnNames: 'emotion_id', referencedTableName: 'emotions', referencedTableSchemaName: 'referenced_schema', referencedColumnNames: 'id', deferrable: true, initiallyDeferred: true, onDelete: 'CASCADE', onUpdate: 'CASCADE'] as AddForeignKeyConstraintChange)
        changeSet.comments = comment
        def preconditionContainer = new PreconditionContainer()
        preconditionContainer.onFail = 'WARN'
        preconditionContainer.onError = 'MARK_RAN'
        preconditionContainer.onSqlOutput = 'TEST'
        preconditionContainer.onFailMessage = 'fail-message!!!1!!1one!'
        preconditionContainer.onErrorMessage = 'error-message'
        def preconditionFactory = PreconditionFactory.instance
        def precondition1 = preconditionFactory.create('or')
        def precondition1_1 = preconditionFactory.create('dbms')
        precondition1_1.type = 'mysql'
        precondition1.addNestedPrecondition(precondition1_1)
        def precondition1_2 = preconditionFactory.create('dbms')
        precondition1_2.type = 'oracle'
        precondition1.addNestedPrecondition(precondition1_2)
        preconditionContainer.addNestedPrecondition(precondition1)
        changeSet.preconditions = preconditionContainer

        def serializedText = serializer.serialize(changeSet, true)

        def expectedText = """\
changeSet(id: 'drop-table', author: 'John Smith', runAlways: true, runOnChange: true, context: 'dev,staging', dbms: 'oracle,mysql') {
  comment '${comment}'
  preConditions(onError: 'MARK_RAN', onErrorMessage: 'error-message', onFail: 'WARN', onFailMessage: 'fail-message!!!1!!1one!', onSqlOutput: 'TEST') {
    or {
      dbms(type: 'mysql')
      dbms(type: 'oracle')
    }
  }
  dropTable(schemaName: 'schema', tableName: 'monkey')
  addForeignKeyConstraint(baseColumnNames: 'emotion_id', baseTableName: 'monkey', baseTableSchemaName: 'base_schema', constraintName: 'fk_monkey_emotion', deferrable: true, initiallyDeferred: true, onDelete: 'CASCADE', onUpdate: 'CASCADE', referencedColumnNames: 'id', referencedTableName: 'emotions', referencedTableSchemaName: 'referenced_schema')
}"""
        assertEquals expectedText.toString(), serializedText
    }


}
