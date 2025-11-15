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

import groovy.transform.PackageScope
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.parser.groovy.exception.UnrecognizedElement
import liquibase.precondition.Precondition
import liquibase.precondition.PreconditionLogic
import liquibase.precondition.core.AndPrecondition
import liquibase.precondition.core.OrPrecondition
import liquibase.precondition.CustomPreconditionWrapper
import liquibase.precondition.PreconditionFactory
import liquibase.precondition.core.NotPrecondition
import liquibase.precondition.core.PreconditionContainer
import liquibase.precondition.core.PreconditionContainer.OnSqlOutputOption
import liquibase.precondition.core.PreconditionContainer.ErrorOption
import liquibase.precondition.core.PreconditionContainer.FailOption
import liquibase.util.PatchedObjectUtil

import static groovy.lang.Closure.DELEGATE_ONLY
import static liquibase.parser.ext.GroovyLiquibaseChangeLogParser.dbChangeLogTagName
import static liquibase.parser.ext.GroovyLiquibaseChangeLogParser.isMap
import static liquibase.parser.groovy.exception.InvalidArguments.argsToString
import static DelegateUtil.*

@groovy.transform.CompileStatic
/** Delegate for the preConditions element used both in changeSet and databaseChangeLog */
class PreconditionDelegate extends Delegatee<Tag> implements PreConditionChildren {
    static enum Tag { changeLogPropertyDefined, changeSetExecuted, columnExists, dbms,
                      expectedQuotingStrategy,  foreignKeyConstraintExists, indexExists,
                      primaryKeyExists, rowCount, runningAs, sequenceExists, sqlCheck,
                      tableExists, tableIsEmpty, uniqueConstraintExists, viewExists }

    protected final List<Precondition> preconditions = []

    PreconditionDelegate(DatabaseChangeLog dbChangeLog, String changeId){
        super(dbChangeLog, changeId + '/preConditions', 'preConditions')
    }

    @Override
    protected def methodMissing(String name, args) {
        addPrecondition name, args as Object[]
    }

    /** Called from all known */
    protected void addPrecondition(NullChecker<Tag> chkr) {
        addPrecondition chkr.elem.name(), chkr.asMap
    }

    protected void addPrecondition(Tag name, Object... args ) {
        addPrecondition name.name(), args
    }
    /**
     * Handle all non-nesting preconditions using the PreconditionFactory.
     * @param name the name of the precondition to create
     * @param args the attributes of the new precondition
     */
    protected void addPrecondition(String name, Object... args ) {
        def preconditionFactory = PreconditionFactory.instance
        Precondition precondition = null
        try {
            precondition = preconditionFactory.create(name)
        } catch (RuntimeException e) {
            error unrecognizedElement(name)
        }

        // We don't always get an exception for an invalid precondition...
        if ( precondition == null ) {
            error unrecognizedElement(name)
        }

        MethodDef m = methodDefs[name]
        if ( args.length == 1 && isMap(args[0].class )) {
            setProps(precondition, args[0] as Map<String, Object>)
        } else if(m) { // There is a dedicated method
            setProps precondition, argsAsMap(name, m, args)
        } else {
            logWarning("Unable to handle arguments ${argsToString(args as List)} for precondition '$name'")
        }

        preconditions << precondition
    }

    protected UnrecognizedElement unrecognizedElement(String name ) {
        Collection<String> knownElements = PreconditionFactory.instance.preconditions.keySet()
        new UnrecognizedElement(name, [], changeId,
           "'${name}' is an unknown precondition. Known preconditions are:" + knownElements.toListString())
    }

    /** Executes an SQL statement and checks the returned value. The SQL must return a single row with a single value.
     * @param params the attributes of the precondition
     * @param closure the SQL for the precondition
     */
    def sqlCheck(Map params, // Legacy
                  Closure<String> sql) {
        addPrecondition chkMap(Tag.sqlCheck, params)('sql', sql.call() as String)
    }

    /** Executes the SQL statement in the child closure and checks the returned value
     matches the value defined in {@code expectedResult}
     The SQL must return a single row with a single value.
     @param sql SQL to execute
     @param expectedResult the single value expected*/
    def sqlCheck(String expectedResult, String sql) {
        addPrecondition Tag.sqlCheck, expectedResult, sql
    }

    /** Executes the SQL statement in the child closure and checks the returned value
     matches the value defined in {@code expectedResult}
     The SQL must return a single row with a single value.
     @param sql SQL to execute
     @param expectedResult the single value expected*/
    void sqlCheck(String expectedResult, Closure<String> sql) {
        addPrecondition chkMap(Tag.sqlCheck) ('expectedResult',expectedResult) ('sql',sql ? sql() as String: null)
    }

