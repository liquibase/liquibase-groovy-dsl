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
package org.liquibase.groovy.delegate

import liquibase.exception.ChangeLogParseException
import org.liquibase.groovy.helper.ExtensionChange

import static org.junit.Assert.*
import liquibase.change.core.RawSQLChange

import org.junit.Test

import org.liquibase.groovy.change.CustomProgrammaticChangeWrapper
import org.liquibase.groovy.custom.MyCustomSqlChange


/**
 * Test the ability to extend the ChangeSetDelegate through groovy metaprogramming
 *
 * @author Jason Clawson
 */
class DelegateExtensionTests extends ChangeSetTests {

    /**
     * Make sure we can handle a custom groovy class that is manually added to the mix via a
     * programmatic change wrapper.
     */
    @Test
    void testMyCustomSqlChange() {
        buildChangeSet {
            myCustomSqlChange()
        }

        def changes = changeSet.changes

        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof CustomProgrammaticChangeWrapper
        assertTrue changes[0].customChange instanceof MyCustomSqlChange
        assertEquals(new RawSQLChange("SELECT * FROM monkey").sql,
                changes[0].customChange.generateStatements(null)[0].sql)
        assertNoOutput()
    }

    /**
     * Verify that we can process a change that is defined in an extension. This will also test the
     * ChangeSetDelegate's methodMissing when we have the single map based property we're expecting.
     */
    @Test
    void processExtensionChange() {
        buildChangeSet {
            extensionChange(name: 'extensionName')
        }

        def changes = changeSet.changes

        assertNotNull changes
        assertEquals 1, changes.size()
        assertTrue changes[0] instanceof ExtensionChange
        assertEquals(new RawSQLChange("SELECT count(*) FROM monkey").sql,
                changes[0].generateStatements(null)[0].sql)
        assertNoOutput()
    }

    /**
     * Try processing an extension change, but this time, call it with a string. This will test the
     * ChangeSetDelegate's methodMissing when we have the wrong kind of argument passed in.  We only
     * support maps.
     */
    @Test(expected = ChangeLogParseException)
    void processExtensionChangeWithString() {
        buildChangeSet {
            extensionChange "not a map"
        }
    }

    /**
     * Try processing an extension change, but this time, call it with a closure.  This is also a
     * test of the methodMissing method.  It's basically the same as above, but with a closure.
     */
    @Test(expected = ChangeLogParseException)
    void processExtensionChangeWithClosure() {
        buildChangeSet {
            extensionChange {
                def x = 3
            }
        }
    }

    /**
     * Try processing an extension with more than one argument.  This will test the
     * ChangeSetDelegate's methodMissing when we have too many arguments.
     */
    @Test(expected = ChangeLogParseException)
    void processExtensionChangeWithMapAndClosure() {
        buildChangeSet {
            extensionChange(name: 'extensionName') {
                def x = 3
            }
        }
    }

}
