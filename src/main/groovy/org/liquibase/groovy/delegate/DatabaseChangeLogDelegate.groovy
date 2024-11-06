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

import liquibase.ContextExpression
import liquibase.Labels
import liquibase.change.visitor.ChangeVisitor
import liquibase.change.visitor.ChangeVisitorFactory
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.IncludeAllFilter
import liquibase.database.DatabaseList
import liquibase.database.ObjectQuotingStrategy
import liquibase.exception.ChangeLogParseException

/**
 * This class is the delegate for the {@code databaseChangeLog} element.  It is the starting point
 * for parsing the Groovy DSL.
 *
 * @author Steven C. Saliman
 */
class DatabaseChangeLogDelegate {
	def databaseChangeLog
	def params
	def resourceAccessor

	DatabaseChangeLogDelegate(databaseChangeLog) {
		this([:], databaseChangeLog)
	}

	DatabaseChangeLogDelegate(Map params, databaseChangeLog) {
		this.params = params
		this.databaseChangeLog = databaseChangeLog
		// It doesn't make sense to expand expressions, since we haven't loaded properties yet.
		params.each { key, value ->
			// The contextFilter attribute needs a little work.  The value needs to be converted
            // into an object, and for now, we'll support the old "context" attribute.
			if ( key.equals("context") || key.equals("contextFilter")) {
                value = new ContextExpression(value) {}
                // LB >= 4.16 uses "contextFilter", so convert the pre-4.16 key.
                key = "contextFilter"
			}
            databaseChangeLog[key] = value
		}
	}

	/**
	 * Parse a changeSet and add it to the change log.
	 * @param params the attributes of the change set.
	 * @param closure the closure containing, among other things, all the refactoring changes the
     *        change set should make.
	 */
	void changeSet(Map params, closure) {
		// Most of the time, we just pass any parameters through to a newly created Liquibase
        // object, but we need to do things a little differently for a ChangeSet because the
        // Liquibase object does not have setters for its properties. We'll need to figure it all
        // out for the constructor.  We want to warn people if they try to pass in something that is
        // not supported because we don't want to silently ignore things, so first get a list of
        // unsupported keys.
		if (params.containsKey('alwaysRun')) {
			throw new ChangeLogParseException("Error: ChangeSet '${params.id}': the alwaysRun attribute of a changeSet has been removed.  Please use 'runAlways' instead.")
		}

		def unsupportedKeys = params.keySet() - [
				'id',
				'author',
				'dbms',
				'runAlways',
				'runOnChange',
				'context',
				'contextFilter',
				'labels',
				'runInTransaction',
				'failOnError',
				'onValidationFail',
				'objectQuotingStrategy',
				'logicalFilePath',
				'filePath',
				'created',
				'runOrder',
				'ignore',
				'runWith',
                'runWithSpoolFile'
		]
		if (unsupportedKeys.size() > 0) {
			throw new ChangeLogParseException("ChangeSet '${params.id}': ${unsupportedKeys.toArray()[0]} is not a supported ChangeSet attribute")
		}

		def objectQuotingStrategy = null
		if ( params.containsKey("objectQuotingStrategy") ) {
			try {
				objectQuotingStrategy = ObjectQuotingStrategy.valueOf(params.objectQuotingStrategy)
			} catch ( IllegalArgumentException e) {
				throw new ChangeLogParseException("ChangeSet '${params.id}': ${params.objectQuotingStrategy} is not a supported ChangeSet ObjectQuotingStrategy")
			}
		}

		def filePath = databaseChangeLog.filePath // default
		if ( params.containsKey('filePath') ) {
			filePath = params.filePath
		}
		if ( params.containsKey('logicalFilePath') ) {
			filePath = params.logicalFilePath
		}
        // Liquibase 4.16 deprecated "context" in favor of "contextFilter", but it still supports
        // both.  A null here is fine.
        def contextFilter = params.contextFilter? params.contextFilter : params.context

        def changeSet = new ChangeSet(
                DelegateUtil.expandExpressions(params.id, databaseChangeLog),
                DelegateUtil.expandExpressions(params.author, databaseChangeLog),
                DelegateUtil.parseTruth(params.runAlways, false),
                DelegateUtil.parseTruth(params.runOnChange, false),
                filePath,
                DelegateUtil.expandExpressions(contextFilter, databaseChangeLog),
                DelegateUtil.expandExpressions(params.dbms, databaseChangeLog),
                DelegateUtil.expandExpressions(params.runWith, databaseChangeLog),
                DelegateUtil.expandExpressions(params.runWithSpoolFile, databaseChangeLog),
                DelegateUtil.parseTruth(params.runInTransaction, true),
                objectQuotingStrategy,
                databaseChangeLog)

        changeSet.changeLogParameters = databaseChangeLog.changeLogParameters

		if ( params.containsKey('failOnError') ) {
			changeSet.failOnError = DelegateUtil.parseTruth(params.failOnError, false)
		}

		if ( params.onValidationFail ) {
			changeSet.onValidationFail = ChangeSet.ValidationFailOption.valueOf(params.onValidationFail)
		}

		if ( params.labels ) {
			changeSet.labels = new Labels(params.labels as String)
		}

		if ( params.created ) {
			changeSet.created = params.created
		}

		if ( params.runOrder ) {
			changeSet.runOrder = params.runOrder
		}

		if ( params.ignore ) {
			changeSet.ignore = DelegateUtil.parseTruth(params.ignore, false)
		}

		def delegate = new ChangeSetDelegate(changeSet: changeSet,
				databaseChangeLog: databaseChangeLog)
		closure.delegate = delegate
		closure.resolveStrategy = Closure.DELEGATE_FIRST
		closure.call()

		databaseChangeLog.addChangeSet(changeSet)
	}

