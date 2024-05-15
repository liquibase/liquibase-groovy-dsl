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

import liquibase.ContextExpression
import liquibase.Labels
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.IncludeAllFilter
import liquibase.database.ObjectQuotingStrategy
import liquibase.exception.ChangeLogParseException
import liquibase.resource.DirectoryResourceAccessor

/**
 * This class is the delegate for the {@code databaseChangeLog} element.  It is the starting point
 * for parsing the Groovy DSL.
 *
 * @author Steven C. Saliman
 */
class DatabaseChangeLogDelegate {
	var DatabaseChangeLog databaseChangeLog
	def params
	def resourceAccessor
	def absoluteResourceAccessor = new DirectoryResourceAccessor(new File("/"))

	DatabaseChangeLogDelegate(databaseChangeLog) {
		this([:], databaseChangeLog)
	}


	DatabaseChangeLogDelegate(Map params, databaseChangeLog) {
		this.params = params
		this.databaseChangeLog = databaseChangeLog
		// It doesn't make sense to expand expressions, since we haven't loaded properties yet.
		params.each { key, value ->
			// The context attribute needs a little work.  The value needs to be converted into an
            // object, and the DatabaseChangelog's attribute depends on the version of Liquibase.
			if ( key.equals("context") || key.equals("contextFilter")) {
                value = new ContextExpression(value) {}
                // LB < 4.16 used "context", >= 4.16 uses "contextFilter"
				if ( databaseChangeLog.hasProperty('context') ) {
                    key = "context"
                } else {
                    key = "contextFilter"
                }
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
				'runWith'
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
        // Liquiase 4.16 deprecated "context" in favor of "contextFilter", but it still supports
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
                null,
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
		def unsupportedKeys = params.keySet() - ['file', 'relativeToChangelogFile', 'context', 'contextFilter', 'labels', 'ignore']
		if ( unsupportedKeys.size() > 0 ) {
			throw new ChangeLogParseException("DatabaseChangeLog:  '${unsupportedKeys.toArray()[0]}' is not a supported attribute of the 'include' element.")
		}

		def relativeToChangelogFile = DelegateUtil.parseTruth(params.relativeToChangelogFile, false)
		def absoluteFile = params.file.startsWith('/')

	   	def fileName = databaseChangeLog
			    .changeLogParameters
			    .expandExpressions(params.file, databaseChangeLog)
        def context = params.contextFilter? params.contextFilter : params.context
		def includeContexts = new ContextExpression(context)
		def labels = new Labels(params.labels)
		def ignore = DelegateUtil.parseTruth(params.ignore, false)
		if ( absoluteFile ) {
			databaseChangeLog.include(fileName, relativeToChangelogFile, true, absoluteResourceAccessor,
                    includeContexts, labels, ignore, DatabaseChangeLog.OnUnknownFileFormat.FAIL)
		} else {
			databaseChangeLog.include(fileName, relativeToChangelogFile, true, resourceAccessor,
                    includeContexts, labels, ignore, DatabaseChangeLog.OnUnknownFileFormat.FAIL)
		}
	}

	/**
	 * Process the includeAll element to include all groovy files in a directory.
	 * @param params
	 */
	void includeAll(Map params = [:]) {
		// validate parameters.
		def unsupportedKeys = params.keySet() - ['path', 'relativeToChangelogFile', 'errorIfMissingOrEmpty', 'resourceComparator', 'filter', 'context', 'contextFilter', 'labels', 'ignore']
		if (unsupportedKeys.size() > 0) {
			throw new ChangeLogParseException("DatabaseChangeLog:  '${unsupportedKeys.toArray()[0]}' is not a supported attribute of the 'includeAll' element.")
		}

		def relativeToChangelogFile = DelegateUtil.parseTruth(params.relativeToChangelogFile, false)
		def errorIfMissingOrEmpty = DelegateUtil.parseTruth(params.errorIfMissingOrEmpty, true)
        def context = params.contextFilter? params.contextFilter : params.context
        def includeContexts = new ContextExpression(context)
		def ignore = DelegateUtil.parseTruth(params.ignore, false)
		def labels = new Labels(params.labels)

		// Set up the resource comparator.  If one is not given, we'll use the
		// standard one.
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

		// Set up the filter.  We always want to filter out non-groovy files,
		// but the user may want to supply one of their own in addition to the
		// standard groovy filter.
		IncludeAllFilter resourceFilter = null
		if ( params.filter ) {
			def filterName = databaseChangeLog
					.changeLogParameters
					.expandExpressions(params.filter, databaseChangeLog)
			try {
				resourceFilter = (IncludeAllFilter) Class.forName(filterName).newInstance()
			} catch (InstantiationException|IllegalAccessException|ClassNotFoundException|ClassCastException e) {
				throw new ChangeLogParseException("DatabaseChangeLog: '${filterName}' is not a valid resource filter.  Does the class exist, and does it implement IncludeAllFilter?")
			}
		}

		def groovyFilter = new GroovyOnlyResourceFilter(userFilter: resourceFilter)

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

		loadAll(pathName, relativeToChangelogFile, groovyFilter,
				errorIfMissingOrEmpty, resourceComparator, resourceAccessor,
				includeContexts, labels, ignore)
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
		def unsupportedKeys = params.keySet() - ['name', 'value', 'context', 'contextFilter', 'labels', 'dbms', 'global', 'file']
		if (unsupportedKeys.size() > 0) {
			throw new ChangeLogParseException("DababaseChangeLog: ${unsupportedKeys.toArray()[0]} is not a supported property attribute")
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
		// The default for global was true prior to Liquibase 3.4
		def global = DelegateUtil.parseTruth(params.global, true)

		def changeLogParameters = databaseChangeLog.changeLogParameters

		if (!params['file']) {
			changeLogParameters.set(params['name'], params['value'], context as ContextExpression, labels as Labels, dbms, global, databaseChangeLog)
		} else {
			String propFile = params['file']
			def props = new Properties()
			def stream = resourceAccessor.openStream(null, propFile)
			if (!stream) {
				throw new ChangeLogParseException("Unable to load file with properties: ${params['file']}")
			}
			props.load(stream)
			props.each { k, v ->
				changeLogParameters.set(k, v, context as ContextExpression, labels as Labels, dbms, global, databaseChangeLog)
			}
		}
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
	 * Helper class to load all the changesets in an included directory.  This
	 * method is basically a copy of the Liquibase 3.6.1
	 * {@code DatabaseChangeLog.includeAll} method, except that it fixes the
	 * paths of included resources before calling the liquibase
	 * {@code DatabaseChangeLog.include} method.  This is needed to work around
	 * a bug in Liquibase where filenames are always converted to absolute paths,
	 * which is undesirable.
	 * @param pathName the name of the directory whose resources we're including
	 * @param isRelativeToChangelogFile whether or not the pathName is
	 *        relative to the original changelog
	 * @param resourceFilter a filter through which to run each file name.  This
	 *        filter can decide whether or not a given file should be processed.
	 * @param errorIfMissingOrEmpty whether or not we should stop parsing if
	 *        the given directory is empty
	 * @param resourceComparator a comparator to use for sorting filenames.
	 * @param resourceAccessor the accessor we should use to find included
	 *        resources
	 * @param includeContexts the context(s) to associate with all the changes
	 *        in the included changelog
	 */
	private def loadAll(pathName, isRelativeToChangelogFile, resourceFilter,
	                    errorIfMissingOrEmpty, resourceComparator,
	                    resourceAccessor, includeContexts, labels, ignore) {
		try {
			pathName = pathName.replace('\\', '/')

			if ( !(pathName.endsWith("/")) ) {
				pathName = pathName + '/'
			}

			String relativeTo = null
			// If whe're including directories relative to the changelog, we'll
			// need to tell the ResourceAccessor to use the changelog's path
			// instead of the working directory's path.
			if ( isRelativeToChangelogFile ) {
				relativeTo = databaseChangeLog.getPhysicalFilePath()
			}

			Set<String> unsortedResources = null;
			try {
				unsortedResources = resourceAccessor.list(relativeTo, pathName, true, true, false)
			} catch (FileNotFoundException e) {
				if ( errorIfMissingOrEmpty ) {
					throw e;
				}
			}
			SortedSet<String> resources = new TreeSet<>(resourceComparator)
			if ( unsortedResources != null ) {
				for ( String resourcePath : unsortedResources ) {
					if ( (resourceFilter == null) || resourceFilter.include(resourcePath) ) {
						resources.add(resourcePath)
					}
				}
			}

			if ( resources.isEmpty() && errorIfMissingOrEmpty ) {
				throw new ChangeLogParseException(
						"DatabaseChangelog: Could not find directory or directory was empty for includeAll '${pathName}'")
			}

			for ( String resourceName : resources ) {
				// Liquibase's resource accessor will return files relative to
				// the working directory, even if we told it to look relative
				// to the working directory, so force the "relativeToChangelog"
				// flag to false when we process the resource.
				databaseChangeLog.include(resourceName, false, true, resourceAccessor,
						includeContexts, labels, ignore, DatabaseChangeLog.OnUnknownFileFormat.SKIP)
			}
		} catch (Exception e) {
			throw new ChangeLogParseException(e)
		}
	}

	private Comparator<String> getStandardChangeLogComparator() {
		return new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return o1. compareTo(o2);
			}
		};
	}
}