    /** Executes the SQL statement in the child closure and checks the returned value
     matches the value defined in {@code expectedResult}
     The SQL must return a single row with a single value.
     @param sql SQL to execute
     @param expectedResult the single value expected*/
    def sqlCheck(Map ǃ, String sql) {
        addPrecondition chkMap(Tag.sqlCheck, ǃ)('sql', sql)
    }

    /**
     * Create a customPrecondition.  A custom precondition is a class that implements the Liquibase
     * customPrecondition.  The code can do anything we want.  Parameters need to be passed to our
     * custom class as key/value pairs, either with the XML style of nested {@code param} blocks,
     * or by calling nested methods where the name of the method becomes the key and the arguments
     * become the value.
     * @param params the params for the precondition, such as the class name.
     * @param closure the closure with nested key/value pairs for the custom precondition.
     */
    def customPrecondition(Map<String, Object> params = [:],
                           @DelegatesTo(value=KeyValueDelegate, strategy = DELEGATE_ONLY) Closure closure) {
        def delegate = new KeyValueDelegate('customPrecondition', changeId)
        delegate.callOn(closure)

        def precondition = new CustomPreconditionWrapper()
        setProps(precondition, params)

        delegate.map.each { key, value ->
             // This is a key/value pair in the Liquibase object, so it won't fail.
            def expandedValue = expandExpressions(value as String)
            precondition.setParam(key, expandedValue ? expandedValue : "null" )
        }

        preconditions << precondition
    }

    /** logical AND operator */
    def and(@DelegatesTo(value=PreconditionDelegate, strategy=DELEGATE_ONLY ) Closure closure) {
        preconditions << nestedPrecondition(new AndPrecondition(), closure)
    }

    /** logical OR operator */
    def or(@DelegatesTo(value=PreconditionDelegate, strategy=DELEGATE_ONLY) Closure closure) {
        preconditions << nestedPrecondition(new OrPrecondition(), closure)
    }

    /** logical NOT operator
        For multiple children AND logic is used
     */
    def not(@DelegatesTo(value=PreconditionDelegate, strategy=DELEGATE_ONLY) Closure closure) {
        preconditions << nestedPrecondition(new NotPrecondition(), closure)
    }

    /**
     * execute a {@code preconditions} closure and return the Liquibase
     * {@code PreconditionContainer} it creates.
     * @param databaseChangeLog the database changelog that owns the changesets.
     * @param changeSetId the id of the changeset that owns the precondtions
     * @param params the parameters to the preconditions
     * @param closure nested closures to call.
     * @return the PreconditionContainer it builds.
     */
    @TypeChecked(TypeCheckingMode.SKIP)
    @PackageScope
    static PreconditionContainer buildPreconditionContainer(DatabaseChangeLog databaseChangeLog,
                                                            Map<String, Object> params,
                        @DelegatesTo(value= PreconditionDelegate, strategy=DELEGATE_ONLY) Closure closure,
                                                            String changeSetId = dbChangeLogTagName) {
        PreconditionContainer preconditions = new PreconditionContainer()
        // TODO use setProps(preconditions, params)
        // Process parameters.  3 of them need a special case.
        params.each {key, value ->
            def paramValue = expandExpressions(value, databaseChangeLog)
            if ( key == "onFail" ) {
                preconditions.onFail = FailOption."${paramValue}" // Does not compile statically
            } else if ( key == "onError" ) {
                preconditions.onError = ErrorOption."${paramValue}" // TODO limit according to changeset or databaseChangeLog
            } else if ( key == "onUpdateSql" || key == "onUpdateSql" ) {
                preconditions.onSqlOutput = OnSqlOutputOption."${paramValue}"
            } else {
                // pass the rest to Liquibase
                try {
                    PatchedObjectUtil.setProperty(preconditions, key, paramValue)
                } catch (RuntimeException e) {
                    throw new ChangeLogParseException("$changeSetId: '${key}' is an invalid property for 'preConditions'", e)
                }
            }
        }

        def delegate = new PreconditionDelegate(databaseChangeLog, changeSetId)
        delegate.nestedPrecondition(preconditions, closure, delegate)
    }

    private <T extends PreconditionLogic> T nestedPrecondition(T nestedPrecondition,
           @DelegatesTo(strategy=DELEGATE_ONLY) Closure preConditions,
           PreconditionDelegate delegate = new PreconditionDelegate(databaseChangeLog, changeId)) {
        delegate.callOn(preConditions)

        delegate.preconditions.each { precondition ->
            nestedPrecondition.addNestedPrecondition(precondition)
        }

        return nestedPrecondition
    }
}

