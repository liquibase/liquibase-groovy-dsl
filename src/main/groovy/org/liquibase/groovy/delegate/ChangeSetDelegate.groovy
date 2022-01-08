/*
 * Copyright 2011-2022 Tim Berglund and Steven C. Saliman
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

import liquibase.Scope
import liquibase.change.AddColumnConfig
import liquibase.change.ChangeFactory
import liquibase.change.ColumnConfig
import liquibase.change.core.LoadDataColumnConfig
import liquibase.exception.ChangeLogParseException
import liquibase.exception.RollbackImpossibleException
import liquibase.util.PatchedObjectUtil

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
class ChangeSetDelegate {
    def changeSet
    def databaseChangeLog
    def inRollback
    def changeFactory = Scope.getCurrentScope().getSingleton(ChangeFactory.class)

    // -------------------------------------------------------------------------------------------
    // Non refactoring elements.

    void comment(String text) {
        changeSet.comments = DelegateUtil.expandExpressions(text, databaseChangeLog)
    }

    void preConditions(Map params = [:], Closure closure) {
        changeSet.preconditions = PreconditionDelegate.buildPreconditionContainer(databaseChangeLog, changeSet.id, params, closure)
    }

    void validCheckSum(String checksum) {
        changeSet.addValidCheckSum(checksum)
    }

    /**
     * Process an empty rollback.  This doesn't actually do anything, but empty rollbacks are
     * allowed by the spec.
     */
    void rollback() {
        // To support empty rollbacks (allowed by the spec)
    }


    void rollback(String sql) {
        changeSet.addRollBackSQL(DelegateUtil.expandExpressions(sql, databaseChangeLog))
    }

    /**
     * Process a rollback when the rollback changes are passed in as a closure.  The closure can
     * contain nested refactoring changes or raw sql statements.  I don't know what the XML parser
     * will do, but if the closure contains both refactorings and ends with SQL, the Groovy DSL
     * parser will append the SQL to list of rollback changes.
     * @param closure the closure to evaluate.
     */
    void rollback(Closure closure) {
        def delegate = new ChangeSetDelegate(changeSet: changeSet,
                databaseChangeLog: databaseChangeLog,
                inRollback: true)
        closure.delegate = delegate
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        def x = closure.call()
        def sql = DelegateUtil.expandExpressions(x, databaseChangeLog)
        if ( sql ) {
            changeSet.addRollBackSQL(sql)
        }
    }

    /**
     * Process a rollback when we're doing an attribute based rollback.  The Groovy DSL parser
     * builds a little bit on the XML parser.  With the XML parser, if some attributes are given as
     * attributes, but not a changeSetId, the parser would just skip attribute processing and look
     * for nested tags.  With the Groovy DSL parser, you can't have both a parameter map and a
     * closure, and all supported attributes are meant to find a change set. What This means is that
     * if a map was specified, we need to at least have a valid changeSetId in the map.
     * @param params
     */
    void rollback(Map params) {
        // Process map parameters in a way that will alert the user that we've got an invalid key.
        // This is a bit brute force, but we can clean it up later
        def id = null
        def author = null
        def filePath = null
        params.each { key, value ->
            if ( key == "changeSetId" ) {
                id = DelegateUtil.expandExpressions(value, databaseChangeLog)
            } else if ( key == "changeSetAuthor" ) {
                author = DelegateUtil.expandExpressions(value, databaseChangeLog)
            } else if ( key == "changeSetPath" ) {
                filePath = DelegateUtil.expandExpressions(value, databaseChangeLog)
            } else {
                throw new ChangeLogParseException("ChangeSet '${changeSet.id}': '${key}' is not a valid rollback attribute.")
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

    void modifySql(Map params = [:], Closure closure) {
        if ( closure ) {
            def delegate = new ModifySqlDelegate(params, changeSet)
            closure.delegate = delegate
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure.call()

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
     * @param args the original arguments to that method.  We can only handle a single map here.
     * @throws ChangeLogParseException if there is no change with the given name in the registry.
     */
    def methodMissing(String name, args) {
        // Start by looking up the change.  I want to let users know about invalid change names
        // before I start validating the arguments.
        def change = lookupChange(name)

        // Process the change if the arguments are good.
        if ( args == null || args.length == 0 ) {
            // We can handle this.  Just look up the change and add it.
            addChange(change)
        } else if ( args.length > 1 || !args[0] instanceof Map ) {
            // We can't handle changes with more than one argument.
            throw new ChangeLogParseException("ChangeSet '${changeSet.id}': '${name}' changes are only valid with a single map argument")
        } else {
            // This is our most common use case - a single map argument.  As a side effect, we
            // lookup the change again, but that's fine.
            addMapBasedChange(name, args[0])
        }
        return null
    }

    /**
     * Processes an addColumn change, which takes a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the nested columns for the change.
     */
    void addColumn(Map params, Closure closure) {
        addChange(makeColumnarChangeFromMap('addColumn', AddColumnConfig, params, closure))
    }

    /**
     * process an addForeignKeyConstraint change.  This change has a deprecated property for which
     * we need a warning.
     * @param params the properties to set on the new changes.
     */
    void addForeignKeyConstraint(Map params) {
        addMapBasedChange('addForeignKeyConstraint', params)
        if ( params['referencesUniqueColumn'] != null ) {
            println "Warning: ChangeSet '${changeSet.id}': addForeignKeyConstraint's referencesUniqueColumn parameter has been deprecated, and may be removed in a future release."
            println "Consider removing it, as Liquibase ignores it anyway."
        }
    }

    /**
     * Processes a createIndex change, which takes a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the nested columns for the change.
     */
    void createIndex(Map params, Closure closure) {
        addChange(makeColumnarChangeFromMap('createIndex', AddColumnConfig, params, closure))
    }

    /**
     * Processes a createProcedure change, which takes a closure in addition to an optional
     * parameter map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the definition of the procedure.
     */
    void createProcedure(Map params = [:], Closure closure) {
        def change = makeChangeFromMap('createProcedure', params)
        change.procedureText = DelegateUtil.expandExpressions(closure.call(), databaseChangeLog)
        addChange(change)
    }

    /**
     * Processes a createProcedure change.  This version of the method processes a createProcedure
     * change where the text of the procedure is given as a string instead of in a closure.
     * @param storedProc the definition of the procedure to create.
     */
    void createProcedure(String storedProc) {
        def change = lookupChange('createProcedure')
        change.procedureText = DelegateUtil.expandExpressions(storedProc, databaseChangeLog)
        addChange(change)
    }

    /**
     * Processes a createTable change, which takes a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the nested columns for the change.
     */
    void createTable(Map params, Closure closure) {
        addChange(makeColumnarChangeFromMap('createTable', ColumnConfig, params, closure))
    }

    /**
     * Processes a createView change, which takes a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the nested columns for the change.
     */
    void createView(Map params, Closure closure) {
        def change = makeChangeFromMap('createView', params)
        change.selectQuery = DelegateUtil.expandExpressions(closure.call(), databaseChangeLog)
        addChange(change)
    }

    /**
     * Processes a customChange change, which takes a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with key value pairs for the change.
     */
    void customChange(Map params, Closure closure = null) {
        def change = lookupChange('customChange')
        if ( closure ) {
            change.classLoader = closure.getClass().getClassLoader()
        } else {
            change.classLoader = this.class.classLoader
        }
        String className = DelegateUtil.expandExpressions(params['class'], databaseChangeLog)
        change.setClass(className)

        if ( closure ) {
            def delegate = new KeyValueDelegate()
            closure.delegate = delegate
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure.call()
            delegate.map.each { key, value ->
                // expandExpressions because the delegate won't
                change.setParam(key, DelegateUtil.expandExpressions(value, databaseChangeLog))
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
     * Processes a delete change, which can take a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the nested columns for the change.
     */
    void delete(Map params, Closure closure) {
        addChange(makeColumnarChangeFromMap('delete', ColumnConfig, params, closure))
    }

    /**
     * Processes a dropColumn change, which takes a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the nested columns for the change.
     */
    void dropColumn(Map params, Closure closure) {
        addChange(makeColumnarChangeFromMap('dropColumn', ColumnConfig, params, closure))
    }

    /**
     * Process an "empty" changes.  It doesn't do anything, but it is allowed by the spec.
     */
    // Match Present  We could load this one, or not as we see fit.
    void empty() {
        // To support empty changes (allowed by the spec)
    }


    /**
     * Processes an executeCommand change, which can take a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with arguments for the change.
     */
    void executeCommand(Map params, Closure closure) {
        def change = makeChangeFromMap('executeCommand', params)
        def delegate = new ArgumentDelegate(changeSetId: changeSet.id,
                changeName: 'executeCommand')
        closure.delegate = delegate
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        delegate.args.each { arg ->
            // expand expressions because the argument delegate won't...
            change.addArg(DelegateUtil.expandExpressions(arg, databaseChangeLog))
        }

        addChange(change)
    }

    /**
     * Processes an insert change, which takes a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the nested columns for the change.
     */
    void insert(Map params, Closure closure) {
        addChange(makeColumnarChangeFromMap('insert', ColumnConfig, params, closure))
    }

    /**
     * Processes a loadData change, which takes a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the nested columns for the change.
     */
    void loadData(Map params, Closure closure) {
        if ( params.file instanceof File ) {
            throw new ChangeLogParseException("Warning: ChangeSet '${changeSet.id}': using a File object for loadData's 'file' attribute is no longer supported.  Use the path to the file instead.")
        }

        addChange(makeColumnarChangeFromMap('loadData', LoadDataColumnConfig, params, closure))
    }

    /**
     * Processes a loadUpdateData change, which takes a closure in addition to a map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the nested columns for the change.
     */
    void loadUpdateData(Map params, Closure closure) {
        if ( params.file instanceof File ) {
            throw new ChangeLogParseException("Warning: ChangeSet '${changeSet.id}': using a File object for loadUpdateData's 'file' attribute is no longer supported.  Use the path to the file instead.")
        }

        addChange(makeColumnarChangeFromMap('loadUpdateData', LoadDataColumnConfig, params, closure))
    }

    /**
     * Processes an output change. This method only takes a map, but we can't use methodMissing for
     * this change because Liquibase initializes the target to the invalid value of an empty string
     * instead of null.
     *
     * @param params the properties to set on the new changes.
     */
    void output(Map params) {
        // Workaround for Issue #28:  Liquibase initializes the target to the invalid value of an
        // empty string instead of null, then checks for null when deciding if it wants to use the
        // default of STDERR.  workaround this by explicitly setting the default if no target was
        // given.
        if ( !params.containsKey('target') ) {
            params.target = 'STDERR'
        }
        addMapBasedChange('output', params)
    }

    /**
     * Processes a sql change.  This version of it takes the SQL as a closure.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the SQL for the change.
     */
    void sql(Map params = [:], Closure closure) {
        def change = makeChangeFromMap('sql', params)
        def delegate = new CommentDelegate(changeSetId: changeSet.id,
                changeName: 'sql')
        closure.delegate = delegate
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        // expand expressions because the comment delegate won't...
        change.sql = DelegateUtil.expandExpressions(closure.call(), databaseChangeLog)
        change.comment = (DelegateUtil.expandExpressions(delegate.comment, databaseChangeLog))
        addChange(change)
    }

    /**
     * Parse a sql change.  This version of the method is syntactic sugar that allows
     * {@code sql 'some query'} in stead of the usual parameter based change.
     * @param sql the SQL for the change.
     */
    void sql(String sql) {
        def change = lookupChange('sql')
        change.sql = DelegateUtil.expandExpressions(sql, databaseChangeLog)
        addChange(change)
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
        def change = makeChangeFromMap('sqlFile', params)
        // Before we add the change, work around the Liquibase bug where sqlFile change sets don't
        // load the SQL until it is too late to calculate checksums properly after a clearChecksum
        // command.  See https://liquibase.jira.com/browse/CORE-1293
        change.finishInitialization()

        addChange(change)
    }

    /**
     * Parse a stop change.  This version of the method is syntactic sugar that allows
     * {@code stop 'some message'} in stead of the usual parameter based change.
     * @param message the stop message.
     */
    void stop(String message) {
        def change = lookupChange('stop')
        change.message = DelegateUtil.expandExpressions(message, databaseChangeLog)
        addChange(change)
    }

    /**
     * Parse a tagDatabase change.  This version of the method is syntactic sugar that allows
     * {@code tagDatabase 'my-tag-name'} in stead of the usual parameter based change.
     * @param tagName the name of the tag to create.
     */
    // Match Present
    void tagDatabase(String tagName) {
        def change = lookupChange('tagDatabase')
        change.tag = DelegateUtil.expandExpressions(tagName, databaseChangeLog)
        addChange(change)
    }

    /**
     * Processes an update change, which takes a closure in addition to a  map.
     * @param params the properties to set on the new changes.
     * @param closure the closure to call with the nested columns for the change.
     */
    void update(Map params, Closure closure) {
        addChange(makeColumnarChangeFromMap('update', ColumnConfig, params, closure))
    }

    /**
     * lookup a change from the Liquibase registry and return an instance of the change class.
     * @param name the name of the change to find.
     * @return an instance of the correct change.
     * @throws ChangeLogParseException if there is no change with the given name in the registry.
     */
    private def lookupChange(name) {
        def change = changeFactory.create(name)

        if ( change == null ) {
            throw new ChangeLogParseException("ChangeSet '${changeSet.id}': '${name}' is not a valid element of a ChangeSet")
        }
        return change
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
    private def makeColumnarChangeFromMap(String name,
                                          columnConfigClass, Map params,
                                          Closure closure) {
        def change = makeChangeFromMap(name, params)

        def columnDelegate = new ColumnDelegate(columnConfigClass: columnConfigClass,
                databaseChangeLog: databaseChangeLog,
                changeSetId: changeSet.id,
                changeName: name)
        closure.delegate = columnDelegate
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()

        // Try to add the columns to the change.  If we're dealing with something like a "delete"
        // change, we'll get an exception, which we'll rethrow as a parse exception to tell the user
        // that columns are not allowed in that change.
        columnDelegate.columns.each { column ->
            try {
                change.addColumn(column)
            } catch (MissingMethodException e) {
                throw new ChangeLogParseException("ChangeSet '${changeSet.id}': columns are not allowed in '${name}' changes.", e)
            }
        }

        // If we have a where clause, try to set it in the change.  We'll get an exception if a
        // where clause is not supported by the change.
        if ( columnDelegate.whereClause != null ) {
            try {
                // The columnDelegate DOES take care of expansion.
                PatchedObjectUtil.setProperty(change, 'where', columnDelegate.whereClause)
            } catch (RuntimeException e) {
                throw new ChangeLogParseException("ChangeSet '${changeSet.id}': a where clause is invalid for '${name}' changes.", e)
            }

        }
        return change

    }

    /**
     * Create a new Liquibase change and set its properties from the given map of parameters.
     * @param klass the type of change to create/
     * @param sourceMap a map of parameter names and values for the new change
     * @return the newly create change, with the appropriate properties set.
     * @throws ChangeLogParseException if the source map contains any keys that are not in the list
     *         of valid paramNames.
     */
    private def makeChangeFromMap(String name, Map sourceMap) {
        def change = lookupChange(name)

        sourceMap.each { key, value ->
            try {
                PatchedObjectUtil.setProperty(change, key, DelegateUtil.expandExpressions(value, databaseChangeLog))
            }
            catch (NumberFormatException ex) {
                change[key] = value.toBigInteger()
            }
            catch (RuntimeException re) {
                throw new ChangeLogParseException("ChangeSet '${changeSet.id}': '${key}' is an invalid property for '${name}' changes.", re)
            }

        }
        return change
    }

    /**
     * Helper method used by changes that don't have closures, just attributes that get set from the
     * parameter map.  This method will add the newly created change to the current change set.
     * @param name the name of the change.  Used for improved error messages.
     * @param klass the Liquibase class to make for the change.
     * @param sourceMap the map of attributes to set on the Liquibase change.
     * @param paramNames a list of valid attribute names.
     */
    private def addMapBasedChange(String name, Map sourceMap) {
        addChange(makeChangeFromMap(name, sourceMap))
    }

    /**
     * Helper method to add a change to the current change set.
     * @param change the change to add
     * @return the modified change set.
     */
    private def addChange(change) {
        if ( inRollback ) {
            changeSet.addRollbackChange(change)
        } else {
            changeSet.addChange(change)
        }
        return changeSet
    }
}
