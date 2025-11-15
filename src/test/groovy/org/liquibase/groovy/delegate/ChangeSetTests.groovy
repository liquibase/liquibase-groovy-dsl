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

import groovy.transform.CompileStatic
import liquibase.change.Change
import liquibase.resource.DirectoryResourceAccessor
import org.junit.After
import org.junit.Before
import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog

import static groovy.lang.Closure.DELEGATE_FIRST
import static groovy.lang.Closure.DELEGATE_ONLY
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.liquibase.groovy.helper.util.assertPropsSet
import static org.liquibase.groovy.helper.util.buildBaseChangeSetDelegate

/**
 * This is the base class for all of the change set related tests.  It mostly contains utility
 * methods to help with testing.
 *
 * @author Steven C. Saliman
 */
@CompileStatic
class ChangeSetTests {
    static final String CHANGESET_ID = 'changeset-id'
    static final String CHANGESET_AUTHOR = 'tlberglund'
    static final String CHANGESET_FILEPATH = '/filePath'

    ChangeSet changeSet
    def resourceAccessor = new DirectoryResourceAccessor(new File(''))
    PrintStream oldStdOut = System.out
    ByteArrayOutputStream bufStr = new ByteArrayOutputStream()

    /**
     * Set up for each test.  This involves two things; creating a change set for each test to
     * modify with changes, and capture stdout so that tests can check for the presence/absence of
     * messages.
     */
    @Before
    void before() {
        // Capture stdout to confirm the presence of a deprecation warning.
        System.out = new PrintStream(bufStr)
    }

    static ChangeSet createChangeSet() {
        def changeLog = new DatabaseChangeLog(CHANGESET_FILEPATH)
        changeLog.changeLogParameters = new ChangeLogParameters()
        changeLog.changeLogParameters.set('database.typeName', 'mysql')
        ChangeSet changeSet = new ChangeSet(
                CHANGESET_ID,
                CHANGESET_AUTHOR,
                false,
                false,
                CHANGESET_FILEPATH,
                'context',
                'mysql',
                true,
                changeLog)
        changeLog.addChangeSet(changeSet)
        changeSet
    }

    /**
     * After each test, make sure stdout is back to what it should be, and for good measure, print
     * out any output we got.
     */
    @After
    void restoreStdOut() {
        if ( oldStdOut != null ) {
            System.out = oldStdOut
        }
        String testOutput = bufStr.toString()
        if ( testOutput != null && testOutput.length() > 0 ) {
            println("Test output:\n${testOutput}")
        }
    }

    /**
     * Helper method that builds a changeSet from the given closure.  Tests will use this to test
     * parsing the various closures that make up the Groovy DSL.
     * @param closure the closure containing changes to parse.
     * @return the changeSet, with parsed changes from the closure added.
     */
    ChangeSet buildChangeSet(args = null,
            @DelegatesTo(value=ChangeSetDelegate, strategy=DELEGATE_FIRST) Closure closure) {
        changeSet = buildChanges args, closure
    }

    /**
     * Helper method that builds a changeSet from the given closure.  Tests will use this to test
     * parsing the various closures that make up the Groovy DSL.
     * @param closure the closure containing changes to parse.
     * @param args parameters
     * @return the changeSet, with parsed changes from the closure added.
     */ //@TypeChecked(TypeCheckingMode.SKIP)
    static ChangeSet buildChanges(args = null,
            @DelegatesTo(value=ChangeSetDelegate, strategy=DELEGATE_ONLY) Closure closure) {
        ChangeSet changeSet = createChangeSet()
        new ChangeSetDelegate(changeSet)
           .callOn( closure, args)
        changeSet
    }


    /**
     * Make sure the given message is present in the standard output.  This can be used to verify
     * that we got expected deprecation warnings.  This method will fail the test of the given
     * message is not in standard out.
     * @param message the message that must exist.
     */
    def assertPrinted(String message) {
        String testOutput = bufStr.toString()
        assertTrue "'${message}' was not found in:\n '${testOutput}'",
                testOutput.contains(message)
    }

    /**
     * Make sure the test did not have any output to standard out.  This can be used to make sure
     * there are no deprecation warnings.
     */
    def assertNoOutput() {
        String testOutput = bufStr.toString()
        assertTrue "Did not expect to have output, but got:\n '${testOutput}",
                testOutput.length() < 1
    }

    /** Verify if the one and only change built using the {@code closure} has all the properties set
     * as defined in the {@code expectedProps} map. See {@link util.assertPropsSet()}
     * and is instance of {@code cls}
     * @param expectedProps expected (name ->) property values map
     * @param closure used to create the change
     * @param cls Class of the expected change
     */
    static <T extends Change> T verify( Map<String, Object> expectedProps, Class<T> cls,
                                        @DelegatesTo(strategy = DELEGATE_FIRST) Closure closure) {
        ChangeSet changeSet = buildChanges( expectedProps, closure )
        assertEquals 0, changeSet.rollback.changes.size()
        assert 1 == changeSet.changes.size()
        T change = cls.cast(changeSet.changes[0])
        assertPropsSet expectedProps, change
        change
    }

    static Change buildChange(ChangeSetDelegate.Tag t, Map args=[:],
                              @DelegatesTo(strategy = DELEGATE_ONLY) Closure closure, clParams = null) {
        ChangeSetDelegate cd = buildBaseChangeSetDelegate()
        Change c = cd.addChange(t, args)
        cd.callOnDelegate(c, closure, clParams)
        c
    }
}

