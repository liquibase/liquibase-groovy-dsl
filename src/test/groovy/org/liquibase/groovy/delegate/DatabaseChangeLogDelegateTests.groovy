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

import liquibase.change.visitor.AddColumnChangeVisitor
import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.DatabaseChangeLog
import liquibase.database.ObjectQuotingStrategy
import liquibase.exception.ChangeLogParseException
import liquibase.parser.ChangeLogParser
import liquibase.parser.ChangeLogParserFactory
import liquibase.parser.ext.GroovyLiquibaseChangeLogParser
import liquibase.precondition.Precondition
import liquibase.precondition.core.DBMSPrecondition
import liquibase.precondition.core.PreconditionContainer
import liquibase.resource.DirectoryResourceAccessor
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.lang.reflect.Field

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

/**
 * One of several test classes for the {@link DatabaseChangeLogDelegate}.  The number of tests for
 * {@link DatabaseChangeLogDelegate} were getting unwieldy, so they were split up.  this class deals
 * with all the non-include related tests.
 *
 * @author Steven C. Saliman
 */
class DatabaseChangeLogDelegateTests {
    // Let's define some paths and directories.  These should all be relative.
    static final ROOT_CHANGELOG_PATH = "src/test/changelog"
    static final TMP_CHANGELOG_PATH = ROOT_CHANGELOG_PATH + "/tmp"
    static final TMP_CHANGELOG_DIR = new File(TMP_CHANGELOG_PATH)
    static final EMPTY_CHANGELOG = "${ROOT_CHANGELOG_PATH}/empty-changelog.groovy"
    static final SIMPLE_CHANGELOG = "${ROOT_CHANGELOG_PATH}/simple-changelog.groovy"
    static final FULL_CHANGELOG = "${ROOT_CHANGELOG_PATH}/full-changelog.groovy"
    // This one is not a real file, but it looks like a legit file.  It is used by tests that
    // build changelogs on the fly.
    static final MOCK_CHANGELOG = "${ROOT_CHANGELOG_PATH}/mock-changelog.groovy"

    def resourceAccessor
    ChangeLogParserFactory parserFactory


    @Before
    void registerParser() {
        // when Liquibase runs, it gives a DirectoryResourceAccessor based on the absolute path of
        // the current working directory.  We'll do the same for this test.  We'll make a file for
        // ".", then get that file's absolute path, which produces something like
        // "/some/path/to/dir/.", just like what Liquibase does.
        def f = new File(".")
        resourceAccessor = new DirectoryResourceAccessor(new File(f.absolutePath))
        parserFactory = ChangeLogParserFactory.instance
        ChangeLogParserFactory.getInstance().register(new GroovyLiquibaseChangeLogParser())
        // make sure we start with clean temporary directories before each test
        TMP_CHANGELOG_DIR.deleteDir()
        TMP_CHANGELOG_DIR.mkdirs()
    }

    /**
     * Attempt to clean up included files and directories.  We do this every time to make sure we
     * start clean each time.  The includeAll test depends on it.
     */
    @After
    void cleanUp() {
        TMP_CHANGELOG_DIR.deleteDir()
    }

    @Test
    void parseEmptyChangelog() {
        def parser = parserFactory.getParser(EMPTY_CHANGELOG, resourceAccessor)

        assertNotNull "Groovy changelog parser was not found", parser

        def changeLog = parser.parse(EMPTY_CHANGELOG, new ChangeLogParameters(), resourceAccessor)
        assertNotNull "Parsed DatabaseChangeLog was null", changeLog
        assertTrue "Parser result was not a DatabaseChangeLog", changeLog instanceof DatabaseChangeLog
    }


    @Test
    void parseSimpleChangelog() {
        def parser = parserFactory.getParser(SIMPLE_CHANGELOG, resourceAccessor)

        assertNotNull "Groovy changelog parser was not found", parser

        def changeLog = parser.parse(SIMPLE_CHANGELOG, null, resourceAccessor)
        assertNotNull "Parsed DatabaseChangeLog was null", changeLog
        assertTrue "Parser result was not a DatabaseChangeLog", changeLog instanceof DatabaseChangeLog
        assertEquals '.', changeLog.logicalFilePath
        assertEquals "myContext", changeLog.contextFilter.toString()

        def changeSets = changeLog.changeSets
        assertEquals 1, changeSets.size()
        def changeSet = changeSets[0]
        assertNotNull "ChangeSet was null", changeSet
        assertEquals 'stevesaliman', changeSet.author
        assertEquals 'change-set-001', changeSet.id
    }

