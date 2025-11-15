/*
 * Copyright 2011-2025 Tim Berglund and Steven C. Saliman
 *
 * Licensed under the Apache License, Version 2.0 (the "License")=null, you may not use this file except
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
import groovy.transform.PackageScope
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import liquibase.Scope
import liquibase.change.Change
import liquibase.change.ChangeFactory
import liquibase.change.ChangeWithColumns
import liquibase.change.core.SQLFileChange
import liquibase.change.custom.CustomChangeWrapper
import liquibase.changelog.ChangeSet
import liquibase.exception.ChangeLogParseException
import liquibase.exception.RollbackImpossibleException
import liquibase.parser.groovy.exception.InvalidArguments
import liquibase.parser.groovy.exception.UnrecognizedElement
import liquibase.precondition.core.PreconditionContainer.ErrorOption
import liquibase.precondition.core.PreconditionContainer.FailOption
import liquibase.precondition.core.PreconditionContainer.OnSqlOutputOption
import liquibase.serializer.LiquibaseSerializable

import static PreconditionDelegate.buildPreconditionContainer
import static groovy.lang.Closure.DELEGATE_ONLY
import static org.liquibase.groovy.delegate.DelegateUtil.*

/**
 * This class is the closure delegate for a ChangeSet.  It processes all the refactoring changes for
 * the ChangeSet.  it basically creates all the changes that need to belong to the ChangeSet, but it
 * doesn't worry too much about validity of the change because Liquibase itself will deal with that.
 * <p>
 * To keep the code simple, we don't worry too much about supporting things that we know to be
 * invalid.  For example, if you try to use a change like
 * <b>addColumn { column(columnName: 'newcolumn') }</b>, you'll get a wonderfully helpful
 * MissingMethodException because of the missing map in the change.  We aren't going to muddy up the
 * code trying to support addColumn changes with no attributes because we know that at least a
 * table name is required.  Similarly, it doesn't make sense to have an addColumn change without at
 * least one column, so we don't deal well with the addColumn change without a closure.
 *
 * @author Steven C. Saliman
 */
@CompileStatic
class ChangeSetDelegate extends Delegatee<Tag> implements ChangeSetChildren {
    protected final ChangeSet changeSet
    protected final boolean inRollback
    protected static ChangeFactory changeFactory = Scope.getCurrentScope().getSingleton(ChangeFactory.class)

    ChangeSetDelegate(ChangeSet changeSet, boolean bInRollback = false) {
        super(changeSet.changeLog, fullChangeSetId(changeSet))
        this.inRollback = bInRollback
        this.changeSet = changeSet
    }

    static enum Tag {
        addAutoIncrement, addColumn, addDefaultValue, addForeignKeyConstraint, addLookupTable,
        addNotNullConstraint, addPrimaryKey, addUniqueConstraint, alterSequence, createIndex, createProcedure,
        createSequence, createTable, createView, customChange, delete, dropAllForeignKeyConstraints, dropColumn,
        dropDefaultValue, dropForeignKeyConstraint, dropIndex, dropNotNullConstraint, dropPrimaryKey,
        dropProcedure, dropSequence, dropTable, dropUniqueConstraint, dropView, empty, executeCommand,
        insert, loadData, loadUpdateData, mergeColumns, modifyDataType, output, preConditions, renameColumn, renameSequence,
        renameTable, renameView, rollback, setColumnRemarks, setTableRemarks, sql, sqlFile, stop, tagDatabase, update
    }
    // -------------------------------------------------------------------------------------------
    // Non refactoring elements.

    void comment(String text) {
        changeSet.comments = DelegateUtil.expandExpressions(text, databaseChangeLog)
    }