	/**
	 * Process the include element to include a file with change sets.
	 * @param params
	 */
	void include(Map params = [:]) {
		// validate parameters.\
		def unsupportedKeys = params.keySet() - [
                'file',
                'relativeToChangelogFile',
                'errorIfMissing',
                'context',
                'contextFilter',
                'labels',
                'ignore',
                'logicalFilePath']
		if ( unsupportedKeys.size() > 0 ) {
			throw new ChangeLogParseException("DatabaseChangeLog:  '${unsupportedKeys.toArray()[0]}' is not a supported attribute of the 'include' element.")
		}

		def relativeToChangelogFile = DelegateUtil.parseTruth(params.relativeToChangelogFile, false)
		def errorIfMissing = DelegateUtil.parseTruth(params.errorIfMissing, false)

	   	def fileName = databaseChangeLog
			    .changeLogParameters
			    .expandExpressions(params.file, databaseChangeLog)
        def context = params.contextFilter? params.contextFilter : params.context
		def includeContexts = new ContextExpression(context)
		def labels = new Labels(params.labels)
		def ignore = DelegateUtil.parseTruth(params.ignore, false)
        def logicalFilePath = params.containsKey("logicalFilePath")?
                 databaseChangeLog
                        .changeLogParameters
                        .expandExpressions(params.logicalFilePath , databaseChangeLog): null

        // The Resource Accessor we need to use depends on whether we are including a relative file
        // or an absolute file, and which version of Liquibase we're using.  For now, we'll assume
        // that we have a relative include, which uses the resource accessor we've been given.
        databaseChangeLog.include(fileName, relativeToChangelogFile, errorIfMissing,
                resourceAccessor, includeContexts, labels, ignore, logicalFilePath, DatabaseChangeLog.OnUnknownFileFormat.FAIL)
	}

	/**
	 * Process the includeAll element to include all files in a directory.
	 * @param params
	 */
	void includeAll(Map params = [:]) {
		// validate parameters.
		def unsupportedKeys = params.keySet() - [
                'path',
                'relativeToChangelogFile',
                'errorIfMissingOrEmpty',
                'resourceComparator',
                'filter',
                'context',
                'contextFilter',
                'labels',
                'ignore',
                'logicalFilePath',
                'minDepth',
                'maxDepth',
                'endsWithFilter'
        ]
		if (unsupportedKeys.size() > 0) {
			throw new ChangeLogParseException("DatabaseChangeLog:  '${unsupportedKeys.toArray()[0]}' is not a supported attribute of the 'includeAll' element.")
		}

        def includeAllParams = createIncludeAllParams(params)
        databaseChangeLog.includeAll(includeAllParams.path,
                includeAllParams.relativeToChangelogFile,
                includeAllParams.filter,
                includeAllParams.errorIfMissingOrEmpty,
                includeAllParams.resourceComparator,
                resourceAccessor,
                includeAllParams.includeContexts,
                includeAllParams.labels,
                includeAllParams.ignore,
                includeAllParams.logicalFilePath,
                includeAllParams.minDepth,
                includeAllParams.maxDepth,
                includeAllParams.endsWithFilter,
                null)
    }