    @Test
    void parseFullChangelog() {
        def parser = parserFactory.getParser(FULL_CHANGELOG, resourceAccessor)

        assertNotNull "Groovy changelog parser was not found", parser

        def changeLog = parser.parse(FULL_CHANGELOG, new ChangeLogParameters(), resourceAccessor)
        assertNotNull "Parsed DatabaseChangeLog was null", changeLog
        assertTrue "Parser result was not a DatabaseChangeLog", changeLog instanceof DatabaseChangeLog
        assertEquals '.', changeLog.logicalFilePath

        def changeSets = changeLog.changeSets
        // We don't care much about how this one parses, just that it did parse.
        assertTrue changeSets.size() > 1

    }

    @Test(expected = ChangeLogParseException)
    void parsingEmptyDatabaseChangeLogFails() {
        def changeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog()
""")
        def parser = parserFactory.getParser(changeLogFile.path, resourceAccessor)
        parser.parse(changeLogFile.path, new ChangeLogParameters(), resourceAccessor)
    }


    @Test
    void parsingDatabaseChangeLogAsProperty() {
        File changeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
    databaseChangeLog = {
    }
    """)
        ChangeLogParser parser = parserFactory.getParser(changeLogFile.path, resourceAccessor)
        DatabaseChangeLog changeLog = parser.parse(changeLogFile.path, null, resourceAccessor)