    /** Preconditions required to execute the changeset. The closure containing nested elements of a precondition.
     If no conditional tags are specified, the default logic is AND for multiple conditions
     <br>Params:
     <dl>
     <dt>onError</dt>
     <dd>Controls what happens if there is an error checking whether the precondition passed or not.</dd>
     <dt>onErrorMessage</dt>
     <dd>Provides a custom message to output when preconditions fail. Since 2.0</dd>
     <dt>onFail</dt>
     <dd>Controls what happens if the preconditions check fails.</dd>
     <dt>onFailMessage</dt>
     <dd>Provides a custom message to output when preconditions fail. Since 2.0</dd>
     <dt>onSqlOutput</dt>
     <dd>Controls how preconditions are evaluated with the update-sql command for XML, YAML, and JSON changelogs. Since 1.9.5</dd>
     </dl> */
    void preConditions(Map ǃ, FailOption onFail = null, ErrorOption onError = null,
                       String onFailMessage = null, String onErrorMessage = null,
                       OnSqlOutputOption onSqlOutput = null,
                       @DelegatesTo(value = PreconditionDelegate, strategy = DELEGATE_ONLY) Closure preconditions) {
        argsAsMap(Tag.preConditions, ǃ, onFail, onError, onFailMessage, onErrorMessage, onSqlOutput, preconditions)
        changeSet.preconditions =
                buildPreconditionContainer(databaseChangeLog, ǃ, preconditions, changeId)
    }

    /** Preconditions required to execute the changeset. The closure containing nested elements of a precondition.
     If no conditional tags are specified, the default logic is AND for multiple conditions
     <br>Params:
     <dl>
     <dt>onError</dt>
     <dd>Controls what happens if there is an error checking whether the precondition passed or not.</dd>
     <dt>onErrorMessage</dt>
     <dd>Provides a custom message to output when preconditions fail. Since 2.0</dd>
     <dt>onFail</dt>
     <dd>Controls what happens if the preconditions check fails.</dd>
     <dt>onFailMessage</dt>
     <dd>Provides a custom message to output when preconditions fail. Since 2.0</dd>
     <dt>onSqlOutput</dt>
     <dd>Controls how preconditions are evaluated with the update-sql command for XML, YAML, and JSON changelogs. Since 1.9.5</dd>
     </dl> */
    void preConditions(FailOption onFail = null, ErrorOption onError = null,
                       String onFailMessage = null, String onErrorMessage = null,
                       OnSqlOutputOption onSqlOutput = null,
                       @DelegatesTo(value= PreconditionDelegate, strategy=DELEGATE_ONLY) Closure preconditions) {
        preConditions [:], onFail, onError, onFailMessage, onErrorMessage, onSqlOutput, preconditions
    }

    // TODO add closure version
    // 1:any | 1:all | 1:*
    void validCheckSum(String checksum) {
        changeSet.addValidCheckSum(checksum)
    }

    /**
     * Process an empty rollback. This doesn't actually do anything, but empty rollbacks are
     * allowed by the spec.
     */
    void rollback() {
        // To support empty rollbacks (allowed by the spec)
    }

    /** Add the given sql statement to the list of statements should be used to roll back the changes
     * defined in the actual changeset when a rollback... command is executed. */
    void rollback(String sql) {
        changeSet.addRollBackSQL(expandExpressions(sql))
    }

    /**
     * Add changes in the closure to the list of changes should be used to roll back the changes
     * defined in the actual changeset when a rollback... command is executed.
     * The closure can contain nested refactoring changes or raw sql statements. If the closure contains both
     * refactorings and ends with SQL, the SQL will appended to list of rollback changes.
     * @param changes the closure to evaluate.
     */
    void rollback(@DelegatesTo(value = ChangeSetDelegate, strategy = DELEGATE_ONLY) Closure changes) {
        def x = callOn( new ChangeSetDelegate(changeSet, true), changes)
        def sql = expandExpressions(x)
        if ( sql ) {
            changeSet.addRollBackSQL(sql as String)
        }
    }

    /** Reference a changeset containing the changes should be used to roll back the changes
     defined in the actual changeset when a rollback... command is executed

      @param changeSetId the 2nd part of the unique id of the changes
      @param changeSetAuthor the 3rd part of the unique id of the changes
      @param changeSetPath the 1st part of the unique id of the changes. Default: the current logicalFilePath
     */
    void rollback(String changeSetId, String changeSetAuthor, String changeSetPath=null) {
       rollback [:], changeSetId, changeSetAuthor, changeSetPath
    }