    /**
     * Process the Groovy DSL's special includeAllSql element that creates a changeSet with a
     * sqlFile change for each file found in the specified path.
     * @param params the params that affect how files are found, and how the changeSets are created
     *         from each one.
     */
    void includeAllSql(Map params = [:]) {
        // Params we use to find the SQL files.
        def includeAllKeys = [
                'path',
                'relativeToChangelogFile',
                'errorIfMissingOrEmpty',
                'resourceComparator',
                'filter',
                'context',
                'contextFilter',
                'labels',
                'ignore',
                'minDepth',
                'maxDepth',
                'endsWithFilter',
        ]

        // Params we use to create the change set
        def changeSetKeys = [
                'author',
                'dbms',
                'runAlways',
                'runOnChange',
                'context',
                'contextFilter',
                'labels',
                'failOnError',
                'onValidationFail',
                'objectQuotingStrategy',
                'created',
                'ignore',
                'runWith',
                'runWithSpoolFile',
        ]

        // Params we use to create the sqlFile change
        def sqlFileKeys = [
                'dbms',
                'encoding',
                'endDelimiter',
                'relativeToChangeLogFile',
                'splitStatements',
                'stripComments',
        ]


        def unsupportedKeys = params.keySet() - includeAllKeys - changeSetKeys - sqlFileKeys - [
                'idPrefix',
                'idSuffix',
                'idKeepsExtension',
        ]

        if ( unsupportedKeys.size() > 0 ) {
            throw new ChangeLogParseException("DatabaseChangeLog:  '${unsupportedKeys.toArray()[0]}' is not a supported attribute of the 'includeAll' element.")
        }

        // Create the parameters to use when searching for files.
        def includeAllParams = createIncludeAllParams(params.subMap(includeAllKeys))

        // Create the parameters to use when creating a change set, creating a default for the
        // author and making sure the value for runOnChange is true.
        def changeSetParams = params.subMap(changeSetKeys)
        if ( !changeSetParams.author ) changeSetParams.author = 'various (generated by includeAllSql)'
        changeSetParams.runAlways = DelegateUtil.parseTruth(params.runAlways, false)
        changeSetParams.runOnChange = DelegateUtil.parseTruth(params.runOnChange, true)
        changeSetParams.failOnError = DelegateUtil.parseTruth(params.failOnError, false)
        // Create the parameters we'll use for the sqlFile change.  Note that even when we use
        // relativeToChangelogFile to locate the included directory, Liquibase's resource Accessor
        // returns paths that are relative to the working directory.
        def sqlFileParams = params.subMap(sqlFileKeys)
        sqlFileParams.relativeToChangelogFile = false

         // find our files.
        def sqlFiles = databaseChangeLog.findResources(includeAllParams.path,
                includeAllParams.relativeToChangelogFile,
                includeAllParams.filter,
                includeAllParams.errorIfMissingOrEmpty,
                includeAllParams.resourceComparator,
                resourceAccessor,
                includeAllParams.minDepth,
                includeAllParams.maxDepth,
                includeAllParams.endsWithFilter)
        if ( !sqlFiles || sqlFiles.isEmpty() ) {
            return // findResources handles errorIfMissingOrEmpty
        }

        def idKeepsExtension = DelegateUtil.parseTruth(params.idKeepsExtension, false)

        // if we have files, sort them and make a change set for each one.
        sqlFiles.each { fileName ->
            // We want the id to be based off the filename, minus any directories, and with the
            // extension stripped off, unless the user wanted to keep extensions.
            def baseName = fileName.path.tokenize('/').last().tokenize('\\').last()
            if ( !idKeepsExtension && baseName.contains('.') ) {
                baseName.take(baseName.lastIndexOf('.'))
            }
            // Make the id from the base fileName and the given prefix and suffix.
            changeSetParams.id = "${params.idPrefix ?: ''}${baseName}${params.idSuffix ?: ''}"
            sqlFileParams.path = fileName
            changeSet(changeSetParams) {
                sqlFile(sqlFileParams)
            }
        }
    }

	/**
	 * Process nested preConditions elements in a database change log.
	 * @param params the attributes of the preConditions
	 * @param closure the closure containing nested elements of a precondition.
	 */
	void preConditions(Map params = [:], Closure closure) {
		databaseChangeLog.preconditions = PreconditionDelegate.buildPreconditionContainer(databaseChangeLog, '<none>', params, closure)
	}

