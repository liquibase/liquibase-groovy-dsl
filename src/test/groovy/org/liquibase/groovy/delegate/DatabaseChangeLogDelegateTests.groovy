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
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.database.ObjectQuotingStrategy
import liquibase.exception.ChangeLogParseException
import liquibase.precondition.Precondition
import liquibase.precondition.core.DBMSPrecondition
import liquibase.precondition.core.PreconditionContainer
import liquibase.precondition.core.PreconditionContainer.ErrorOption
import liquibase.precondition.core.PreconditionContainer.FailOption
import liquibase.resource.DirectoryResourceAccessor
import org.junit.After
import org.junit.Before
import org.junit.Test
import liquibase.parser.ext.GroovyLiquibaseChangeLogParser.Arg
import java.lang.reflect.Field
import org.liquibase.groovy.delegate.DatabaseChangeLogDelegate.Tag
import liquibase.parser.groovy.exception.*

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue
import static liquibase.parser.ext.GroovyLiquibaseChangeLogParser.*
import static org.liquibase.groovy.helper.util.*
import static liquibase.database.ObjectQuotingStrategy.*
import static org.liquibase.groovy.helper.constants.*
import static liquibase.precondition.core.PreconditionContainer.OnSqlOutputOption.*
import static org.liquibase.groovy.delegate.DelegateUtil.cast

/**
 * One of several test classes for the {@link DatabaseChangeLogDelegate}.  The number of tests for
 * {@link DatabaseChangeLogDelegate} were getting unwieldy, so they were split up.  this class deals
 * with all the non-include related tests.
 *
 * @author Steven C. Saliman
 */
class DatabaseChangeLogDelegateTests extends DatabaseChangeLogTests {
    // Let's define some paths and directories.  These should all be relative.
    static final File TMP_CHANGELOG_DIR = new File(TMP_CHANGELOG_PATH)
    static final String EMPTY_CHANGELOG = "${ROOT_CHANGELOG_PATH}/empty-changelog.groovy"
    static final String SIMPLE_CHANGELOG = "${ROOT_CHANGELOG_PATH}/simple-changelog.groovy"
    static final String FULL_CHANGELOG = "${ROOT_CHANGELOG_PATH}/full-changelog.groovy"

