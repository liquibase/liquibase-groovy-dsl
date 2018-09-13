/*
 * Copyright 2011-2018 Tim Berglund and Steven C. Saliman
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.liquibase.groovy.delegate


import liquibase.change.Change
import liquibase.change.ChangeFactory
import liquibase.change.ChangeWithColumns
import liquibase.change.ColumnConfig
import liquibase.change.core.*
import liquibase.change.custom.CustomChangeWrapper
import liquibase.exception.ChangeLogParseException
import liquibase.exception.RollbackImpossibleException
import liquibase.util.PatchedObjectUtil

import java.lang.reflect.ParameterizedType

/**
 * This class is the closure delegate for a ChangeSet.  It processes all the
 * refactoring changes for the ChangeSet.  it basically creates all the changes
 * that need to belong to the ChangeSet, but it doesn't worry too much about
 * validity of the change because Liquibase itself will deal with that.
 * <p>
 * To keep the code simple, we don't worry too much about supporting things
 * that we know to be invalid.  For example, if you try to use a change like
 * <b>addColumn { column(columnName: 'newcolumn') }</b>, you'll get a
 * wonderfully helpful MissingMethodException because of the missing map in
 * the change.  We aren't going to muddy up the code trying to support
 * addColumn changes with no attributes because we know that at least a
 * table name is required.  Similarly, it doesn't make sense to have an
 * addColumn change without at least one column, so we don't deal well with the
 * addColumn change without a closure.
 *
 * @author Steven C. Saliman
 */
class ChangeSetDelegate {
	def changeSet
	def databaseChangeLog
	def resourceAccessor
	def inRollback
	private Map<String, SortedSet<Class<? extends Change>>> registry

	// ------------------------------------------------------------------------
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
	 * Process an empty rollback.  This doesn't actually do anything, but empty
	 * rollbacks are allowed by the spec.
	 */
	void rollback() {
		// To support empty rollbacks (allowed by the spec)
	}


	void rollback(String sql) {
		changeSet.addRollBackSQL(DelegateUtil.expandExpressions(sql, databaseChangeLog))
	}

	/**
	 * Process a rollback when the rollback changes are passed in as a closure.
	 * The closure can contain nested refactoring changes or raw sql statements.
	 * I don't know what the XML parser will do, but if the closure contains
	 * both refactorings and ends with SQL, the Groovy DSL parser will append the
	 * SQL to list of rollback changes.
	 * @param closure the closure to evaluate.
	 */
	void rollback(Closure closure) {
		def delegate = new ChangeSetDelegate(changeSet: changeSet,
						databaseChangeLog: databaseChangeLog,
						inRollback: true,
						resourceAccessor: resourceAccessor)
		closure.delegate = delegate
		closure.resolveStrategy = Closure.DELEGATE_FIRST
		def sql = DelegateUtil.expandExpressions(closure.call(), databaseChangeLog)
		if ( sql ) {
			changeSet.addRollBackSQL(sql)
		}
	}

	/**
	 * Process a rollback when we're doing an attribute based rollback.  The
	 * Groovy DSL parser builds a little bit on the XML parser.  With the XML
	 * parser, if some attributes are given as attributes, but not a changeSetId,
	 * the parser would just skip attribute processing and look for nested tags.
	 * With the Groovy DSL parser, you can't have both a parameter map and a
	 * closure, and all supported attributes are meant to find a change set. What
	 * This means is that if a map was specified, we need to at least have a
	 * valid changeSetId in the map.
	 * @param params
	 */
	void rollback(Map params) {
		// Process map parameters in a way that will alert the user that we've got
		// an invalid key.  This is a bit brute force, but we can clean it up later
		def id = null
		def author = null
		def filePath = null
		if ( params.containsKey('id') ) {
			throw new ChangeLogParseException("Error: ChangeSet '${changeSet.id}': the 'id' attribute of a rollback has been removed. Use 'changeSetId' instead.")
		}
		if ( params.containsKey('author') ) {
			throw new ChangeLogParseException("Error: ChangeSet '${changeSet.id}': the 'author' attribute of a rollback has been removed. Use 'changeSetAuthor' instead.")
		}
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
		def delegate = new GroovyChangeDelegate(closure, changeSet, resourceAccessor)
		delegate.changeSet = changeSet
		delegate.resourceAccessor = resourceAccessor
		closure.delegate = delegate
		closure.resolveStrategy = Closure.DELEGATE_FIRST
		closure.call()
	}

	// -----------------------------------------------------------------------
	// Refactoring changes