        assertNotNull "Parsed DatabaseChangeLog was null", changeLog
    }


    /**
     * Test processing preconditions with almost all the options.  The onSqlOutput and onUpdateSql
     * attributes do the same thing, so this test will look at the onUpdateSql option.
     */
    @Test
    void preconditionParametersWithOnUpdateSql() {
        def closure = {
            preConditions(onFail: 'WARN', onError: 'MARK_RAN', onUpdateSql: 'TEST', onFailMessage: 'fail-message!!!1!!1one!', onErrorMessage: 'error-message') {

            }
        }

        def databaseChangeLog = new DatabaseChangeLog('changelog.xml')
        databaseChangeLog.changeLogParameters = new ChangeLogParameters()
        def delegate = new DatabaseChangeLogDelegate(databaseChangeLog)
        closure.delegate = delegate
        closure.call()

        // Liquibase now wraps the container in a container.  I don't know why.
        def preconditions = databaseChangeLog.preconditions.nestedPreconditions[0]
        assertNotNull preconditions
        assertTrue preconditions instanceof PreconditionContainer
        assertEquals PreconditionContainer.FailOption.WARN, preconditions.onFail
        assertEquals PreconditionContainer.ErrorOption.MARK_RAN, preconditions.onError
        assertEquals PreconditionContainer.OnSqlOutputOption.TEST, preconditions.onSqlOutput
        assertEquals 'fail-message!!!1!!1one!', preconditions.onFailMessage
        assertEquals 'error-message', preconditions.onErrorMessage
    }

    /**
     * Test processing preconditions with almost all the options.  The onSqlOutput and onUpdateSql
     * attributes do the same thing, so this test will look at the onSqlOutput option.
     */
    @Test
    void preconditionParametersWithOnSqlOutput() {
        def closure = {
            preConditions(onFail: 'WARN', onError: 'MARK_RAN', onSqlOutput: 'TEST', onFailMessage: 'fail-message!!!1!!1one!', onErrorMessage: 'error-message') {

            }
        }

        def databaseChangeLog = new DatabaseChangeLog('changelog.xml')
        databaseChangeLog.changeLogParameters = new ChangeLogParameters()
        def delegate = new DatabaseChangeLogDelegate(databaseChangeLog)
        closure.delegate = delegate
        closure.call()

        // Liquibase now wraps the container in a container.  I don't know why.
        def preconditions = databaseChangeLog.preconditions.nestedPreconditions[0]
        assertNotNull preconditions
        assertTrue preconditions instanceof PreconditionContainer
        assertEquals PreconditionContainer.FailOption.WARN, preconditions.onFail
        assertEquals PreconditionContainer.ErrorOption.MARK_RAN, preconditions.onError
        assertEquals PreconditionContainer.OnSqlOutputOption.TEST, preconditions.onSqlOutput
        assertEquals 'fail-message!!!1!!1one!', preconditions.onFailMessage
        assertEquals 'error-message', preconditions.onErrorMessage
    }

    /**
     * Test creating a changeSet with no attributes. This verifies that we use expected default
     * values when a value is not provided.
     */
    @Test
    void changeSetEmpty() {
        def changeLog = buildChangeLog {
            changeSet([:]) {}
        }
        assertNotNull changeLog.changeSets
        assertEquals 1, changeLog.changeSets.size()
        assertNull changeLog.changeSets[0].id
        assertNull changeLog.changeSets[0].author
        assertFalse changeLog.changeSets[0].alwaysRun
        // the property doesn't match xml or docs.
        assertFalse changeLog.changeSets[0].runOnChange
        assertEquals MOCK_CHANGELOG.toString(), changeLog.changeSets[0].filePath
        assertEquals 0, changeLog.changeSets[0].contextFilter.contexts.size()
        assertNull changeLog.changeSets[0].labels
        assertNull changeLog.changeSets[0].dbmsSet
        assertTrue changeLog.changeSets[0].runInTransaction
        assertNull changeLog.changeSets[0].failOnError
        assertEquals "HALT", changeLog.changeSets[0].onValidationFail.toString()
        assertNull changeLog.changeSets[0].created
        assertNull changeLog.changeSets[0].runOrder
        assertFalse changeLog.changeSets[0].ignore
    }

    /**
     * Test creating a changeSet with all supported attributes.  We support filePath and
     * logicalFilepath.  This test uses filePath.  This test also sets both a context and a
     * contextFilter to prove that the contextFilter takes precedence over the older context
     * parameter.
     */
    @Test
    void changeSetFull() {
        def params = new ChangeLogParameters()
        params.set('myParam', 'myValue')

        def changeLog = buildChangeLog(params) {
            changeSet(id: 'monkey-change',
                      author: 'stevesaliman',
                      dbms: 'mysql',
                      runAlways: true,
                      runOnChange: true,
                      context: 'should_be_overridden_by_contextFilter',
                      contextFilter: 'testing',
                      labels: 'test_label',
                      runInTransaction: false,
                      failOnError: true,
                      onValidationFail: "MARK_RAN",
                      objectQuotingStrategy: "QUOTE_ONLY_RESERVED_WORDS",
                      created: 'test_created',
                      runOrder: 'last',
                      ignore: true,
                      runWith: 'my_executor',
                      runWithSpoolFile: 'my.log',
                      filePath: 'file_path',
                      logicalFilePath: 'logical_file_path') {
                dropTable(tableName: 'monkey')
            }
        }

        assertNotNull changeLog.changeSets
        assertEquals 1, changeLog.changeSets.size()
        def changeSet = changeLog.changeSets[0]
        assertEquals 'monkey-change', changeSet.id
        assertEquals 'stevesaliman', changeSet.author
        assertTrue changeSet.alwaysRun // the property doesn't match xml or docs.
        assertTrue changeSet.runOnChange
        assertEquals 'file_path', changeSet.filePath
        assertEquals 'logical_file_path', changeSet.logicalFilePath
        assertEquals 'testing', changeSet.contextFilter.contexts.toArray()[0]
        assertEquals 'test_label', changeSet.labels.toString()
        assertEquals 'mysql', changeSet.dbmsSet.toArray()[0]
        assertFalse changeSet.runInTransaction
        assertTrue changeSet.failOnError
        assertEquals "MARK_RAN", changeSet.onValidationFail.toString()
        assertEquals ObjectQuotingStrategy.QUOTE_ONLY_RESERVED_WORDS, changeSet.objectQuotingStrategy
        assertEquals 'test_created', changeSet.created
        assertEquals 'last', changeSet.runOrder
        assertTrue changeSet.ignore
        assertEquals 'my_executor', changeSet.runWith
        assertEquals 'my.log', changeSet.runWithSpoolFile

        // Did the changeset get the parameters?
        def changeLogParameters = changeSet.changeLogParameters
        Field f = changeLogParameters.getClass().getDeclaredField("globalParameters")
        f.setAccessible(true)
        def changeSetParams = f.get(changeLogParameters)
        def param = changeSetParams[changeSetParams.size() - 1] // The last one is ours.
        assertEquals 'myParam', param.key
        assertEquals 'myValue', param.value
    }

    /**
     * Test creating a changeSet with all supported attributes, but this time, skip setting the
     * contextFilter parameter to prove that we can still use the older context parameter.  This
     * test also skips the filePath attribute to prove we inherit from the changelog.
     */
    @Test
    void changeSetFullNoContextFilter() {
        def changeLog = buildChangeLog {
            changeSet(id: 'monkey-change',
                      author: 'stevesaliman',
                      dbms: 'mysql',
                      runAlways: true,
                      runOnChange: true,
                      context: 'testing',
                      labels: 'test_label',
                      runInTransaction: false,
                      failOnError: true,
                      onValidationFail: "MARK_RAN",
                      objectQuotingStrategy: "QUOTE_ONLY_RESERVED_WORDS",
                      created: 'test_created',
                      runOrder: 'last',
                      ignore: true,
                      runWith: 'my_executor',
                      runWithSpoolFile: 'my.log',
                      logicalFilePath: 'logical_file_path') {
                dropTable(tableName: 'monkey')
            }
        }

        assertNotNull changeLog.changeSets
        assertEquals 1, changeLog.changeSets.size()
        assertEquals 'monkey-change', changeLog.changeSets[0].id
        assertEquals 'stevesaliman', changeLog.changeSets[0].author
        assertTrue changeLog.changeSets[0].alwaysRun // the property doesn't match xml or docs.
        assertTrue changeLog.changeSets[0].runOnChange
        // This one should inherit from the database change log
        assertEquals 'src/test/changelog/mock-changelog.groovy', changeLog.changeSets[0].filePath
        assertEquals 'logical_file_path', changeLog.changeSets[0].logicalFilePath
        assertEquals 'testing', changeLog.changeSets[0].contextFilter.contexts.toArray()[0]
        assertEquals 'test_label', changeLog.changeSets[0].labels.toString()
        assertEquals 'mysql', changeLog.changeSets[0].dbmsSet.toArray()[0]
        assertFalse changeLog.changeSets[0].runInTransaction
        assertTrue changeLog.changeSets[0].failOnError
        assertEquals "MARK_RAN", changeLog.changeSets[0].onValidationFail.toString()
        assertEquals ObjectQuotingStrategy.QUOTE_ONLY_RESERVED_WORDS, changeLog.changeSets[0].objectQuotingStrategy
        assertEquals 'test_created', changeLog.changeSets[0].created
        assertEquals 'last', changeLog.changeSets[0].runOrder
        assertTrue changeLog.changeSets[0].ignore
        assertEquals 'my_executor', changeLog.changeSets[0].runWith
        assertEquals 'my.log', changeLog.changeSets[0].runWithSpoolFile
    }

    /**
     * Test creating a changeSet with all supported attributes, and one of them has an expression to
     * expand.  This test will omit the filePath and logicalFilePath attributes to make sure we get
     * the correct default.
     */
    @Test
    void changeSetFullWithProperties() {
        def changeLog = buildChangeLog {
            property(name: 'authName', value: 'stevesaliman')
            changeSet(id: 'monkey-change',
                      author: '\${authName}',
                      dbms: 'mysql',
                      runAlways: true,
                      runOnChange: true,
                      contextFilter: 'testing',
                      labels: 'test_label',
                      runInTransaction: false,
                      failOnError: true,
                      onValidationFail: "MARK_RAN",
                      objectQuotingStrategy: "QUOTE_ONLY_RESERVED_WORDS",
                      created: 'test_created',
                      runOrder: 'first',
                      runWith: 'my_executor',
                      runWithSpoolFile: 'my.log',
                      ignore: false) {
                dropTable(tableName: 'monkey')
            }
        }

        assertNotNull changeLog.changeSets
        assertEquals 1, changeLog.changeSets.size()
        assertEquals 'monkey-change', changeLog.changeSets[0].id
        assertEquals 'stevesaliman', changeLog.changeSets[0].author
        assertTrue changeLog.changeSets[0].alwaysRun // the property doesn't match xml or docs.
        assertTrue changeLog.changeSets[0].runOnChange
        assertEquals MOCK_CHANGELOG.toString(), changeLog.changeSets[0].filePath
        assertEquals 'testing', changeLog.changeSets[0].contextFilter.contexts.toArray()[0]
        assertEquals 'test_label', changeLog.changeSets[0].labels.toString()
        assertEquals 'mysql', changeLog.changeSets[0].dbmsSet.toArray()[0]
        assertFalse changeLog.changeSets[0].runInTransaction
        assertTrue changeLog.changeSets[0].failOnError
        assertEquals "MARK_RAN", changeLog.changeSets[0].onValidationFail.toString()
        assertEquals ObjectQuotingStrategy.QUOTE_ONLY_RESERVED_WORDS, changeLog.changeSets[0].objectQuotingStrategy
        assertEquals 'test_created', changeLog.changeSets[0].created
        assertEquals 'first', changeLog.changeSets[0].runOrder
        assertFalse changeLog.changeSets[0].ignore
        assertEquals 'my_executor', changeLog.changeSets[0].runWith
        assertEquals 'my.log', changeLog.changeSets[0].runWithSpoolFile
    }

    /**
     * Test creating a changeSet with an unsupported attribute.
     */
    @Test(expected = ChangeLogParseException)
    void changeSetInvalidAttribute() {
        buildChangeLog {
            changeSet(id: 'monkey-change',
                      author: 'stevesaliman',
                      dbms: 'mysql',
                      runAlways: false,
                      runOnChange: true,
                      contextFilter: 'testing',
                      labels: 'test_label',
                      runInTransaction: false,
                      failOnError: true,
                      onValidationFail: "MARK_RAN",
                      invalidAttribute: 'invalid') {
                dropTable(tableName: 'monkey')
            }
        }
    }

    /**
     * Test creating a changeSet with an unsupported Object quoting strategy.
     */
    @Test(expected = ChangeLogParseException)
    void changeSetInvalidQuotingStrategy() {
        buildChangeLog {
            changeSet(id: 'monkey-change',
                      author: 'stevesaliman',
                      dbms: 'mysql',
                      runAlways: false,
                      runOnChange: true,
                      contextFilter: 'testing',
                      labels: 'test_label',
                      runInTransaction: false,
                      failOnError: true,
                      onValidationFail: "MARK_RAN",
                      objectQuotingStrategy: "MONKEY_QUOTING") {
                dropTable(tableName: 'monkey')
            }
        }
    }

    /**
     * Test change log preconditions.  This uses the same delegate as change set preconditions, so
     * we don't have to do much here, just make sure we can call the correct thing from a change
     * log and have the change log altered.
     */
    @Test
    void preconditionsInChangeLog() {
        def changeLog = buildChangeLog {
            preConditions {
                dbms(type: 'mysql')
            }
        }

        assertEquals 0, changeLog.changeSets.size()
        assertNotNull changeLog.preconditions
        assertTrue changeLog.preconditions.nestedPreconditions.every { precondition -> precondition instanceof Precondition }
        def preconditions = extractPreconditions changeLog.preconditions
        assertEquals 1, preconditions.size()
        assertTrue preconditions[0] instanceof DBMSPrecondition
        assertEquals 'mysql', preconditions[0].type
    }

    /**
     * Try adding a property with an invalid attribute
     */
    @Test(expected = ChangeLogParseException)
    void propertyInvalidAttribute() {
        buildChangeLog {
            property(propertyName: 'invalid', propertyValue: 'invalid')
        }
    }

    /**
     * Try creating an empty property.
     */
    @Test
    void propertyEmpty() {
        def changeLog = buildChangeLog {
            property([:])
        }

        // change log parameters are not exposed through the API, so get them using reflection.
        def changeLogParameters = changeLog.changeLogParameters
        Field f = changeLogParameters.getClass().getDeclaredField("globalParameters")
        f.setAccessible(true)
        def properties = f.get(changeLogParameters)
        def property = properties[properties.size() - 1] // The last one is ours.
        assertNull property.key
        assertNull property.value
        assertNull property.validDatabases
        def contexts = property.validContexts?.contexts
        assertTrue contexts == null || contexts.size() == 0
        def labels = property.labels?.labels
        assertTrue labels == null || labels.size() == 0
    }

    /**
     * Try creating a property with a name and value only.  Make sure we don't try to set the
     * database or contexts
     */
    @Test
    void propertyPartial() {
        def changeLog = buildChangeLog {
            property(name: 'emotion', value: 'angry')
        }

        // change log parameters are not exposed through the API, so get them using reflection.
        // Also, there are
        def changeLogParameters = changeLog.changeLogParameters
        Field f = changeLogParameters.getClass().getDeclaredField("globalParameters")
        f.setAccessible(true)
        def properties = f.get(changeLogParameters)
        def property = properties[properties.size() - 1] // The last one is ours.
        assertNull property.validDatabases
        def contexts = property.validContexts?.contexts
        assertTrue contexts == null || contexts.size() == 0
        def labels = property.labels?.labels
        assertTrue labels == null || labels.size() == 0
    }

    /**
     * Try creating a property with all supported attributes, and a boolean for the global
     * attribute.  This test also sets both a context and contextFilter to make sure the newer
     * contextFilter takes precedence over the older context parameter.
     */
    @Test
    void propertyFullBooleanGlobal() {
        def changeLog = buildChangeLog {
            property(name: 'emotion',
                    value: 'angry',
                    dbms: 'mysql',
                    labels: 'test_label',
                    context: 'should_be_overridden',
                    contextFilter: 'test',
                    'global': true)
        }

        // change log parameters are not exposed through the API, so get them using reflection.
        def changeLogParameters = changeLog.changeLogParameters
        Field f = changeLogParameters.getClass().getDeclaredField("globalParameters")
        f.setAccessible(true)
        def properties = f.get(changeLogParameters)
        def property = properties[properties.size() - 1] // The last one is ours.
        assertEquals 'emotion', property.key
        assertEquals 'angry', property.value
        assertEquals 'mysql', property.validDatabases[0]
        assertEquals 'test', property.validContexts.contexts.toArray()[0]
        assertEquals 'test_label', property.labels.toString()
    }

    /**
     * Try creating a property with all supported attributes and a String for the global attribute.
     * This test skips setting the contextFilter parameter to prove the older context parameter
     * still works.
     */
    @Test
    void propertyFullStringGlobal() {
        def changeLog = buildChangeLog {
            property(name: 'emotion',
                    value: 'angry',
                    dbms: 'mysql',
                    labels: 'test_label',
                    context: 'test',
                    'global': 'true')
        }

        // change log parameters are not exposed through the API, so get them using reflection.
        def changeLogParameters = changeLog.changeLogParameters
        Field f = changeLogParameters.getClass().getDeclaredField("globalParameters")
        f.setAccessible(true)
        def properties = f.get(changeLogParameters)
        def property = properties[properties.size() - 1] // The last one is ours.
        assertEquals 'emotion', property.key
        assertEquals 'angry', property.value
        assertEquals 'mysql', property.validDatabases[0]
        assertEquals 'test', property.validContexts.contexts.toArray()[0]
        assertEquals 'test_label', property.labels.toString()
    }

    /**
     * Try including a property from a file that doesn't exist, and we want to treat missing files
     * as an error.  Expect an exception.
     */
    @Test(expected = ChangeLogParseException)
    void propertyFromInvalidFile() {
        def changeLog = buildChangeLog {
            property(file: "${TMP_CHANGELOG_DIR}/bad.properties", errorIfMissing: true)
        }
    }

    /**
     * Try including a property from a file that doesn't exist, but we want to ignore errors.
     */
    void propertyFromInvalidFileIgnoreError() {
        def changeLog = buildChangeLog {
            property(file: "${TMP_CHANGELOG_DIR}/bad.properties", errorIfMissing: false)
        }
    }

    /**
     * Try including a property from a file that doesn't exist and we don't specify the
     * errorIfMissing attribute.  Expect an exception to prove the errors are the default.
     */
    @Test(expected = ChangeLogParseException)
    void propertyFromInvalidFileDefault() {
        def changeLog = buildChangeLog {
            property(file: "${TMP_CHANGELOG_DIR}/bad.properties")
        }
    }

    /**
     * Try including a property from a file when we don't have a dbms or context.  For this test, we
     * will also try to use a relative file.
     */
    @Test
    void propertyFromFilePartial() {
        def propertyFile = createFileFrom(TMP_CHANGELOG_DIR, '.properties', """
emotion=angry
""")
        propertyFile = propertyFile.path
        propertyFile = propertyFile.replaceAll("\\\\", "/")
        // Now make it relative - add 1 to the index to eat the "/"
        propertyFile = propertyFile.substring(ROOT_CHANGELOG_PATH.length() +1)

        def changeLog = buildChangeLog {
            property(file: "${propertyFile}", relativeToChangelogFile: true)
        }


        // change log parameters are not exposed through the API, so get them using reflection.
        // Also, there are
        def changeLogParameters = changeLog.changeLogParameters
        Field f = changeLogParameters.getClass().getDeclaredField("globalParameters")
        f.setAccessible(true)
        def properties = f.get(changeLogParameters)
        def property = properties[properties.size() - 1] // The last one is ours.
        assertEquals 'emotion', property.key
        assertEquals 'angry', property.value
        assertNull property.validDatabases
        def contexts = property.validContexts?.contexts
        assertTrue contexts == null || contexts.size() == 0
        def labels = property.labels?.labels
        assertTrue labels == null || labels.size() == 0
    }

    /**
     * Try including a property from a file when we do have a context and dbms..
     */
    @Test
    void propertyFromFileFull() {
        def propertyFile = createFileFrom(TMP_CHANGELOG_DIR, '.properties', """
emotion=angry
""")
        propertyFile = propertyFile.path
        propertyFile = propertyFile.replaceAll("\\\\", "/")

        def changeLog = buildChangeLog {
            property(file: "${propertyFile}", relativeToChangelogFile: false, dbms: 'mysql', contextFilter: 'test', labels: 'test_label')
        }

        // change log parameters are not exposed through the API, so get them using reflection.
        // Also, there are
        def changeLogParameters = changeLog.changeLogParameters
        Field f = changeLogParameters.getClass().getDeclaredField("globalParameters")
        f.setAccessible(true)
        def properties = f.get(changeLogParameters)
        def property = properties[properties.size() - 1] // The last one is ours.
        assertEquals 'emotion', property.key
        assertEquals 'angry', property.value
        assertEquals 'mysql', property.validDatabases[0]
        assertEquals 'test', property.validContexts.contexts.toArray()[0]
        assertEquals 'test_label', property.labels.toString()
    }

    /**
     * Try removing a property from changes when there is an invalid parameter in the map.  Expect
     * an exception.  This test needs the dbms to match the changelog parameters.
     */
    @Test(expected = ChangeLogParseException)
    void removeChangeSetPropertyInvalidParameter() {
        def params = new ChangeLogParameters()
        params.database = 'h2'

        buildChangeLog(params) {
            removeChangeSetProperty(change: 'addColumn',
                                    dbms: 'h2',
                                    remove: 'afterColumn',
                                    invalid: 'value')
        }
    }

    /**
     * Try removing a property from changes when we have an invalid change type.  Expect an
     * exception.  This test needs the dbms to match the changelog parameters.
     */
    @Test(expected = ChangeLogParseException)
    void removeChangeSetPropertyInvalidChange() {
        // Set the database against which the changelog will run
        def params = new ChangeLogParameters()
        params.database = 'h2'

        buildChangeLog(params) {
            removeChangeSetProperty(change: 'invalidChange',
                    dbms: 'h2',
                    remove: 'afterColumn')
        }
    }

    /**
     * Try removing a property from changes when we are missing the dbms.  Expect an exception.
     */
    @Test(expected = ChangeLogParseException)
    void removeChangeSetPropertyNoDbms() {
        // Set the database against which the changelog will run
        def params = new ChangeLogParameters()
        params.database = 'h2'

        buildChangeLog(params) {
            removeChangeSetProperty(change: 'addColumn',
                                    remove: 'afterColumn')
        }
    }

    /**
     * Try removing a property from changes when we are missing the "remove" parameter.  Expect an
     * exception.  This test needs the dbms to match the changelog parameters.
     */
    @Test(expected = ChangeLogParseException)
    void removeChangeSetPropertyNoRemove() {
        // Set the database against which the changelog will run
        def params = new ChangeLogParameters()
        params.database = 'h2'

        buildChangeLog(params) {
            removeChangeSetProperty(change: 'addColumn',
                                    dbms: 'h2')
        }
    }

    /**
     * Try removing a property from changes when we are running in the different database than we
     * are targeting.  Expect the database change log to remain without ChangeVisitors
     */
    @Test
    void removeChangeSetPropertyDatabaseMismatch() {
        // Set the database against which the changelog will run
        def params = new ChangeLogParameters()
        params.database = 'h2'

        def changeLog = buildChangeLog(params) {
            removeChangeSetProperty(change: 'addColumn',
                                    dbms: 'mysql',
                                    remove: 'afterColumn')
        }

        assertEquals 0, changeLog.changeSets.size()
        assertEquals 0, changeLog.changeVisitors.size()
    }

    /**
     * Try removing a property from changes when we are running in the same database we are
     * targeting.  Expect the database change log to gain a ChangeVisitor
     */
    @Test
    void removeChangeSetPropertyDatabaseMatches() {
        // Set the database against which the changelog will run
        def params = new ChangeLogParameters()
        params.database = 'h2'

        def changeLog = buildChangeLog(params) {
            removeChangeSetProperty(change: 'addColumn',
                                    dbms: 'h2',
                                    remove: 'afterColumn')
        }

        assertEquals 0, changeLog.changeSets.size()
        assertEquals 1, changeLog.changeVisitors.size()
        def visitor = changeLog.changeVisitors[0]
        assertTrue visitor instanceof AddColumnChangeVisitor
        assertEquals 1, visitor.dbms.size()
        assertEquals 'h2', visitor.dbms[0]
        assertEquals 'afterColumn', visitor.remove
    }

    /**
     * Helper method that builds a changeSet from the given closure.  Tests will use this to test
     * parsing the various closures that make up the Groovy DSL.
     * @param closure the closure containing changes to parse.
     * @return the changeSet, with parsed changes from the closure added.
     */
    private def buildChangeLog(Closure closure) {
        return buildChangeLog(null, closure)
    }

    /**
     * Helper method that builds a changeSet from the given closure.  Tests will use this to test
     * parsing the various closures that make up the Groovy DSL.
     * @param closure the closure containing changes to parse.
     * @return the changeSet, with parsed changes from the closure added.
     */
    private def buildChangeLog(ChangeLogParameters parameters, Closure closure) {
        def changelog = new DatabaseChangeLog(MOCK_CHANGELOG)
        if ( parameters == null ) {
            changelog.changeLogParameters = new ChangeLogParameters()
        } else {
            changelog.changeLogParameters = parameters
        }
        closure.delegate = new DatabaseChangeLogDelegate(changelog)
        closure.delegate.resourceAccessor = resourceAccessor
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        return changelog
    }


    private File createFileFrom(directory, suffix, text) {
        createFileFrom(directory, 'liquibase-', suffix, text)
    }

    private File createFileFrom(directory, prefix, suffix, text) {
        def file = File.createTempFile(prefix, suffix, directory)
        file << text
    }

    /**
     * Helper method to extract the actual preconditions from a list of potential preconditions.
     * <p>
     * Liquibase often nests the actual preconditions in a precondition container.  This method
     * will walk through a collection of objects, extracting preconditions and recursively checking
     * nested items in a container to get just the preconditions themselves.
     * @param preconditions the collection of preconditions to search
     * @return a list of actual preconditions.
     */
    private def extractPreconditions(preconditions) {
        def actualPreconditions = []
        preconditions?.each { pc ->
            if ( pc instanceof PreconditionContainer ) {
                actualPreconditions.addAll extractPreconditions(pc.nestedPreconditions)
            } else if ( pc instanceof Precondition) {
                actualPreconditions.add pc
            }
        }
        return actualPreconditions
    }


}