    /** {@link #rollback}
     *
     @param changeSetId the 2nd part of the unique id of the changes
     @param changeSetAuthor the 3rd part of the unique id of the changes
     @param changeSetPath the 1st part of the unique id of the changes. Default: the current logicalFilePath
     */
    void rollback(Map ǃ, String changeSetId, String changeSetAuthor=null,
                  String changeSetPath=null) {
        argsAsMap Tag.rollback, ǃ, changeSetId, changeSetAuthor, changeSetPath
    }

    /**
     * Process a rollback when we're doing an attribute based rollback.  The Groovy DSL parser
     * builds a little bit on the XML parser.  With the XML parser, if some attributes are given as
     * attributes, but not a changeSetId, the parser would just skip attribute processing and look
     * for nested tags.  With the Groovy DSL parser, you can't have both a parameter map and a
     * closure, and all supported attributes are meant to find a change set. What This means is that
     * if a map was specified, we need to at least have a valid changeSetId in the map.
     */
    void rollback(Map<String, Object> params) {
        // Process map parameters in a way that will alert the user that we've got an invalid key.
        // This is a bit brute force, but we can clean it up later
        String id = null
        String author = null
        String filePath = null
        params.each { key, value ->
            if ( key == "changeSetId" ) {
                id = expandExpressions(value as String)
            } else if ( key == "changeSetAuthor" ) {
                author = expandExpressions(value as String)
            } else if ( key == "changeSetPath" ) {
                filePath = expandExpressions(value as String)
            } else {
                error InvalidAttribute(Tag.rollback, key)
            }
        }

        // If we don't at least have an ID, we can't continue.
        if ( id == null ) {
            throw new RollbackImpossibleException("no changeSetId given for rollback in '${changeSet.id}'")
        }

        // If we weren't given a path, use the one from the databaseChangeLog
        if ( filePath == null ) {
            filePath = databaseChangeLog.filePath
        }

        def referencedChangeSet = databaseChangeLog.getChangeSet(filePath, author, id)
        if ( referencedChangeSet ) {
            referencedChangeSet.changes.each { change ->
                changeSet.addRollbackChange(change)
            }
        } else {
            throw new RollbackImpossibleException("Could not find changeSet to use for rollback: ${filePath}:${author}:${id}")
        }
    }

    void modifySql(Map params = [:],
                   @DelegatesTo(value = ModifySqlDelegate, strategy = DELEGATE_ONLY) Closure closure) {
        if ( closure ) {
            def delegate = new ModifySqlDelegate(params, changeSet)
            callOn(delegate, closure)

            // No need to expand expressions, the ModifySqlDelegate will do it.
            delegate.sqlVisitors.each {
                changeSet.addSqlVisitor(it)
            }
        }
    }

