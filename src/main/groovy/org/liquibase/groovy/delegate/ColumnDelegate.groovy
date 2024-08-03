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

import liquibase.change.ColumnConfig
import liquibase.exception.ChangeLogParseException
import liquibase.util.PatchedObjectUtil;

/**
 * This class is a delegate for nested columns found frequently in the DSL, such as inside the
 * {@code createTable} change.  It can handle both normal columns, as found in the
 * {@code createTable} change, and the {@code LoadDataColumnConfig} columns that can be found in the
 * {@code loadData} change.  When the {@link ChangeSetDelegate} creates a ColumnDelegate for a,
 * given change, it will need to set the correct columnConfigClass.
 * <p>
 * This class also handles the nested where and whereParams elements that appear in the
 * {@code update} and {@code delete} changes.  This probably does not cohere with the overall
 * purpose of the class, but it is much better than having to duplicate the column processing logic
 * since the {@code update} change uses columns and a where clause.
 * <p>
 * This delegate will expand expressions to make databaseChangeLog property substitutions.  It is
 * important that the caller does not do it again.
 *
 * @author Steven C. Saliman
 */
class ColumnDelegate {
    def columnConfigClass = ColumnConfig
    def databaseChangeLog
    def changeSetId = '<unknown>' // used for error messages
    def changeName = '<unknown>' // used for error messages
    def change // the change to populate

    /**
     * Parse a single column entry in a closure.
     * @param params the attributes to set.
     * @param closure a child closure to call, such as a constraint clause
     */
    def column(Map params, Closure closure = null) {
        def column = columnConfigClass.newInstance()

        // Process the column params
        params.each { key, value ->
            try {
                PatchedObjectUtil.setProperty(column, key, DelegateUtil.expandExpressions(value, databaseChangeLog))
            } catch (RuntimeException e) {
                // Rethrow as an ChangeLogParseException with a more helpful message than you'll get
                // from the Liquibase helper.
                throw new ChangeLogParseException("ChangeSet '${changeSetId}': '${key}' is not a valid column attribute for '${changeName}' changes.", e)
            }
        }

        // Process nested closure (constraints)
        if ( closure ) {
            def constraintDelegate = new ConstraintDelegate(databaseChangeLog: databaseChangeLog,
                    changeSetId: changeSetId,
                    changeName: changeName)
            closure.delegate = constraintDelegate
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure.call()
            column.constraints = constraintDelegate.constraint
        }

        // Try to add the column to the change.  If we're dealing with something like a "delete"
        // change, we'll get an exception, which we'll rethrow as a parse exception to tell the user
        // that columns are not allowed in that change.
        try {
            change.addColumn(column)
        } catch (MissingMethodException e) {
            throw new ChangeLogParseException("ChangeSet '${changeSetId}': columns are not allowed in '${changeName}' changes.", e)
        }
    }

    /**
     * Process the where clause for the closure and add it to the change.  If the change doesn't
     * support where clauses, we'll get a ChangeLogParseException.
     * @param whereClause the where clause to use.
     */
    def where(String whereClause) {
        whereClause = DelegateUtil.expandExpressions(whereClause, databaseChangeLog)
        // If we have a where clause, try to set it in the change.
        try {
            // The columnDelegate DOES take care of expansion.
            PatchedObjectUtil.setProperty(change, 'where', whereClause)
        } catch (RuntimeException e) {
            throw new ChangeLogParseException("ChangeSet '${changeSetId}': a where clause is invalid for '${changeName}' changes.", e)
        }
    }

    /**
     * Process the whereParams clause for the closure and add the parameters to the change.  If the
     * change doesn't support whereParams, we'll get a ChangeLogParseException.
     * @param closure the nested closure with the parameters themselves.
     */
    def whereParams(closure) {
        def whereParamsDelegate = new WhereParamsDelegate(databaseChangeLog: databaseChangeLog,
                changeSetId: changeSetId,
                changeName: changeName,
                change: change)
        closure.delegate = whereParamsDelegate
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    /**
     * Groovy calls methodMissing when it can't find a matching method to call.  We use it to tell
     * the user which changeSet had the invalid element.
     * @param name the name of the method Groovy wanted to call.
     * @param args the original arguments to that method.
     */
    def methodMissing(String name, args) {
        throw new ChangeLogParseException("ChangeSet '${changeSetId}': '${changeName}' is not a valid child element of ${changeName} changes")
    }
}

