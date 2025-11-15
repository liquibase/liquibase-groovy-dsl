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


import liquibase.exception.ChangeLogParseException
import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.DatabaseChangeLog
import liquibase.parser.groovy.exception.InvalidArguments
import liquibase.parser.groovy.exception.InvalidAttribute
import liquibase.parser.groovy.exception.UnrecognizedElement
import liquibase.precondition.Precondition
import liquibase.precondition.core.*
import liquibase.precondition.CustomPreconditionWrapper

import static groovy.lang.Closure.DELEGATE_FIRST
import static org.liquibase.groovy.helper.util.*
import static org.liquibase.groovy.helper.constants.*
import static liquibase.database.ObjectQuotingStrategy.QUOTE_ALL_OBJECTS

import spock.lang.*

/**
 * This class tests the creation of Liquibase ChangeSet Preconditions.  It is probably a bit of
 * overkill since most preconditions are set by passing named preconditions to the Liquibase
 * factory, but it does serve to make sure that ll the preconditions currently known will work as
 * we would expect.
 * <p>
 * since we just pass through to Liquibase, we're not too concerned with validating attributes.
 *
 * @author Steven C. Saliman
 */
class PreconditionDelegateTests extends Specification {

    /** Try creating a dbms precondition  */
    void "dbms '#type' arguments"() {
        List<Precondition> preconditions = buildPreconditions cl
        expect:
        1 == preconditions.size()
        mysql == (preconditions[0] as DBMSPrecondition).type

        where:
        type         | cl
        'named'      | { dbms(type: mysql) }
        'positional' | { dbms(mysql) }
    }

    /** Try creating a runningAs precondition. */
    void "runningAs '#type' arguments"() {
        List<Precondition> preconditions = buildPreconditions cl
        expect:
        1 == preconditions.size()
        tlberglund == (preconditions[0] as RunningAsPrecondition).username

        where:
        type        | cl
        'named'     | { runningAs(username: tlberglund) }
        'positional'| { runningAs( tlberglund ) }
    }

    void "expectedQuotingStrategy '#type' arguments"() {
        List<Precondition> preconditions = buildPreconditions cl
        expect:
        1 == preconditions.size()
        QUOTE_ALL_OBJECTS == (preconditions[0] as ObjectQuotingStrategyPrecondition).strategy

        where:
        type        | cl
        'named'     | { expectedQuotingStrategy(strategy: 'QUOTE_ALL_OBJECTS') } // String version
        'positional'| { expectedQuotingStrategy QUOTE_ALL_OBJECTS } // Object version
    }

    static final expChangeLogPropertyDefined = [
            property: 'prop'
            ,value: 'val'
    ]

    /** Try creating a dbms precondition  */
    void "changeLogPropertyDefined '#type' arguments"() {
        verify(expChangeLogPropertyDefined, cl, ChangeLogPropertyDefinedPrecondition)

        where:
        type        | cl
        'named'     | { changeLogPropertyDefined(expChangeLogPropertyDefined) }
        'positional'| { changeLogPropertyDefined( it.property, it.value ) }
    }
    static final String changeLogXML = 'changelog.xml'

    static final expChangeSetExecuted = [
            id: 'unleash-monkey'
            ,author: tlberglund
            ,changeLogFile: changeLogXML
    ]

    /** Try creating a changeSetExecuted precondition.  */
    void "changeSetExecuted '#type' arguments"() {
        verify (expChangeSetExecuted, cl, ChangeSetExecutedPrecondition)

        where:
        type         | cl
        'named'      | { changeSetExecuted(id: it.id, author: it.author, changeLogFile: it.changeLogFile)}
        'mixed'      | { changeSetExecuted( it.id, changeLogFile: it.changeLogFile, it.author)}
        'positional' | { changeSetExecuted( it.id, tlberglund, it.changeLogFile) }
    }

    /** Try creating a columnExists precondition.  */
    void "columnExists '#type' arguments"() {
        verify( expPropsColumnTableSchemaCatalogName, cl, ColumnExistsPrecondition)

        where:
        type         | cl
        'named'      | {
            columnExists expPropsColumnTableSchemaCatalogName}
        'positional' | {
            columnExists( columnName, tableName, schemaName, catalogName) }
        'mixed'      | {
            columnExists( catalogName: catalogName, columnName, tableName, schemaName)}
    }

