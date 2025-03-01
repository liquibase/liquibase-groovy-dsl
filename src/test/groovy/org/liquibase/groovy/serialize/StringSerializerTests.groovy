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

import liquibase.change.core.DropTableChange
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import org.junit.Test

import static org.junit.Assert.assertEquals


class StringSerializerTests extends SerializerTests {

    @Test
    void includesSingleQuotation() {
        def changeSet = new ChangeSet(
                'drop-table',
                "John Smith('johnsmith')",
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
changeSet(id: 'drop-table', author: 'John Smith(\\'johnsmith\\')') {
  dropTable(schemaName: 'schema', tableName: 'monkey')
}"""
        assertEquals expectedText, serializedText
    }

    @Test
    void startSingleQuotation() {
        def changeSet = new ChangeSet(
                'drop-table',
                "'John Smith",
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
changeSet(id: 'drop-table', author: '\\'John Smith') {
  dropTable(schemaName: 'schema', tableName: 'monkey')
}"""
        assertEquals expectedText, serializedText
    }

    @Test
    void endSingleQuotation() {
        def changeSet = new ChangeSet(
                'drop-table',
                "John Smith'",
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
changeSet(id: 'drop-table', author: 'John Smith\\'') {
  dropTable(schemaName: 'schema', tableName: 'monkey')
}"""
        assertEquals expectedText, serializedText
    }

    @Test
    void startAndEndSingleQuotation() {
        def changeSet = new ChangeSet(
                'drop-table',
                "'John Smith'",
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
changeSet(id: 'drop-table', author: '\\'John Smith\\'') {
  dropTable(schemaName: 'schema', tableName: 'monkey')
}"""
        assertEquals expectedText, serializedText
    }

    @Test
    void continuousSingleQuote() {
        def changeSet = new ChangeSet(
                'drop-table',
                "'''",
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
changeSet(id: 'drop-table', author: '\\'\\'\\'') {
  dropTable(schemaName: 'schema', tableName: 'monkey')
}"""
        assertEquals expectedText, serializedText
    }

    @Test
    void includesNewline() {
        def changeSet = new ChangeSet(
                'drop-table',
                "John \nSmith",
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
changeSet(id: 'drop-table', author: 'John \\nSmith') {
  dropTable(schemaName: 'schema', tableName: 'monkey')
}"""
        assertEquals expectedText, serializedText
    }

    @Test
    void startNewline() {
        def changeSet = new ChangeSet(
                'drop-table',
                "\nJohn Smith",
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
changeSet(id: 'drop-table', author: '\\nJohn Smith') {
  dropTable(schemaName: 'schema', tableName: 'monkey')
}"""
        assertEquals expectedText, serializedText
    }

    @Test
    void endNewline() {
        def changeSet = new ChangeSet(
                'drop-table',
                "John Smith\n",
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
changeSet(id: 'drop-table', author: 'John Smith\\n') {
  dropTable(schemaName: 'schema', tableName: 'monkey')
}"""
        assertEquals expectedText, serializedText
    }

    @Test
    void multipleNewline() {
        def changeSet = new ChangeSet(
                'drop-table',
                "John\n Smith\n(johnsmith)\n",
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
changeSet(id: 'drop-table', author: 'John\\n Smith\\n(johnsmith)\\n') {
  dropTable(schemaName: 'schema', tableName: 'monkey')
}"""
        assertEquals expectedText, serializedText
    }


}