	/**
	 * Process nested property elements in a database change log.
	 * @param params the attributes of the property.
	 */
	void property(Map params = [:]) {
		// Start by validating input
		def unsupportedKeys = params.keySet() - [
                'name',
                'value',
                'context',
                'contextFilter',
                'labels',
                'dbms',
                'global',
                'file',
                'relativeToChangelogFile',
                'errorIfMissing',
        ]
		if (unsupportedKeys.size() > 0) {
			throw new ChangeLogParseException("DatabaseChangeLog: ${unsupportedKeys.toArray()[0]} is not a supported property attribute")
		}

		ContextExpression context = null
		if (params['context'] != null) {
			context = new ContextExpression(params['context'])
		}
        // Done second so it takes precedence over "context"
		if (params['contextFilter'] != null) {
			context = new ContextExpression(params['contextFilter'])
		}
		Labels labels = null
		if (params['labels'] != null) {
			labels = new Labels(params['labels'])
		}
		def dbms = params['dbms'] ?: null
		// The default for global was true prior to Liquibase 3.4, and the other parsers still use
        // true as the default.
		def global = DelegateUtil.parseTruth(params.global, true)

		def changeLogParameters = databaseChangeLog.changeLogParameters

		if (!params['file']) {
			changeLogParameters.set(params['name'], params['value'], context as ContextExpression, labels as Labels, dbms, global, databaseChangeLog)
		} else {
			String propFile = params['file']
            def relativeTo = null // Default to a path relative to the working directory
            if ( DelegateUtil.parseTruth(params['relativeToChangelogFile'], false) ) {
                relativeTo = databaseChangeLog.physicalFilePath
            }
            def errorIfMissing = DelegateUtil.parseTruth(params['errorIfMissing'], true)
			def props = new Properties()

			def stream = resourceAccessor.openStream(relativeTo, propFile)
			if ( stream ) {
                props.load(stream)
                props.each { k, v ->
                    changeLogParameters.set(k, v, context as ContextExpression, labels as Labels, dbms, global, databaseChangeLog)
                }
            } else if ( errorIfMissing ) {
                throw new ChangeLogParseException("Unable to load file with properties: ${params['file']}")
            }

		}
	}

    /**
     * Process nested removeChangeSetProperty elements in a changelog.
     * @param params the attributes of the removeChangeSetProperty change.
     */
    def removeChangeSetProperty(Map params = [:]) {
        // Start by validating input
        def unsupportedKeys = params.keySet() - [
                'change',
                'dbms',
                'remove'
        ]
        if (unsupportedKeys.size() > 0) {
            throw new ChangeLogParseException("DatabaseChangeLog: ${unsupportedKeys.toArray()[0]} is not a supported property attribute")
        }

        if ( !params.dbms || !params.remove ) {
            throw new ChangeLogParseException("DatabaseChangeLog: missing value for the 'dbms' or 'remove' parameter")
        }

        def currentDb = databaseChangeLog.changeLogParameters.database
        if ( !DatabaseList.definitionMatches(params.dbms, currentDb, false) ) {
            // Log it?
            return
        }

        ChangeVisitor changeVisitor = ChangeVisitorFactory.getInstance().create(params.change)
        if ( !changeVisitor ) {
            throw new ChangeLogParseException("DatabaseChangeLog: ${params.change} is not a valid change type")
        }

        changeVisitor.dbms = params.dbms.split(',')
        changeVisitor.remove = params.remove
        databaseChangeLog.changeVisitors.add(changeVisitor)
    }

	def propertyMissing(String name) {
		def changeLogParameters = databaseChangeLog.changeLogParameters
		if (changeLogParameters.hasValue(name, databaseChangeLog)) {
			return changeLogParameters.getValue(name, databaseChangeLog)
		} else {
			throw new MissingPropertyException(name, this.class)
		}
	}

	/**
	 * Groovy calls methodMissing when it can't find a matching method to call.
	 * We use it to tell the user which changeSet had the invalid element.
	 * @param name the name of the method Groovy wanted to call.
	 * @param args the original arguments to that method.
	 */
	def methodMissing(String name, args) {
		throw new ChangeLogParseException("DatabaseChangeLog: '${name}' is not a valid element of a DatabaseChangeLog")
	}