    /** try creating a tableExists precondition. */
    void "tableExists '#type' arguments"() {
        verify( expPropsTableSchemaCatalogName, cl, TableExistsPrecondition)

        where:
        type         | cl
        'named'      | { tableExists(expPropsTableSchemaCatalogName)}
        'positional' | { tableExists( tableName, schemaName, catalogName) }
        'mixed'      | { tableExists( catalogName: catalogName, tableName, schemaName)}
    }

    void "tableExists '#type' arguments"() {
        verify( expPropsTableSchemaCatalogName, cl, TableIsEmptyPrecondition)

        where:
        type         | cl
        'named'      | { tableIsEmpty(expPropsTableSchemaCatalogName)}
        'positional' | { tableIsEmpty( tableName, schemaName, catalogName) }
        'mixed'      | { tableIsEmpty( catalogName: catalogName, tableName, schemaName)}
    }

     /** Try creating a vewExists precondition. */
    void "viewExists '#type' arguments"() {
        verify( expPropsViewSchemaCatalogName, cl, ViewExistsPrecondition)

        where:
        type         | cl
        'named'      | {
            viewExists(schemaName: schemaName, viewName: it.viewName, catalogName: catalogName)}
        'positional' | {
            viewExists( it.viewName, schemaName, catalogName) }
        'mixed'      | {
            viewExists( catalogName: catalogName, it.viewName, schemaName)}
    }

    static final expForeignKeyConstraintExists = expPropsSchemaAndCatalogName + [
            foreignKeyName: 'fk_monkey_key'
            ,foreignKeyTableName: tableName
    ]

    /** Try creating a foreignKeyConstraintExists precondition */
    void "foreignKeyConstraintExists '#type' arguments"() {
        verify expForeignKeyConstraintExists, cl, ForeignKeyExistsPrecondition

        where:
        type         | cl
        'named'      | {
            foreignKeyConstraintExists expForeignKeyConstraintExists }
        'positional' | {
            foreignKeyConstraintExists it.foreignKeyName, tableName, schemaName, catalogName }
        'mixed'      | {
            foreignKeyConstraintExists catalogName: catalogName, it.foreignKeyName, tableName, schemaName}
    }

    static final expIndexExists = expPropsTableSchemaCatalogName + [
            indexName: 'index'
           ,columnNames: columnNames // Either or indexName
    ]

    /** Try creating an indexExists precondition. */
    void "indexExists '#type' arguments"() {
        verify expIndexExists, cl, IndexExistsPrecondition

        where:
        type         | cl
        'named'      | {
            indexExists it }
        'positional' | {
            indexExists it.indexName, tableName, columnNames, schemaName, catalogName }
        'mixed'      | {
            indexExists catalogName: catalogName, it.indexName, tableName, columnNames, schemaName}
    }

    static final expRowCount = expPropsTableSchemaCatalogName + [
        expectedRows: 1
    ]

    void "rowCount '#type' arguments"() {
        verify expRowCount, cl, RowCountPrecondition

        where:
        type         | cl
        'named'      | { rowCount it}
        'positional' | { rowCount( it.expectedRows, tableName, schemaName, catalogName) }
        'mixed'      | { rowCount( catalogName: catalogName, '1', tableName, schemaName)} // int as string
    }

    static final expSequenceExists = expPropsSchemaAndCatalogName + [
            sequenceName: sequenceName
    ]

    /** Try creating a sequenceExists precondition. */
    void "sequenceExists '#type' arguments"() {
        verify expSequenceExists, cl, SequenceExistsPrecondition

        where:
        type         | cl
        'named'      | { sequenceExists( it )}
        'positional' | { sequenceExists( it.sequenceName, schemaName, catalogName) }
        'mixed'      | { sequenceExists( catalogName: catalogName, it.sequenceName, schemaName)}
    }

    static final expPrimaryKeyExists = expPropsTableSchemaCatalogName + [
            primaryKeyName: primaryKeyName
    ]

    /** Try creating a primaryKeyExists precondition. */
    void "primaryKeyExists '#type' arguments"() {
        verify( expPrimaryKeyExists, cl, PrimaryKeyExistsPrecondition)

        where:
        type         | cl
        'named'      | { primaryKeyExists( it )}
        'positional' | { primaryKeyExists( it.primaryKeyName, tableName, schemaName, catalogName) }
        'mixed'      | { primaryKeyExists( catalogName: catalogName, it.primaryKeyName, tableName , schemaName)}
    }