	/**
	 * process an addForeignKeyConstraint change.  This change has a deprecated
	 * property for which we need a warning.
	 * @param params the properties to set on the new changes.
	 */
	void addForeignKeyConstraint(Map params) {
		if ( params['referencesUniqueColumn'] != null ) {
			println "Warning: ChangeSet '${changeSet.id}': addForeignKeyConstraint's referencesUniqueColumn parameter has been deprecated, and may be removed in a future release."
			println "Consider removing it, as Liquibase ignores it anyway."
		}

		def name = 'addForeignKeyConstraint'
		Map<String, SortedSet<Class<? extends Change>>> registry = ChangeFactory.getInstance().getRegistry()
		SortedSet<Class<? extends Change>> changes = registry.get(name)

		def change
		if (changes == null || changes.isEmpty()) {
			change = makeChangeFromMap(name, AddForeignKeyConstraintChange, params)
		} else {
			change = makeChangeFromMap(name, changes.getAt(0), params)
		}
		addChange(change)
	}

	void createProcedure(Map params = [:], Closure closure) {
		def change = makeChangeFromMap('createProcedure', CreateProcedureChange, params)
		change.procedureText = DelegateUtil.expandExpressions(closure.call(), databaseChangeLog)
		addChange(change)
	}

	void createProcedure(String storedProc) {
		def change = new CreateProcedureChange()
		change.procedureText = DelegateUtil.expandExpressions(storedProc, databaseChangeLog)
		change.setResourceAccessor(resourceAccessor)
		addChange(change)
	}

	/**
	 * This method only remains to let users know the correct name for this
	 * change.
	 */
	@Deprecated
	void createStoredProcedure(Map params = [:], Closure closure) {
		throw new ChangeLogParseException("Error: ChangeSet '${changeSet.id}': 'createStoredProcedure' changes have been removed. Use 'createProcedure' instead.")
	}

	@Deprecated
	void createStoredProcedure(String storedProc) {
		throw new ChangeLogParseException("Error: ChangeSet '${changeSet.id}': 'createStoredProcedure' changes have been removed. Use 'createProcedure' instead.")
	}

	void createView(Map params, Closure closure) {
		def change = makeChangeFromMap('createView', CreateViewChange, params)
		change.selectQuery = DelegateUtil.expandExpressions(closure.call(), databaseChangeLog)
		addChange(change)
	}

