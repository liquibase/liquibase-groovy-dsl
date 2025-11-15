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
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import liquibase.change.core.ExecuteShellCommandChange
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import org.junit.Test

import static groovy.lang.Closure.DELEGATE_ONLY
import static org.junit.Assert.*

/**
 * Tests for {@link ExecuteCommandDelegate}  It makes sure it can be called in all its various
 * permutations.
 *
 * @author Steven C. Saliman
 */
@CompileStatic
class ArgumentDelegateTests {

    /**
     * Test what happens when the closure is empty.  This is fine, and we should have no arguments
     * when we're done.
     */
    @Test
    void emptyArguments() {
        def args = buildArguments {}

        assertNotNull args
        assertEquals 0, args.size()
    }

    /**
     * Test what happens when we have a single argument that is a string.
     */
    @Test
    void oneStringArgument() {
        def args = buildArguments {
            arg 'one'
        }

        assertNotNull args
        assertEquals 1, args.size()
        assertEquals 'one', args[0]
    }

    /**
     * Test what happens when we have a two arguments that is are strings.
     */
    @Test
    void twoStringArguments() {
        def args = buildArguments {
            arg 'one'
            arg 'two'
        }

        assertNotNull args
        assertEquals 2, args.size()
        assertEquals 'one', args[0]
        assertEquals 'two', args[1]
    }

    /**
     * Test what happens when we have a single argument that is in a map.
     */
    @Test
    void oneMapArgument() {
        def args = buildArguments {
            arg(value: 'one')
        }

        assertNotNull args
        assertEquals 1, args.size()
        assertEquals 'one', args[0]
    }

    /**
     * Test what happens when we have a two arguments that is are in maps.
     */
    @Test
    void twoMapArguments() {
        def args = buildArguments {
            arg(value: 'one')
            arg(value: 'two')
        }

        assertNotNull args
        assertEquals 2, args.size()
        assertEquals 'one', args[0]
        assertEquals 'two', args[1]
    }

    /**
     * Let's have some fun.  Pass one argument as a string and another as a map.  This raises
     * questions about the change set writer, but it it is legal.
     */
    @Test
    void mismatchedArguments() {
        def args = buildArguments {
            arg 'one'
            arg(value: 'two')
        }

        assertNotNull args
        assertEquals 2, args.size()
        assertEquals 'one', args[0]
        assertEquals 'two', args[1]
    }

    /**
     * Try calling an invalid method in the closure.  Make sure we get our ChangeLogParseException
     * and not Groovy's standard MethodMissingException.
     */
    @TypeChecked(TypeCheckingMode.SKIP)
    @Test(expected = ChangeLogParseException)
    void invalidClosure() {
        buildArguments {
            invalid "this is an invalid method"
        }
    }

    /**
     * Try creating a map based argument with an invalid attribute to make sure we get our
     * ChangeLogParseException and not something more generic.
     */
    @Test(expected = ChangeLogParseException)
    void invalidAttribute() {
        buildArguments {
            arg(argument: 'invalid')
        }
    }

    /**
     * Helper method to execute an {@link ExecuteCommandDelegate} and return any arguments it created.
     * @param closure
     * @return
     */
    List<String> buildArguments(@DelegatesTo(value=ExecuteCommandDelegate, strategy=DELEGATE_ONLY) Closure closure) {
        def changelog = new DatabaseChangeLog()
        ChangeSetDelegate changeSet = new ChangeSetDelegate(
                new ChangeSet(changelog)
        )
        ExecuteShellCommandChange change = changeSet.lookupChange('executeCommand')
        def delegate = new ExecuteCommandDelegate(changeSet, change)
        changeSet.callOnDelegate(change, closure)

        return change.args
    }
}