    static final expUniqueConstraintExists = expPropsTableSchemaCatalogName + [
            constraintName: constraintName
            ,columnNames: columnNames
    ]

    /** Try creating a uniqueConstraintExists precondition.  */
    void "uniqueConstraintExists '#type' arguments"() {
        verify( expUniqueConstraintExists, cl , UniqueConstraintExistsPrecondition)

        where:
        type         | cl
        'named'      | { uniqueConstraintExists( it )}
        'positional' | { uniqueConstraintExists( tableName, it.constraintName, columnNames, schemaName, catalogName) }
        'mixed'      | { uniqueConstraintExists( tableName, catalogName: catalogName, it.constraintName, columnNames, schemaName)}
    }

    /** And clauses are handled a little differently. Make sure we can create it correctly. */
    void andClause() {
        def preconditions = buildPreconditions {
            and {
                dbms( mysql)
                runningAs( 'tlberglund')
            }
        }
        expect:
        1 == preconditions.size()
        (preconditions[0] as AndPrecondition).with {
            null != nestedPreconditions
            2 == nestedPreconditions.size()
            nestedPreconditions[0] instanceof DBMSPrecondition
            nestedPreconditions[1] instanceof RunningAsPrecondition
        }
    }

    /** Or clauses are handled a little differently. Make sure we can create it correctly.   */
    void orClause() {
        def preconditions = buildPreconditions {
            or {
                dbms(type: 'mysql')
                runningAs(username: 'tlberglund')
            }
        }
        expect:
        1 == preconditions.size()
        (preconditions[0] as OrPrecondition).with {
            null != nestedPreconditions
            2 == nestedPreconditions.size()
            nestedPreconditions[0] instanceof DBMSPrecondition
            nestedPreconditions[1] instanceof RunningAsPrecondition
        }
    }

    /** Not clauses are handled a little differently. Make sure we can create it correctly.  */
    void notClause() {
        def preconditions = buildPreconditions {
            not {
                dbms(type: 'mysql')
                runningAs(username: 'tlberglund')
            }
        }
        expect:
        1 == preconditions.size()
        (preconditions[0] as NotPrecondition).with {
            null != nestedPreconditions
            2 == nestedPreconditions.size()
            nestedPreconditions[0] instanceof DBMSPrecondition
            nestedPreconditions[1] instanceof RunningAsPrecondition
        }
    }

    /** SqlCheck preconditions are treated a little different than most. Try creating one with no
     * attributes and an empty closure to make sure we get no side effects.
     */
    void sqlCheckEmpty() {
        def preconditions = buildPreconditions {
            sqlCheck([:]) {}
        }
        expect:
        1 == preconditions.size()
        (preconditions[0] as SqlPrecondition).with {
            !expectedResult
            !sql
        }
    }

    /** Try creating a sqlCheck precondition with an invalid attribute */
    void sqlCheckInvalidAttribute() {
        when:  buildPreconditions {
            sqlCheck(expected: 'angry') {
                "SELECT emotion FROM monkey WHERE id=2884"
            }
        }
        then:  thrown(InvalidAttribute)
    }

    /** Try creating a sqlCheck precondition with all currently known attributes and some SQL in the
     * closure.
     */
    static final expSqlCheck = [
        sql: sqlSelect
       ,expectedResult: 'res'
    ]

    void "sqlCheck '#type' arguments"() {
        verify( expSqlCheck, cl , SqlPrecondition)

        where:
        type         | cl
        'named'      | { sqlCheck( it )}
        'positional' | { sqlCheck( 'res', sqlSelect) }
        'child'      | { sqlCheck( expectedResult: 'res', {sqlSelect})}
        'mixed'      | { sqlCheck( expectedResult: 'res', sqlSelect)}
        'positional+child'| { sqlCheck( 'res', {sqlSelect})}
    }

    /**
     * customPrecondition preconditions are also handled a little differently, so we need some more
     * checks here. This first test sees what happens when a custom precondition is made with an
     * invalid attribute.
     */
    void customPreconditionInvalidAttribute() {
        when: buildPreconditions {
            customPrecondition(class: 'org.liquibase.precondition.MonkeyFailPrecondition') {
                param(paramName: 'emotion', value: 'angry')
            }
        }
        then: thrown(ChangeLogParseException)
    }