    /**
     * Helper method that "fixes" incoming parameters to be used with includeAll and includeAllSql
     * elements.  It makes sure we have sensible defaults for all the required items.
     * @param params the incoming parameters to "fix"
     * @return a copy of the parameters with various items replaced by objects we can use elsewhere.
     */
    private createIncludeAllParams(Map params) {
        def includeAllParams = params.collectEntries(Closure.IDENTITY)

        // If the incoming params contain certain keys, copy them to the final params, if not, use
        // a default.  Groovy's way of getting a value with myMap.someKey, combined with the elvis
        // operator works well, and is concise, but there is a hidden "gotcha" we need to watch out
        // for...  The number 0 is "falsy" in Groovy, which means that If the value of a parameter
        // is 0 (as maxDepth could be), then the elvis operator will return false, and we'll get the
        // default value instead of the given value of 0.  This means that if a param could be 0, we
        // we need to use containsKey instead.
        includeAllParams.relativeToChangelogFile = DelegateUtil.parseTruth(params.relativeToChangelogFile, false)
        includeAllParams.errorIfMissingOrEmpty = DelegateUtil.parseTruth(params.errorIfMissingOrEmpty, true)
        def context = params.contextFilter? params.contextFilter : params.context
        includeAllParams.includeContexts = new ContextExpression(context)
        includeAllParams.ignore = DelegateUtil.parseTruth(params.ignore, false)
        includeAllParams.labels = new Labels(params.labels)
        includeAllParams.logicalFilePath = params.containsKey("logicalFilePath")?
                databaseChangeLog
                        .changeLogParameters
                        .expandExpressions(params.logicalFilePath , databaseChangeLog): null
        includeAllParams.minDepth = params.containsKey("minDepth")? params.minDepth : 0
        includeAllParams.maxDepth = params.containsKey("maxDepth")? params.maxDepth : Integer.MAX_VALUE // recurse by default
        includeAllParams.endsWithFilter = params.endsWithFilter? params.endsWithFilter: "" // LB doesn't like null

        // Set up the resource comparator.  If one is not given, we'll use the standard one.
        Comparator<String> resourceComparator = getStandardChangeLogComparator()
        if ( params.resourceComparator ) {
            def comparatorName = databaseChangeLog
                    .changeLogParameters
                    .expandExpressions(params.resourceComparator, databaseChangeLog)
            try {
                resourceComparator = (Comparator<String>) Class.forName(comparatorName).newInstance()
            } catch (InstantiationException|IllegalAccessException|ClassNotFoundException|ClassCastException e) {
                // Standard Liquibase would eat this and just use the standard,
                // but I really don't like ignoring declared intentions.  If
                // we cannot do what we were asked, we should stop and make the
                // user fix the issue.
                throw new ChangeLogParseException("DatabaseChangeLog: '${comparatorName}' is not a valid resource comparator.  Does the class exist, and does it implement Comparator?")
            }
        }
        includeAllParams.resourceComparator = resourceComparator

        // Initialize the filter, if we have one.
        IncludeAllFilter filter = null
        if ( params.filter ) {
            def filterName = databaseChangeLog
                    .changeLogParameters
                    .expandExpressions(params.filter, databaseChangeLog)
            try {
                filter = (IncludeAllFilter) Class.forName(filterName).newInstance()
            } catch (InstantiationException|IllegalAccessException|ClassNotFoundException|ClassCastException e) {
                throw new ChangeLogParseException("DatabaseChangeLog: '${filterName}' is not a valid resource filter.  Does the class exist, and does it implement IncludeAllFilter?")
            }
        }
        includeAllParams.filter = filter

        def pathName = params.path
        if ( pathName == null ) {
            throw new ChangeLogParseException("DatabaseChangeLog: No path attribute for includeAll")
        }

        pathName = databaseChangeLog
                .changeLogParameters
                .expandExpressions(params.path, databaseChangeLog)

        // If there is still a '$' in the path after expanding expressions, it
        // means we've got an invalid property.  Stop here.
        if ( pathName.contains('$') ) {
            throw new ChangeLogParseException("DatabaseChangeLog:  '${pathName}' contains an invalid property in an 'includeAll' element.")
        }
        includeAllParams.path = pathName

        return includeAllParams
    }

    /**
     * @return a default Comparator that sorts by path, which is the default in Liquibase.
     */
	private Comparator<String> getStandardChangeLogComparator() {
        // Liquibase won't let us send a null comparator, but doesn't expose the default to us.  So
        // we'll just return what Liquibase uses.  It might be worth DatabaseChangeLog from time
        // to time to make sure they don't change this out from under us.
        return Comparator.comparing(o -> o.replace("WEB-INF/classes/", ""))
	}
}