    void groovyChange(Closure closure) {
        def delegate = new GroovyChangeDelegate(closure, changeSet)
        delegate.changeSet = changeSet
        closure.delegate = delegate
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    // ------------------------------------------------------------------------------------------
    // Refactoring changes.  Most changes will be handled by method missing.  We only need to define
    // methods that take closures or strings.

    /**
     * Groovy calls methodMissing when it can't find a matching method to call.  We use it to create
     * a Liquibase change with the same name as the element.  The methodMissing method can only
     * create changes that are present in the Liquibase Registry, have no nested elements, and take
     * maps as attributes.
     * <p>
     * Changes that allow nested elements need special handling in their own methods to handle the
     * closure delegate needed to process the nested elements.  Non-map arguments (like strings)
     * also need special handling.
     * @param name the name of the method Groovy wanted to call.  We'll assume it is a valid
     *        Liquibase change name.
     * @param params the original arguments to that method.  We can only handle a single map here.
     * @throws ChangeLogParseException if there is no change with the given name in the registry.
     */
    protected def methodMissing(String name, params) {
        MethodDef m = methodDefs[name]
        def args = params as Object[]
        if(m) {// Let the map version to handle it: no change methods: preConditions with different parameter types
           return callSingleMapArgVersion (name, m, args)
        }
        // Start by looking up the change.  I want to let users know about invalid change names
        // before I start validating the arguments.
        Change change = lookupChange(name)

        // Process the change if the arguments are good.
        if ( args == null || args.length == 0 ) {
            // We can handle this.  Just look up the change and add it.
            addChange(change)
        } else if ( m ) { // There is a dedicated method
            addChange setProps(change, argsAsMap(name, m, args))
        } else if ( args.length == 1 && args[0] instanceof Map ) {
            // This is our most common use case - a single map argument.  As a side effect, we
            // lookup the change again, but that's fine.
            addChange setProps(change, args[0] as Map)
        } else {
            error new InvalidArguments(name, '', changeId, args) // TODO collect args from change
        }
        return null
    }

    /** Helper method called from the generated methods. Calling (Tag, Map) version is more efficient
     Helps to find missing method definitions
     */
    protected Change addChange(NullChecker<Tag> args, @DelegatesTo(strategy = DELEGATE_ONLY) Closure cl = null) {
        if(cl) {
            return addChangeWithChild(args.elem, args.asMap, cl)
        }else {
            return addChange(args.elem, args.asMap)
        }
    }

    /** Helper method called from the generated methods. Calling (Tag, Map) version is more efficient
      Helps to find missing method definitions
     */
    protected Change addChange(Tag t, Object... args) {
        MethodDef m = methodDefs[t.name()]
        if ( m ) { // There is a dedicated method
            //boolean requiresClosure = requiresClosure(m)
            if ( m.lastArgClosure ) {
               return addChangeWithChild(t, argsAsMap(t.name(), m, args), cast(args.last(), Closure))
            } else {
               return addChange(t, argsAsMap(t.name(), m, args))
            }
        } else throw new RuntimeException("No method found for $t") // Helps to find missing method definitions
    }

    /**
     * process an addForeignKeyConstraint change.  This change has a deprecated property for which
     * we need a warning.
     * @param params the properties to set on the new changes.
     */
    void addForeignKeyConstraint(Map params) {
        addChange(Tag.addForeignKeyConstraint, params)
        if ( params['referencesUniqueColumn'] != null ) {
            println "Warning: ChangeSet '${changeSet.id}': addForeignKeyConstraint's referencesUniqueColumn parameter has been deprecated, and may be removed in a future release."
            println "Consider removing it, as Liquibase ignores it anyway."
        }
    }

    /** Create a definition for a stored procedure from either the tag content or from file defined by {@code path.}
     <br>Params:<dl>
     <dt>procedureName</dt>
     <dd>Name of the stored procedure. Required if replaceIfExists=true.</dd>
     <dt>path</dt>
     <dd>File containing the procedure text. Either this attribute or a nested procedure text is required.</dd>
     <dt>relativeToChangelogFile</dt>
     <dd>Specifies whether the path defined in {@code path} is relative to the
     changelog file rather than looked up in the search path. Default: false
     See: https://docs.liquibase.com/concepts/changelogs/how-liquibase-finds-files.html</dd>
     <dt>dbms</dt>
     <dd>Specifies which database type(s) a changeset is to be used for.
     See valid database type names on Liquibase Database Tutorials
     . Separate multiple databases with commas. Specify that a changeset is not applicable to a particular
     database type by prefixing with !. The keywords all and none are also available.
     Will run for all dbms' if empty or absent</dd>
     <dt>replaceIfExists</dt>
     <dd>If the stored procedure defined by {@code procedureName} already exists, alter it instead of creating it. Default: false.</dd>
     </dl> */
    void createProcedure(Map params = [:], // TODO could be generated
                         @DelegatesTo(value = CreateProcedureDelegate, strategy = DELEGATE_ONLY) Closure<String> procedureText = null) {
        addChangeWithMixedChild chkMap(Tag.createProcedure, params),'procedureText', procedureText
    }

    /**
     * Processes a customChange change, which takes a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with key value pairs for the change.
     */
    @TypeChecked(TypeCheckingMode.SKIP)
    void customChange(Map params,
                      @DelegatesTo(strategy = DELEGATE_ONLY) Closure closure = null) {
        CustomChangeWrapper change = lookupChange('customChange')
        if ( closure ) {
            change.classLoader = closure.getClass().getClassLoader()
        } else {
            change.classLoader = this.class.classLoader
        }
        String className = expandExpressions(params['class'])
        change.setClass(className)

        if ( closure ) {
            def delegate = new KeyValueDelegate('customChange', changeSet.id)
            callOn(delegate, closure)
            delegate.map.each { key, value ->
                // expandExpressions because the delegate won't
                change.setParam(key, expandExpressions(value) as String)
            }
        }

        addChange(change)
    }

    /**
     * A Groovy-specific extension that allows a closure to be provided, implementing the change.
     * The closure is passed the instance of Database.
     */
    void customChange(Closure closure) {
        //TODO Figure out how to implement closure-based custom changes.  It's not easy, since the
        // closure would probably need the Database object to be interesting, and that's not
        // available at parse time. Perhaps we could keep this closure around somewhere to run
        // later when the Database is alive.
    }

    /**
     * Process an "empty" changes.  It doesn't do anything, but it is allowed by the spec.
     */
    // Match Present  We could load this one, or not as we see fit.
    void empty() {
        // To support empty changes (allowed by the spec)
    }

    /**
     * Output {@code message} into {@code target} stream
     * @param ǃ
     * @param message text to output
     * @param target stream output {@code message} into
     */
    void output(String message, String target = null) {
        output argsAsMap(Tag.output, message, target)
    }

    /**
     *  Output {@code message} into {@code target} stream
     * @param message text to output
     * @param target stream output {@code message} into
     */
    void output(Map ǃ, String message, String target = null ) {
        addChange Tag.output, argsAsMap(Tag.output, ǃ, message, target)
    }

/*  Processes an output change. This method only takes a map, but we can't use methodMissing for
    this change because Liquibase initializes the target to the invalid value of an empty string
    instead of null.*/
    /**
     * Output {@code message} into {@code target} stream
     * @param params the properties to set. Valid parameters are:<br>
     * {@code message} text to output<br>
     * {@code target} stream output {@code message} into
     */
    void output(Map params=null, Closure<String> message = null) {
        // Workaround for Issue #28:  Liquibase initializes the target to the invalid value of an
        // empty string instead of null, then checks for null when deciding if it wants to use the
        // default of STDERR.  workaround this by explicitly setting the default if no target was
        // given.
        if ( !params.containsKey('target') ) {
            params.target = 'STDERR'
        }
        addChange chkMap(Tag.output, params)('message',message ? message() as String: null)
    }

    protected void addChangeWithMixedChild(NullChecker params, String childName,
                                 @DelegatesTo(strategy = DELEGATE_ONLY) Closure<String> child) {
        Change change = addChange(lookupChange(params.elem.name()))
        setProps change, params(childName, callOnDelegate(change, child)).asMap
    }

    /** Execute any SQL statement(s) in the content.
     The SQL change can also contain comments of either of the following formats:
     A multi-line comment that starts with /* and ends with *\/.
     A single line comment starting with -- and finishing at the end of the line.
     or a comment element can be used
     <br>Params:<dl>
     <dt>stripComments</dt>
     <dd>When true, any comments are removed in the statement before executing on the database. Default: true.</dd>
     <dt>splitStatements</dt>
     <dd>When true, Liquibase splits statements on {@code endDelimiter} and executes them separately. Default: true.</dd>
     <dt>endDelimiter</dt>
     <dd>The delimiter to separate raw SQL statements. The default value is `;`
     See: https://docs.liquibase.com/change-types/enddelimiter-sql.html</dd>
     <dt>dbms</dt>
     <dd>Specifies which database type(s) a changeset is to be used for.
     See valid database type names on Liquibase Database Tutorials
     . Separate multiple databases with commas. Specify that a changeset is not applicable to a particular
     database type by prefixing with !. The keywords all and none are also available.
     Will run for all dbms' if empty or absent</dd>
     </dl> */
    void sql(Map ǃ, Boolean stripComments=null, String dbms=null, Boolean splitStatements=null, String endDelimiter=null,
             @DelegatesTo(value = SqlDelegate, strategy = DELEGATE_ONLY) Closure<String> sql) {
        addChangeWithMixedChild( chkMap(Tag.sql, ǃ) ('stripComments',stripComments) ('dbms',dbms) ('splitStatements',splitStatements) ('endDelimiter',endDelimiter), 'sql', sql)
    }

    /** Execute any SQL statement(s) in the content.
     The SQL change can also contain comments of either of the following formats:
     A multi-line comment that starts with /* and ends with *\/.
     A single line comment starting with -- and finishing at the end of the line.
     or a comment element can be used
     <br>Params:<dl>
     <dt>stripComments</dt>
     <dd>When true, any comments are removed in the statement before executing on the database. Default: true.</dd>
     <dt>splitStatements</dt>
     <dd>When true, Liquibase splits statements on {@code endDelimiter} and executes them separately. Default: true.</dd>
     <dt>endDelimiter</dt>
     <dd>The delimiter to separate raw SQL statements. The default value is `;`
     See: https://docs.liquibase.com/change-types/enddelimiter-sql.html</dd>
     <dt>dbms</dt>
     <dd>Specifies which database type(s) a changeset is to be used for.
     See valid database type names on Liquibase Database Tutorials
     . Separate multiple databases with commas. Specify that a changeset is not applicable to a particular
     database type by prefixing with !. The keywords all and none are also available.
     Will run for all dbms' if empty or absent</dd>
     </dl> */ // TODO should be generated
    void sql( Boolean stripComments=null, String dbms=null, Boolean splitStatements=null, String endDelimiter=null,
              @DelegatesTo(value=SqlDelegate, strategy=DELEGATE_ONLY) Closure<String> sql) {
        //sql chkMap(Tag.sql) ('stripComments',stripComments) ('dbms',dbms) ('splitStatements',splitStatements) ('endDelimiter',endDelimiter) .asMap, sql
        addChangeWithMixedChild( chkMap(Tag.sql) ('stripComments',stripComments) ('dbms',dbms) ('splitStatements',splitStatements) ('endDelimiter',endDelimiter), 'sql', sql)
    }

    void sql(String sql, Boolean stripComments=null, String dbms=null, Boolean splitStatements=null, String endDelimiter=null) {
        addChange chkMap(Tag.sql) ('sql',sql)('stripComments', stripComments)('dbms', dbms) ('splitStatements',splitStatements) ('endDelimiter' , endDelimiter)
    }

    void sql(Map ǃ, String sql, Boolean stripComments=null, String dbms=null, Boolean splitStatements=null, String endDelimiter=null) {
        addChange chkMap(Tag.sql,ǃ) ('sql',sql)('dbms', dbms)('stripComments', stripComments) ('splitStatements',splitStatements) ('endDelimiter' , endDelimiter)
    }

     /**
     * Processes a sqlFile change.  We can't use methodMissing here because we have additional
     * validation we need to do.
     * @param params the properties to set on the new changes.
     */
    void sqlFile(Map params) {
        // It doesn't make sense to have SQL in a sqlFile change, even though liquibase allows it.
        if ( params.containsKey('sql') ) {
            throw new ChangeLogParseException("ChangeSet '${changeSet.id}': 'sql' is an invalid property for 'sqlFile' changes.")
        }
        SQLFileChange change = addChange(Tag.sqlFile, params) as SQLFileChange
        // Before we add the change, work around the Liquibase bug where sqlFile change sets don't
        // load the SQL until it is too late to calculate checksums properly after a clearChecksum
        // command.  See https://liquibase.jira.com/browse/CORE-1293
        change.finishInitialization()
    }

    /**
     * Stop Liquibase execution with a message
     * {@code stop 'some message'} instead of the usual parameter based change.
     * @param message Message to send to output
    */
    void stop(String message) {
        stop([message:message])
    }

    void stop(Map args) {
        addChange(Tag.stop, args)
    }

    /** Apply a tag to the database for future update or rollback.
     * See <a href='https://docs.liquibase.com/change-types/tag-database.html'>tagDatabase</a>
     * @param tag the tag to apply.
     */
    void tagDatabase(String tag) {
        tagDatabase([tag: tag])
    }

    /** {@link #tagDatabase} */
    void tagDatabase(Map args) {
        addChange(Tag.tagDatabase, args)
    }

    /**
     * lookup a change from the Liquibase registry and return an instance of the change class.
     * @param name the name of the change to find.
     * @return an instance of the correct change.
     * @throws UnrecognizedElement if there is no change with the given name in the registry.
     */
    @PackageScope <T extends Change> T lookupChange(String name) {
        Change change = changeFactory.create(name)

        if ( change == null ) {
            error new UnrecognizedElement(name, changeFactory.definedChanges)
        }
        return change as T
    }

    /** Create a Delegate belonging to the given change(name), then call the closure on it. */
    @PackageScope def callOnDelegate(Change change,
                                     @DelegatesTo(strategy = DELEGATE_ONLY) Closure closure,
                                     clArgs = null) {
        Class<ChangeDelegate> delegateClass = closureDelegate(change.serializedObjectName)
        def delegate = delegateClass.newInstance(this,
                    ColumnDelegate.isAssignableFrom(delegateClass) ?
                                              change as ChangeWithColumns : change)
        callOn(delegate, closure, clArgs)
        //delegate
    }

    /**
     * Create a Liquibase change for the types of changes that can have a nested closure of columns
     * and where clauses.
     * @param name the name of the change to make, used for improved error messages.
     * @param changeClass the Liquibase class to create.
     * @param columnConfigClass the class for the nested column configuration.
     * @param closure the closure with column information
     * @param params a map containing attributes of the new change
     * @param paramNames a list of valid properties for the new change
     * @return the newly created change
     */
    @PackageScope
    <T extends Change> T addChangeWithChild(Tag name,
                                           Map params,
                                           @DelegatesTo(strategy = DELEGATE_ONLY) Closure closure) {
        T change = makeChangeFromMap(name.name(), params)

        // Make a new delegate and give it the change to populate.
        if(closure) {
            callOnDelegate(change, closure)
        }
        addChange change
    }

    /**
     * Create a new Liquibase change and set its properties from the given map of parameters.
     * @param klass the type of change to create/
     * @param sourceMap a map of parameter names and values for the new change
     * @return the newly create change, with the appropriate properties set.
     * @throws ChangeLogParseException if the source map contains any keys that are not in the list
     *         of valid paramNames.
     */
    @PackageScope <T extends Change> T makeChangeFromMap(String name, Map<String, Object> sourceMap) {
        setProps lookupChange(name), sourceMap
    }

    /**
     * Helper method used by changes that don't have closures, just attributes that get set from the
     * parameter map.  This method will add the newly created change to the current change set.
     * @param name the name of the change. Used for improved error messages.
     * @param sourceMap the map of attributes to set on the Liquibase change.
     */
    protected <T extends Change> T addChange(Tag name, Map sourceMap) {
        addChange(makeChangeFromMap(name.name(), sourceMap))
    }

    /**
     * Helper method to add a change to the current change set.
     * @param change the change to add
     * @return the change
     */
    protected <T extends Change> T addChange(T change) {
        if ( inRollback ) {
            changeSet.addRollbackChange(change)
        } else {
            changeSet.addChange(change)
        }
        return change
    }
}

@CompileStatic
abstract class ChangeDelegate<Tag extends Enum<Tag>> extends Delegatee<Tag> {
    protected final Change change
    ChangeDelegate(ChangeSetDelegate changeSet, Change change ) {
        super(changeSet.databaseChangeLog, changeSet.changeId, (change as LiquibaseSerializable).serializedObjectName)
        this.change = change
    }

    UnrecognizedElement unrecognizedElement(String name) {
        new UnrecognizedElement(name, null, changeId,
                "Unrecognized child element: '$name' for '$parent'! Valid elements are ${knownElements().toListString()}")
    }
}

@CompileStatic
class DeleteDelegate extends ChangeDelegate implements WhereDelegate{
    DeleteDelegate(ChangeSetDelegate changeSet, Change change) {
        super(changeSet, change)
    }
}

@CompileStatic
class UpdateDelegate extends InsertDelegate implements WhereDelegate {
    UpdateDelegate(ChangeSetDelegate changeSet, ChangeWithColumns change) {
        super(changeSet, change)
    }
}
