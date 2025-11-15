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

import liquibase.changelog.ChangeSet
import liquibase.precondition.core.DBMSPrecondition
import spock.lang.Specification

import liquibase.precondition.core.PreconditionContainer.ErrorOption
import liquibase.precondition.core.PreconditionContainer.FailOption

import static ChangeSetTests.buildChanges
import static org.liquibase.groovy.helper.constants.*
import static liquibase.precondition.core.PreconditionContainer.OnSqlOutputOption.*
import static org.liquibase.groovy.helper.util.assertPropsSet

/**
 *
 * @author Tim Berglund
 */
class ChangeSetPreconditionTests extends Specification {

    void testPreconditionWithoutParams() {
        ChangeSet changeSet = buildChanges {
            preConditions {
                dbms(mysql)
            }
            addColumn( 'animal') {
                column(name: 'monkey_status', type: 'varchar(98)')
            }
        }
        expect:
        1 == changeSet.changes.size()
        def preconditions = changeSet.preconditions?.nestedPreconditions
        1 == preconditions?.size()
        assertPropsSet ([onFail: FailOption.HALT,
                        onError: ErrorOption.HALT,
                        onFailMessage: null,
                        onErrorMessage: null,
                        onSqlOutput: IGNORE], changeSet.preconditions)
        mysql == (preconditions[0] as DBMSPrecondition).type
    }

    static final expPropsPreConditions = [
            onFail: FailOption.WARN,
            onError: ErrorOption.MARK_RAN,
            onFailMessage: fail,
            onErrorMessage: err,
            onSqlOutput: TEST
    ]

    void "preConditions #type arguments"() {
        ChangeSet changeSet = buildChanges expPropsPreConditions, cl
        assertPropsSet expPropsPreConditions, changeSet.preconditions
        where:
        type         | cl
        'mixed type' | { preConditions 'WARN', 'MARK_RAN', onSqlOutput: 'TEST', it.onFailMessage, it.onErrorMessage, {}}
        'named'      | { preConditions(onFail: 'WARN', onError: 'MARK_RAN', onSqlOutput: 'TEST', onFailMessage: fail, onErrorMessage: err) {}}
        'mixed'      | { preConditions it.onFail, it.onError, onSqlOutput: TEST, it.onFailMessage, it.onErrorMessage, {}}
        'positional' | { preConditions it.onFail, it.onError, it.onFailMessage, it.onErrorMessage, it.onSqlOutput, {} }
    }
}

