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

import groovy.transform.TypeChecked
import liquibase.change.Change
import liquibase.parser.groovy.exception.UnrecognizedElement
import org.junit.Test

import static groovy.lang.Closure.DELEGATE_ONLY
import static groovy.transform.TypeCheckingMode.SKIP
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNull
import static org.liquibase.groovy.helper.util.buildBaseChangeSetDelegate
import static org.liquibase.groovy.delegate.ChangeSetDelegate.Tag.*

/**
 * Tests for {@link CommentDelegate}  It makes sure it can be called in all its various
 * permutations.
 *
 * @author Steven C. Saliman
 */
@TypeChecked
class CommentDelegateTests {

    /**
     * Test what happens when the closure is empty.  This is fine, and we should have no comments
     * when we're done.
     */
    @Test
    void emptyComment() {
        def comment = buildComments(sql, null) {}

        assertNull comment
    }

    /**
     * Test what happens when we have a comment and no SQL..
     */
    @Test
    void commentsNoSql() {
        def comment = buildComments(sql, null) {
            comment 'No comment'
        }

        assertEquals 'No comment', comment
    }

    /**
     * Test what happens when we have a two comments, and some SQL.  In this case the comments
     * should be appended.  We'll also add some Sql to the mix.
     */
//    @Test
//    void twoCommentsWithSql() {
//        def comment = buildComments(sql, "delete from monkey;") {
//            comment 'first'
//            comment 'second'
//            "delete from monkey;"
//        }
//
//        assertEquals 'first second', comment
//    }

    /**
     * Try calling an invalid method in the closure.  Make sure we get our ChangeLogParseException
     * and not Groovy's standard MethodMissingException.
     */
    @TypeChecked(SKIP)
    @Test(expected = UnrecognizedElement)
    void invalidClosure() {
        buildComments(sql, null) {
            invalid "this is an invalid method"
        }
    }

    /**
     * Helper method to execute an {@link ExecuteCommandDelegate} and return any arguments it created.
     * @param closure
     * @return
     */
	static def buildComments(ChangeSetDelegate.Tag tag, String expectedResult, Map args = [:],
									 @DelegatesTo(value = CommentDelegate, strategy=DELEGATE_ONLY) Closure closure) {
       ChangeSetDelegate changeSet = buildBaseChangeSetDelegate()
        Change change = changeSet.makeChangeFromMap(tag.name(), args)
        def sql = changeSet.callOnDelegate(change, closure)
        assertEquals expectedResult, sql
        return change['comment']
    }
}
