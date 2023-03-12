/*
 * Copyright 2011-2023 Tim Berglund and Steven C. Saliman
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

/**
 * Little utility with helper methods that all the delegates can use.
 *
 * @author Steven C. Saliman
 */
class DelegateUtil {
    /**
     * Helper method that expands a text expression, replacing variables inside strings with their
     * values from the database change log parameters.
     * @param expression the text to expand, or null if the expression is null.
     * @param databaseChangeLog the database change log
     * @return the text, after substitutions have been made.
     */
    static def expandExpressions(expression, databaseChangeLog) {
        // Don't expand a null into the text "null", just return null
        if ( expression == null ) {
            return null
        }

        // Don't try to expand if we have no paramaters.
        if ( databaseChangeLog.changeLogParameters == null ) {
            return expression
        }
        return databaseChangeLog.changeLogParameters.expandExpressions(expression.toString(), databaseChangeLog)
    }

    /**
     * Helper method to determine the truth of a value.  We need this because Groovy's
     * {@code asBoolean} method for Strings treats any non-empty string as true, including the
     * string whose contents are "false".  This means that a property whose value is "false" would
     * be set to true in a simple if statement.
     * <p>
     * We get around this problem by using the {@code toBoolean} method if the given value is a
     * String.  This way, only the strings "1", "true", and "y" are treated as true.  All others are
     * treated as false.
     * <p>
     * Examples of "true" values are {@code true}, {@code 1}, and {@code "true"}.  Examples of
     * "false" are {@code false}, {@code 0}, and {@code "false"}.
     * @param value the value to parse
     * @param defaultValue the default value to use if there is no value given.
     * @return whether or not the given value is "true", or the defaultValue if no value is given.
     */
    static boolean parseTruth(value, defaultValue) {
        if ( value == null ) {
            return defaultValue
        }
        if ( value instanceof String ) {
            return value.toBoolean()
        }
        return value.asBoolean()
    }
}