	void customChange(Map params, Closure closure = null) {
		def change = new CustomChangeWrapper()
		if ( closure ) {
			change.classLoader = closure.getClass().getClassLoader()
		} else {
			change.classLoader = this.class.classLoader
		}
		String className = DelegateUtil.expandExpressions(params['class'], databaseChangeLog)
		change.setClass(className)
		change.setResourceAccessor(resourceAccessor)

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
	 * A Groovy-specific extension that allows a closure to be provided,
	 * implementing the change. The closure is passed the instance of
	 * Database.
	 */
	void customChange(Closure closure) {
		//TODO Figure out how to implement closure-based custom changes
		// It's not easy, since the closure would probably need the Database object to be
		// interesting, and that's not available at parse time. Perhaps we could keep this closure
		// around somewhere to run later when the Database is alive.
	}

	void delete(Map params, Closure closure) {
		def change = makeColumnarChangeFromMap('delete', DeleteDataChange, ColumnConfig, params, closure)
		addChange(change)
	}

	void delete(Map params) {
		addMapBasedChange('delete', DeleteDataChange, params)
	}

	/**
	 * Process an "empty" changes.  It doesn't do anything, but it is allowed
	 * by the spec.
	 */
	void empty() {
		// To support empty changes (allowed by the spec)
	}

	void executeCommand(Map params) {
		addMapBasedChange('executeCommand', ExecuteShellCommandChange, params)
	}

	void executeCommand(Map params, Closure closure) {
		def change = makeChangeFromMap('executeCommand', ExecuteShellCommandChange, params)
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

	void loadData(Map params, Closure closure) {
		if ( params.file instanceof File ) {
			throw new ChangeLogParseException("Warning: ChangeSet '${changeSet.id}': using a File object for loadData's 'file' attribute is no longer supported.  Use the path to the file instead.")
		}

		def name = 'loadData'
		Map<String, SortedSet<Class<? extends Change>>> registry = ChangeFactory.getInstance().getRegistry()
		SortedSet<Class<? extends Change>> changes = registry.get(name)

		def change
		if (changes == null || changes.isEmpty()) {
			change = makeColumnarChangeFromMap(name, LoadDataChange, LoadDataColumnConfig, params, closure)
		} else {
			change = makeColumnarChangeFromMap(name, changes.getAt(0), getColumnConfigClass(changes.getAt(0)), params, closure)
		}

		addChange(change)
	}

	void loadUpdateData(Map params, Closure closure) {
		if ( params.file instanceof File ) {
			throw new ChangeLogParseException("Warning: ChangeSet '${changeSet.id}': using a File object for loadUpdateData's 'file' attribute is no longer supported.  Use the path to the file instead.")
		}

		def name = 'loadUpdateData'
		Map<String, SortedSet<Class<? extends Change>>> registry = ChangeFactory.getInstance().getRegistry()
		SortedSet<Class<? extends Change>> changes = registry.get(name)

		def change
		if (changes == null || changes.isEmpty()) {
			change = makeColumnarChangeFromMap(name, LoadUpdateDataChange, LoadDataColumnConfig, params, closure)
		} else {
			change = makeColumnarChangeFromMap(name, changes.getAt(0), getColumnConfigClass(changes.getAt(0)), params, closure)
		}

		addChange(change)
	}

	void output(Map params) {
		// Workaround for Issue #28:  Liquibase initializes the target to the
		// invalid value of an empty string instead of null, then checks for
		// null when deciding if it wants to use the default of STDERR.
		// workaround this by explicitly setting the default if no target was
		// given.
		if ( !params.containsKey('target') ) {
			params.target = 'STDERR'
		}
		addMapBasedChange('output', OutputChange, params)
	}

	void sql(Map params = [:], Closure closure) {
		def change = makeChangeFromMap('sql', RawSQLChange, params)
		def delegate = new CommentDelegate(changeSetId: changeSet.id,
				changeName: 'sql')
		closure.delegate = delegate
		closure.resolveStrategy = Closure.DELEGATE_FIRST
		// expand expressions because the comment delegate won't...
		change.sql = DelegateUtil.expandExpressions(closure.call(), databaseChangeLog)
		change.comment = (DelegateUtil.expandExpressions(delegate.comment, databaseChangeLog))
		addChange(change)
	}

	void sql(String sql) {
		def change = new RawSQLChange()
		change.sql = DelegateUtil.expandExpressions(sql, databaseChangeLog)
		change.setResourceAccessor(resourceAccessor)
		addChange(change)
	}

	void sqlFile(Map params) {
		// It doesn't make sense to have SQL in a sqlFile change, even though
		// liquibase allows it.
		if ( params.containsKey('sql') ) {
			throw new ChangeLogParseException("ChangeSet '${changeSet.id}': 'sql' is an invalid property for 'sqlFile' changes.")
		}
		def change = makeChangeFromMap('sqlFile', SQLFileChange, params)
		// Before we add the change, work around the Liquibase bug where sqlFile
		// change sets don't load the SQL until it is too late to calculate
		// checksums properly after a clearChecksum command.  See
		// https://liquibase.jira.com/browse/CORE-1293
		change.finishInitialization()

		addChange(change)
	}

	/**
	 * Parse a stop change.  This version of the method follows the XML by taking
	 * a 'message' parameter
	 * @param params the parameter map
	 */
	void stop(Map params) {
		addMapBasedChange('stop', StopChange, params)
	}

	/**
	 * Parse a stop change.  This version of the method is syntactic sugar that
	 * allows {@code stop 'some message'} in stead of the usual parameter based
	 * change.
	 * @param message the stop message.
	 */
	void stop(String message) {
		def change = new StopChange()
		change.message = DelegateUtil.expandExpressions(message, databaseChangeLog)
		change.setResourceAccessor(resourceAccessor)
		addChange(change)
	}

	/**
	 * Parse a tagDatabase change.  This version of the method follows the XML
	 * by taking a 'tag' parameter.
	 * @param params params the parameter map
	 */
	void tagDatabase(Map params) {
		addMapBasedChange('tagDatabase', TagDatabaseChange, params)
	}

	/**
	 * Parse a tagDatabase change.  This version of the method is syntactic sugar
	 * that allows {@code tagDatabase 'my-tag-name'} in stead of the usual
	 * parameter based change.
	 * @param tagName the name of the tag to create.
	 */
	void tagDatabase(String tagName) {
		def change = new TagDatabaseChange()
		change.tag = DelegateUtil.expandExpressions(tagName, databaseChangeLog)
		change.setResourceAccessor(resourceAccessor)
		addChange(change)
	}

	/**
	 * Groovy calls methodMissing when it can't find a matching method to call.
	 *
	 * We'll do a lookup on the liquibase registry to find a possible change for the name of the method. If we can find it, we'll use that.
	 *
	 * else
	 *
	 * We use it to tell the user which changeSet had the invalid element.
	 * @param name the name of the method Groovy wanted to call.
	 * @param args the original arguments to that method.
	 */
	void methodMissing(String name, args) {
		if(this.registry == null){
			this.registry = ChangeFactory.getInstance().getRegistry()
		}

		SortedSet<Class<? extends Change>> changes = this.registry.get(name)

		if (changes == null || changes.isEmpty()) {
			throw new ChangeLogParseException("ChangeSet '${changeSet.id}': '${name}' is not a valid element of a ChangeSet")
		}

		Map params = args != null && args.size() > 0 ? (Map) args[0] : null
		Closure closure = args != null && args.size() > 1 ? (Closure) args[1] : null

		def change
		Class<? extends Change> changeClass = changes.getAt(0)

		if (closure && ChangeWithColumns.isAssignableFrom(changeClass)) {
			change = makeColumnarChangeFromMap(name, changeClass, getColumnConfigClass(changeClass), params, closure)
		} else {
			change = makeChangeFromMap(name, changeClass, params)
		}

		addChange(change)
	}

	static Class<? extends ColumnConfig> getColumnConfigClass(def changeClass) {
		Class<? extends ColumnConfig> columnConfigType = null
		while (true) {
			if (changeClass.equals(Object.class)) {
				break
			}
			def changeWithColumnInterfaceType = changeClass.getGenericInterfaces().find {
				it instanceof ParameterizedType && it.getRawType().equals(ChangeWithColumns.class)
			}
			if (changeWithColumnInterfaceType) {
				columnConfigType = changeWithColumnInterfaceType.getActualTypeArguments().getAt(0)
			}

			changeClass = changeClass.getSuperclass()
		}

		columnConfigType
	}

	/**
	 * Create a Liquibase change for the types of changes that can have a nested
	 * closure of columns and where clauses.
	 * @param name the name of the change to make, used for improved error messages.
	 * @param changeClass the Liquibase class to create.
	 * @param columnConfigClass the class for the nested column configuration.
	 * @param closure the closure with column information
	 * @param params a map containing attributes of the new change
	 * @param paramNames a list of valid properties for the new change
	 * @return the newly created change
	 */
	private def makeColumnarChangeFromMap(String name, Class changeClass,
	                                      columnConfigClass, Map params,
	                                      Closure closure) {
		def change = makeChangeFromMap(name, changeClass, params)

		def columnDelegate = new ColumnDelegate(columnConfigClass: columnConfigClass,
						                        databaseChangeLog: databaseChangeLog,
						                        changeSetId: changeSet.id,
						                        changeName: name)
		closure.delegate = columnDelegate
		closure.resolveStrategy = Closure.DELEGATE_FIRST
		closure.call()

		// Try to add the columns to the change.  If we're dealing with something
		// like a "delete" change, we'll get an exception, which we'll rethrow as
		// a parse exception to tell the user that columns are not allowed in that
		// change.
		columnDelegate.columns.each { column ->
			try {
			  change.addColumn(column)
			} catch (MissingMethodException e) {
				throw new ChangeLogParseException("ChangeSet '${changeSet.id}': columns are not allowed in '${name}' changes.", e)
			}
		}

		// If we have a where clause, try to set it in the change.  We'll get an
		// exception if a where clause is not supported by the change.
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
	 * Create a new Liquibase change and set its properties from the given
	 * map of parameters.
	 * @param klass the type of change to create/
	 * @param sourceMap a map of parameter names and values for the new change
	 * @return the newly create change, with the appropriate properties set.
	 * @throws ChangeLogParseException if the source map contains any keys that
	 * are not in the list of valid paramNames.
	 */
	private def makeChangeFromMap(String name, Class klass, Map sourceMap) {
		def change = klass.newInstance()
		change.resourceAccessor = resourceAccessor

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
	 * Helper method used by changes that don't have closures, just attributes
	 * that get set from the parameter map.  This method will add the newly
	 * created change to the current change set.
	 * @param name the name of the change.  Used for improved error messages.
	 * @param klass the Liquibase class to make for the change.
	 * @param sourceMap the map of attributes to set on the Liquibase change.
	 * @param paramNames a list of valid attribute names.
	 */
	private def addMapBasedChange(String name, Class klass, Map sourceMap) {
		addChange(makeChangeFromMap(name, klass, sourceMap))
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