    @Before
    void registerParser() {
        // when Liquibase runs, it gives a DirectoryResourceAccessor based on the absolute path of
        // the current working directory.  We'll do the same for this test.  We'll make a file for
        // ".", then get that file's absolute path, which produces something like
        // "/some/path/to/dir/.", just like what Liquibase does.
        def f = new File(".")
        resourceAccessor = new DirectoryResourceAccessor(new File(f.absolutePath))

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
        assertEquals ObjectQuotingStrategy.QUOTE_ALL_OBJECTS, changeLog.objectQuotingStrategy

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

    @Test
    void parseFullChangelog4() {
        String fullChangeLog = "${ROOT_CHANGELOG_PATH}/full-changelog4.groovy"
        def parser = parserFactory.getParser(fullChangeLog, resourceAccessor)

        assertNotNull "Groovy changelog parser was not found", parser

        def changeLog = parser.parse(fullChangeLog, new ChangeLogParameters(), resourceAccessor)
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
        ["databaseChangeLog = {}"
        ,"databaseChangeLog {}"
        ,"databaseChangeLog: {}"
        ].eachWithIndex { String entry, int i ->
            assertNotNull "case $i: Parsed DatabaseChangeLog was null",
                parseDatabaseChangeLog( entry)
        }
    }

    /**
     * Test processing preconditions with almost all the options.  The onSqlOutput and onUpdateSql
     * attributes do the same thing, so this test will look at the onUpdateSql option.
     */
    @Test
    void preconditionParametersWithOnUpdateSql() {

        def databaseChangeLog = buildChangeLog{
            preConditions(onFail: 'WARN', onError: 'MARK_RAN', onUpdateSql: 'TEST', onFailMessage: 'fail-message!!!1!!1one!', onErrorMessage: 'error-message') {}
        }

        // Liquibase now wraps the container in a container.  I don't know why.
        Precondition preconditions = databaseChangeLog.preconditions.nestedPreconditions[0]
        assertNotNull preconditions
        assertTrue preconditions instanceof PreconditionContainer
        assertEquals FailOption.WARN, preconditions.onFail
        assertEquals ErrorOption.MARK_RAN, preconditions.onError
        assertEquals TEST, preconditions.onSqlOutput
        assertEquals 'fail-message!!!1!!1one!', preconditions.onFailMessage
        assertEquals 'error-message', preconditions.onErrorMessage
    }

    /**
     * Test processing preconditions with almost all the options.  The onSqlOutput and onUpdateSql
     * attributes do the same thing, so this test will look at the onSqlOutput option.
     */
    @Test
    void preconditionParametersWithOnSqlOutput() {
        [ named: { preConditions(onFail: 'WARN', onError: 'MARK_RAN', onSqlOutput: TEST,
                                onFailMessage: fail, onErrorMessage: err) {}}
         ,positional: { preConditions( 'WARN', 'MARK_RAN', fail, err, TEST) {}}
         ,mixed: { preConditions( 'WARN', 'MARK_RAN', fail, onSqlOutput: TEST, err) {}}
        ].each { c, cl ->
            try {
                DatabaseChangeLog changelog = buildChangeLog cl

                // Liquibase now wraps the container in a container.  I don't know why.
                assertPropsSet ChangeSetPreconditionTests.expPropsPreConditions,
                        cast(changelog.preconditions.nestedPreconditions[0], PreconditionContainer)
            } catch (e) {
                failedCase(c, e)
            }
        }
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

    static final Map changeSetExpectedArgs = [
            id: 'monkey-change',
            author: 'stevesaliman',
            dbmsSet: ['mysql'] as Set,
            alwaysRun: true,
            runOnChange: true,
            //context: 'should_be_overridden_by_contextFilter',
            contextFilter: 'testing',
            labels: 'test_label',
            runInTransaction: false,
            failOnError: true,
            onValidationFail: ChangeSet.ValidationFailOption.MARK_RAN,
            objectQuotingStrategy: QUOTE_ALL_OBJECTS,
            created: 'test_created',
            runOrder: 'last',
            ignore: true,
            runWith: 'my_executor',
            runWithSpoolFile: 'my.log',
            logicalFilePath: 'logical_file_path',
            comments: strComment
            ]

    @Test
    void changeSetFullPositionalStringConvert() {
        def changeLog =
             buildChangeLog changeSetExpectedArgs, { it.with {
                    changeSet(id, author,
                            'y',
                            contextFilter, alwaysRun, labels,
                            dbmsSet.first(), logicalFilePath,
                            'MARK_RAN', 0,
                            runOrder, failOnError,
                            'QUOTE_ALL_OBJECTS', runWith,
                            created, runWithSpoolFile, ignore) {
                        comment 'comment'
                    }
                }
            }
        assertNotNull changeLog
        assertEquals 1, changeLog.changeSets.size()
        assertPropsSet changeSetExpectedArgs, changeLog.changeSets[0]
    }

    @Test
    void changeSetFullPositional() {
        def changeLog =
             buildChangeLog changeSetExpectedArgs, { it.with {
                changeSet(id, author,
                          runOnChange,
                          contextFilter, alwaysRun, labels,
                          dbmsSet.first(), logicalFilePath,
                          onValidationFail, runInTransaction,
                          runOrder, failOnError,
                          objectQuotingStrategy, runWith,
                          created, runWithSpoolFile, ignore) {
                    comment('comment')
                }
            }
        }
        assertNotNull changeLog
        assertEquals 1, changeLog.changeSets.size()
        assertPropsSet changeSetExpectedArgs, changeLog.changeSets[0]
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
        def property = lastParam(changeLog)
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

        def property = lastParam changeLog
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

        def property = lastParam(changeLog)
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

        def property = lastParam changeLog
        assertEquals 'emotion', property.key
        assertEquals 'angry', property.value
        assertEquals 'mysql', property.validDatabases[0]
        assertEquals 'test', property.validContexts.contexts.toArray()[0]
        assertEquals 'test_label', property.labels.toString()
    }

    @Test
    void propertyFullPositionalGlobal() {
        def changeLog = buildChangeLog expectedPropertyArgs, {
            it.with {
                property key, value, validContexts, labels, validDatabases.first()
            }
        }

        def property = lastParam changeLog
        assertMapEquals expectedPropertyArgs, property.properties
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
        def relFileName = makeRelativeTo(propertyFile, ROOT_CHANGELOG_PATH)

        def changeLog = buildChangeLog {
            property(file: relFileName, relativeToChangelogFile: true)
        }

        def property = lastParam(changeLog)
        assertEquals 'emotion', property.key
        assertEquals 'angry', property.value
        assertNull property.validDatabases
        def contexts = property.validContexts?.contexts
        assertTrue contexts == null || contexts.size() == 0
        def labels = property.labels?.labels
        assertTrue labels == null || labels.size() == 0
    }

    /** Try including a property from a file when we do have a context and dbms.. */
    @Test
    void propertyFromFileFull() {
        File propertyFile = createFileFrom(TMP_CHANGELOG_DIR, '.prop', "emotion=angry\n")
        String propertyFileName = propertyFile.path.replaceAll("\\\\", "/")

        def changeLog = buildChangeLog {
            property(file: propertyFileName, relativeToChangelogFile: false, dbms: 'mysql',
                    contextFilter: 'test', labels: 'test_label')
        }

        def property = lastParam changeLog
        assertMapEquals expectedPropertyArgs, property.properties
    }

    static Map expectedPropertyArgs = [
            key: 'emotion'
            ,value: 'angry'
            ,validDatabases: ['mysql']
            ,validContexts: 'test'
            ,labels: 'test_label'
    ]

    /** Try including a property from a file when we do have a context and dbms.. */
    @Test
    void propertyFromFileFullPositional() {
        File propertyFile = createFileFrom(TMP_CHANGELOG_DIR, '.prop', "emotion=angry\n")

        String relFileName = makeRelativeTo(propertyFile, ROOT_CHANGELOG_PATH)

        def changeLog = buildChangeLog expectedPropertyArgs, {
            it.with {
                property relFileName, true, validContexts, labels, validDatabases.first( )
            }
        }

        def property = lastParam changeLog
        assertMapEquals expectedPropertyArgs, property.properties
    }

    /** Try including a property from a file when we do have a context and dbms.. */
//    @Test
//    void propertyFromFileFullPositionalWithConvert() {
//        File propertyFile = createFileFrom(TMP_CHANGELOG_DIR, '.prop', "emotion=angry\n")
//
//        String relFileName = makeRelativeTo(propertyFile, ROOT_CHANGELOG_PATH)
//
//        def changeLog = buildChangeLog {
//            expectedPropertyArgs.with {
//                property relFileName, true, validContexts, labels, validDatabases.first(), 'y'
//            }
//        }
//
//        def property = lastParam changeLog
//        assertMapEquals expectedPropertyArgs, property.properties
//    }

    /** Try including a property from a file when we do have a context and dbms.. */
    @Test
    void propertyFromFileFullPositionalLocal() {
        File propertyFile = createFileFrom(TMP_CHANGELOG_DIR, '.prop', "emotion=angry\n")

        String propertyFileName = propertyFile.path.replaceAll("\\\\", "/")

        def changeLog = buildChangeLog expectedPropertyArgs, {
            it.with {
                property(propertyFileName, false, validContexts, labels, validDatabases.first(), false)
            }
        }

        def property = lastParam(changeLog, false)
        assertMapEquals expectedPropertyArgs, property.properties
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

    static String errMessage(String c, String expectedMsg){
        /case "$c" expected error msg "$expectedMsg" != "%s"/
    }

    @Test
    void parseDatabaseChangeLogMissingClosureErrors() {
        [ //Fails io("databaseChangeLog 'a', logicalFilePath:'b' {}", databaseChangeLogInvalidArgs('a')),
          "databaseChangeLog 'a', logicalFilePath:'b'": new MissingClosure(dbChangeLogTagName),
          "databaseChangeLog 'a'": new MissingClosure(dbChangeLogTagName),
          'databaseChangeLog': new MissingClosure(dbChangeLogTagName)
        ].each {
            String clue = /case "$it.key"/
            try {
                parseDatabaseChangeLog(it.key)
            } catch (ParseErrorWithFileNLine e) {
                assertEquals(clue, it.value.class, e.class)
                (it.value as ParseErrorWithFileNLine).with {
                    it.fileNameAndLine = ' @memtest:1'
                    assertEquals clue, it.message, e.message
                }
            }
        }
    }

    @Test
    void parseDatabaseChangeLogErrors() {
        [ "nonExistent () {}" : unrecognizedRootElement('nonExistent').message,
          "nonExistent 'a'"   : unrecognizedRootElement('nonExistent').message,
          "nonExistent ()"    : unrecognizedRootElement('nonExistent').message,
          "nonExistent {}"    : unrecognizedRootElement('nonExistent').message,
          "nonExistent"       : unrecognizedRootElement('nonExistent').message
        ].each {  key, expMsg ->
            String errMsg = errMessage key, expMsg
            try{
                parseDatabaseChangeLog(key)
                assertFalse(String.format( errMsg, ''), true)
            } catch (ChangeLogParseException e) {
                assertTrue(String.format( errMsg, e.message), e.message.startsWith(expMsg))
            }
        }
    }

    static String changeLog2(String s) {  "$dbChangeLogTagName: $s"  }

    @Test
    void parseDatabaseChangeLog1stLevelArgSetTwiceErrors() {
        def chLog = new DatabaseChangeLogDelegate(null, resourceAccessor)
        use(DelegateeCategory) {
            [ "preConditions( HALT, onFail: HALT){}": chLog.attributeSetTwice(Tag.preConditions, 'onFail'),
              "preConditions( HALT, OnError.HALT, onError: 'HALT'){}": chLog.attributeSetTwice(Tag.preConditions, 'onError'),
              "preConditions( 'HALT', onFail: 'HALT'){}": chLog.attributeSetTwice(Tag.preConditions, 'onFail'),
              "property 'a', 'v', name: 'b'": chLog.attributeSetTwice(Tag.property, Arg.name),
              "property 'a', file: 'b'": chLog.attributeSetTwice(Tag.property, Arg.file),
              "include 'a', file: 'b'": chLog.attributeSetTwice(Tag.include, Arg.file),
              "includeAll 'a', path: 'b'": chLog.attributeSetTwice(Tag.includeAll, Arg.path),
              "changeSet ('a', id: 'b') {}": chLog.attributeSetTwice(Tag.changeSet, 'id'),
            ].each {key, expMsg ->
                String errMsg = errMessage key, expMsg
                try {
                    parseDatabaseChangeLog "databaseChangeLog{\n$key\n}"
                    fail(String.format(errMsg, ''))
                } catch (ChangeLogParseException e) {
                    assertTrue(String.format(errMsg, e.message), e.message.startsWith(expMsg))
                }
            }
        }
    }

    @Test
    void parseDatabaseChangeLog1stLevelErrors() {
        def chLog = new DatabaseChangeLogDelegate(null, resourceAccessor)
        use(DelegateeCategory<Tag>) {
            [
            // No such property: 1 for class: liquibase.precondition.core.PreconditionContainer$FailOption
            //        'preConditions (1) {}' : chLog.invalidArgs(Tag.preConditions, 1, {}),
            "property ''"      : changeLog2(nonEmptyParameterRequiredFor(Tag.property, Arg.file)),
            'property'         : chLog.invalidArgs(Tag.property),

            'preConditions'        : chLog.missingClosure(Tag.preConditions),
            // 'preConditions () {} {}': chLog.invalidArgs(Tag.preConditions, {}, {}),
            //'preConditions {}{}'   : chLog.invalidArgs(Tag.preConditions, {}, {}),
            "preConditions 'a'"    : chLog.missingClosure(Tag.preConditions),

            'include'         : chLog.invalidArgs(Tag.include, null),
            'include ()'        : chLog.invalidArgs(Tag.include, null),
            // 'include () {} {}' : chLog.invalidArgs(Tag.include, {}, {}),
            //'include (1) {}'   : chLog.invalidArgs(Tag.include, 1, {}),
            // 'include {}{}'     : chLog.invalidArgs(Tag.include, {}, {}),

            //"property 'a'", changeLog (invalidArgs(property,'a')),

            'changeSet'         : chLog.missingClosure(Tag.changeSet),
            //'changeSet (1) {}'  : chLog.invalidArgs(Tag.changeSet, 1, {}),
            //'changeSet () {} {}': chLog.invalidArgs(Tag.changeSet, {}, {}),
            //'changeSet {}{}'    : chLog.invalidArgs(Tag.changeSet, {}, {}),
            "changeSet 'a'"     : chLog.missingClosure(Tag.changeSet),
            "changeSet 'a', id: 'b' {}": chLog.invalidElement('b'),

            "nonExistent 'a'"   : chLog.invalidElement('nonExistent'),
            "nonExistent ()"    : chLog.invalidElement('nonExistent'),
            "nonExistent {}"    : chLog.invalidElement('nonExistent'),
            "nonExistent"       : chLog.invalidElement('nonExistent'),
            "nonExistent () {}" : chLog.invalidElement('nonExistent')
            /* Fails. Should fail with invalid name
             'property () {} {}': chLog.invalidArgs(Tag.property, {}, {}),
             'property {}{}'    : chLog.invalidArgs(Tag.property, {}, {}),
            */
            ].each { key, expMsg ->
                String errMsg = errMessage key, expMsg
                try {
                    parseDatabaseChangeLog "databaseChangeLog{\n$key\n}"
                    assertFalse(String.format(errMsg, ''), true)
                } catch (ChangeLogParseException e) {
                    assertTrue(String.format(errMsg, e.message), e.message.startsWith(expMsg))
                }
            }
        }
    }

    @Test
    void parseDatabaseChangeLogParams() {
        [
         'ObjectQuotingStrategy as String': "databaseChangeLog('1', 'c', 'QUOTE_ALL_OBJECTS') {}"
        ,'mixed 1': "databaseChangeLog( contextFilter: 'c', '1', objectQuotingStrategy:QUOTE_ALL_OBJECTS) {}"
        ,'mixed 2': "databaseChangeLog('1',  contextFilter: 'c', objectQuotingStrategy:'QUOTE_ALL_OBJECTS') {}"
        ,'mapped': "databaseChangeLog(logicalFilePath:'1', contextFilter: 'c', objectQuotingStrategy:QUOTE_ALL_OBJECTS) {}"
        ,'import *': "databaseChangeLog('1', 'c', QUOTE_ALL_OBJECTS) {}"
        ,'non String logicalFilePath':  "databaseChangeLog (1, 'c','QUOTE_ALL_OBJECTS') {}"
//         ,'closure as value':'databaseChangeLog () {} {}'
//         ,'closure as value2':'databaseChangeLog {}{}'
        ].each {
            try {
                DatabaseChangeLog log = parseDatabaseChangeLog it.value
                assertEquals it.key, '1', log.logicalFilePath
                assertEquals it.key, 'c', log.contextFilter.toString()
                assertEquals it.key, QUOTE_ALL_OBJECTS, log.objectQuotingStrategy
            } catch(e) {
                failedCase(it.key, e)
            }
        }
    }

    @Test
    void propertyNameValuePositionalParams() {
        [
         'positionalNameValueOnly': "property 'n', 'v'", // name + value
//         'invalid file':'property (1) {}'
//         'mixed 1': "property value: 'v', 'n'", // Calls file param version
//         'mixed 2': "property ('n',  value: 'v')",
        ].each {
            DatabaseChangeLog changeLog = parseDatabaseChangeLog "databaseChangeLog{\n${it.value}\n}"
            // change log parameters are not exposed through the API, so get them using reflection.
            def params = changeLog.changeLogParameters
            assertEquals 'v', params.getValue('n', changeLog)
        }
    }

    @Test
    void changeSetPositionalParams() {
        [
         'positional': "changeSet('1', 'a'){}", // name + value
         'map only':"changeSet(id:'1', author: 'a'){}",
         'mixed 1': "changeSet(author: 'a', '1'){}",
         'mixed 2': "changeSet('1',  author: 'a'){}",
         'mixed different type': "changeSet(1, author: 'a'){}",
         'positional different type': "changeSet(1, 'a'){}",
        ].each {
            try {
                DatabaseChangeLog changeLog = parseDatabaseChangeLog "databaseChangeLog{\n${it.value}\n}"
                assertEquals 1, changeLog.changeSets.size()
                ChangeSet c = changeLog.changeSets[0] as ChangeSet
                assertEquals 'id', '1', c.id
                assertEquals 'author', 'a', c.author
            } catch(e) {
                failedCase(it.key, e)
            }
        }
    }
}