    /** Try a custom precondition with a nested param that has an invalid attribute */
    void customPreconditionInvalidParamAttribute() {
        when: buildPreconditions {
            customPrecondition(className: 'org.liquibase.precondition.MonkeyFailPrecondition') {
                param(paramName: 'emotion')
            }
        }
        then: thrown(ChangeLogParseException)
    }

    /** Try a custom precondition with a nested param that has an invalid attribute */
    void customPreconditionMissingName() {
        when: buildPreconditions {
            customPrecondition(className: 'org.liquibase.precondition.MonkeyFailPrecondition') {
                param(value: 'angry')
            }
        }
        then: thrown(ChangeLogParseException)
    }

    /**
     * Test creating a custom precondition with a parameter that has a name but no value.  It is
     * unusual, but legal.  When this happens, the missing value will be converted to
     * the word "null"
     */
    void customPreconditionMissingValue() {
        def preconditions = buildPreconditions {
            customPrecondition(className: 'org.liquibase.precondition.MonkeyFailPrecondition') {
                param(name: 'emotion')
            }
        }
        expect:
        1 == preconditions.size()
        (preconditions[0] as CustomPreconditionWrapper).with {
            1 == paramValues.size()
            null == paramValues.emotion
        }
    }

    /** Try creating a custom precondition with 2 nested param elements. */
    void customPreconditionTwoParamElements() {
        def preconditions = buildPreconditions {
            customPrecondition(className: 'org.liquibase.precondition.MonkeyFailPrecondition') {
                param(name: 'emotion', value: 'angry')
                param(name: 'rfid-tag', value: 28763)
            }
        }
        expect:
        1 == preconditions.size()
        (preconditions[0] as CustomPreconditionWrapper).with {
            2 == paramValues.size()
            'angry' == paramValues.emotion
            '28763' == paramValues['rfid-tag'] // Liquibase converts to string.
        }
    }

    /** Test creating a precondition using nested methods instead of 'param' elements.  */
    void customPreconditionFails() {
        def preconditions = buildPreconditions {
            customPrecondition(className: 'org.liquibase.precondition.MonkeyFailPrecondition') {
                emotion('angry')
                'rfid-tag'(28763)
            }
        }
        expect:
        1 == preconditions.size()
        (preconditions[0] as CustomPreconditionWrapper).with {
            2 == paramValues.size()
            'angry' == paramValues.emotion
            '28763' == paramValues['rfid-tag'] // Liquibase converts to string.
        }
    }

    void "precondition error #type" () {
        when:
        buildPreconditions closure
        then:
        thrown(expEx)
        where:
        type                   | closure                                           | expEx
        'no arguments'    | { dbms }                                                | InvalidArguments
        'invalid precondition' | { linkExists (host: 'www.thewebsiteisdown.com') } | UnrecognizedElement
        'existing with wrong property'| { tableExists(name: 'monkey') }            | InvalidAttribute // This should be InvalidArguments
        // TODO works outside 'missing arguments'    | { dbms() }                                       | InvalidArguments
    }

    /** Verify if the one and only precondition built using the {@code closure} has all the properties set
     * as defined in the {@code exp} map. See {@link util.assertPropsSet()}
     * @param expectedProps expected (name ->) property values map
     * @param closure used to create the precondition
     * @param cls Class of the expected precondition
     */
    static verify(Map expectedProps, closure, Class cls) {
        List<Precondition> preconditions = buildPreconditions( closure, expectedProps )
        assert 1 == preconditions.size()
        assertPropsSet expectedProps, cls.cast(preconditions[0] )
    }

    /**
     * Helper method to run the precondition with the first parameter as argument closure and return the preconditions.
     * @param args optional Map parameter for the closure (for expected parameters)
     * @param closure the closure to call
     * @return the preconditions that were created.
     */
    static List<Precondition> buildPreconditions(
       @DelegatesTo(value = PreconditionDelegate, strategy=DELEGATE_FIRST) Closure closure,
        clArgs = null) {
        def changelog = new DatabaseChangeLog()
        changelog.changeLogParameters = new ChangeLogParameters()
        def delegate = new PreconditionDelegate(changelog,'')
        delegate.callOn( closure, clArgs)
        delegate.preconditions
    }

    void "errors"(){ // TODO test  error cases
        // viewExists( it.tableName, columnName, schemaName, catalogName) }
    }
}

