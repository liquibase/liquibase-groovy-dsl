/*
 * Copyright 2011-2017 Tim Berglund and Steven C. Saliman
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

import liquibase.ContextExpression
import liquibase.Labels
import liquibase.changelog.ChangeSet
import liquibase.changelog.IncludeAllFilter
import liquibase.database.ObjectQuotingStrategy
import liquibase.exception.ChangeLogParseException
import liquibase.parser.ChangeLogParserFactory

/**
 * This class is the delegate for the {@code databaseChangeLog} element.  It
 * is the starting point for parsing the Groovy DSL.
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
		// It doesn't make sense to expand expressions, since we haven't loaded
		// properties yet.
		params.each { key, value ->
			databaseChangeLog[key] = value
		}
	}

	/**
	 * Parse a changeSet and add it to the change log.
	 * @param params the attributes of the change set.
	 * @param closure the closure containing, among other things, all the
	 * refactoring changes the change set should make.
	 */
	void changeSet(Map params, closure) {
		// Most of the time, we just pass any parameters through to a newly created
		// Liquibase object, but we need to do things a little differently for a
		// ChangeSet because the Liquibase object does not have setters for its
		// properties. We'll need to figure it all out for the constructor.
		// We want to warn people if they try to pass in something that is not
		// supported because we don't want to silently ignore things, so first get
		// a list of unsupported keys.
		if (params.containsKey('alwaysRun')) {
			throw new ChangeLogParseException("Error: ChangeSet '${params.id}': the alwaysRun attribute of a changeSet has been removed.  Please use 'runAlways' instead.")
		}

		def unsupportedKeys = params.keySet() - ['id', 'author', 'dbms', 'runAlways', 'runOnChange', 'context', 'labels', 'runInTransaction', 'failOnError', 'onValidationFail', 'objectQuotingStrategy']
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

		if( objectQuotingStrategy == null ){
			objectQuotingStrategy= databaseChangeLog.objectQuotingStrategy
		}

		if( objectQuotingStrategy == null ){
			objectQuotingStrategy= ObjectQuotingStrategy.LEGACY
		}

		def changeSet = new ChangeSet(
				DelegateUtil.expandExpressions(params.id, databaseChangeLog),
				DelegateUtil.expandExpressions(params.author, databaseChangeLog),
				DelegateUtil.parseTruth(params.runAlways, false),
				DelegateUtil.parseTruth(params.runOnChange, false),
				databaseChangeLog.filePath,
				DelegateUtil.expandExpressions(params.context, databaseChangeLog),
				DelegateUtil.expandExpressions(params.dbms, databaseChangeLog),
				DelegateUtil.parseTruth(params.runInTransaction, true),
				objectQuotingStrategy,
				databaseChangeLog)

		if (params.containsKey('failOnError')) {
			changeSet.failOnError = params.failOnError?.toBoolean()
		}

		if (params.onValidationFail) {
			changeSet.onValidationFail = ChangeSet.ValidationFailOption.valueOf(params.onValidationFail)
		}

		if (params.labels) {
			changeSet.labels = new Labels(params.labels as String)
		}

		def delegate = new ChangeSetDelegate(changeSet: changeSet,
				databaseChangeLog: databaseChangeLog,
				resourceAccessor: resourceAccessor)
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
		if (params.containsKey('path')) {
			throw new ChangeLogParseException("Error: the 'include' element no longer supports the 'path' attribute.  Please use the 'includeAll' element instead.")
		}

		// validate parameters.
		def unsupportedKeys = params.keySet() - ['file', 'relativeToChangelogFile']
		if (unsupportedKeys.size() > 0) {
			throw new ChangeLogParseException("DatabaseChangeLog:  '${unsupportedKeys.toArray()[0]}' is not a supported attribute of the 'include' element.")
		}

		def physicalChangeLogLocation = databaseChangeLog.physicalFilePath.replace(System.getProperty("user.dir").toString() + "/", "")

		def relativeToChangelogFile = DelegateUtil.parseTruth(params.relativeToChangelogFile, false)

		if (relativeToChangelogFile && (physicalChangeLogLocation.contains("/") || physicalChangeLogLocation.contains("\\\\"))) {
			params.file = physicalChangeLogLocation.replaceFirst("/[^/]*\$", "") + "/" + params.file
		}

	   	def fileName = databaseChangeLog
			    .changeLogParameters
			    .expandExpressions(params.file, databaseChangeLog)
		includeChangeLog(fileName)
	}

	/**
	 * Process the includeAll element to include all groovy files in a directory.
	 * @param params
	 */
	void includeAll(Map params = [:]) {
		// validate parameters.
		def unsupportedKeys = params.keySet() - ['path', 'relativeToChangelogFile', 'errorIfMissingOrEmpty', 'resourceFilter']
		if (unsupportedKeys.size() > 0) {
			throw new ChangeLogParseException("DatabaseChangeLog:  '${unsupportedKeys.toArray()[0]}' is not a supported attribute of the 'includeAll' element.")
		}

		def relativeToChangelogFile = DelegateUtil.parseTruth(params.relativeToChangelogFile, false)
		def errorIfMissingOrEmpty = DelegateUtil.parseTruth(params.errorIfMissingOrEmpty, true)

		IncludeAllFilter resourceFilter = null
		if ( params.resourceFilter ) {
			def filterName = databaseChangeLog
					.changeLogParameters
					.expandExpressions(params.resourceFilter, databaseChangeLog)
			resourceFilter = (IncludeAllFilter) Class.forName(filterName).newInstance();
		}

		def pathName = databaseChangeLog
				.changeLogParameters
				.expandExpressions(params.path, databaseChangeLog)
		loadIncludedChangeSets(pathName, relativeToChangelogFile, resourceFilter,
				errorIfMissingOrEmpty,	getStandardChangeLogComparator());
	}

	/**
	 * Helper method to load all the groovy changesets in a directory.
	 * @param dirName the name of the directory whose change sets we want.
	 * @param isRelativeToChangelogFile whether or not the dirName is relative
	 *        to the original change log.
	 * @param resourceFilter a filter through which to run each file name.  This
	 *        filter can decide whether or not a given file should be processed.
	 * @param errorIfMissingOrEmpty whether or not we should stop parsing if
	 *        the given directory is empty.
	 * @param resourceComparator a comparator to use for sorting filenames.
	 */
	private def loadIncludedChangeSets(dirName, isRelativeToChangelogFile, resourceFilter,
	                                   errorIfMissingOrEmpty,
	                                   resourceComparator) {
		try {
			dirName = dirName.replace('\\', '/');

			if (!(dirName.endsWith("/"))) {
				dirName = dirName + '/';
			}

			String relativeTo = null;
			if ( isRelativeToChangelogFile ) {
				def parent = new File(databaseChangeLog.physicalFilePath).parent
				if ( parent != null ) {
					relativeTo = parent + '/' + dirName
				}
			}

			Set<String> unsortedResources = null;
			try {
				unsortedResources = resourceAccessor.list(relativeTo, dirName, true, false, true);
			} catch (FileNotFoundException e) {
				if (errorIfMissingOrEmpty){
					throw new ChangeLogParseException("DatabaseChangeLog: includeAll path '${dirName} does not exist, or has no files that match the filter.")
				}
			}
			def resources = new TreeSet<String>(resourceComparator);
			if ( unsortedResources != null ) {
				unsortedResources.each { resource ->
					if (resourceFilter == null || resourceFilter.include(resource)) {
						resources.add(resource);
					}
				}
			}

			if (resources.size() == 0 && errorIfMissingOrEmpty) {
				throw new ChangeLogParseException("DatabaseChangeLog: includeAll path '${dirName} does not exist, or has no files that match the filter.")
			}

			// Filter the list to just the groovy files and include each one.
			resources.findAll({it.endsWith('.groovy')}).each {
				includeChangeLog(it);
			}
		} catch (Exception e) {
			throw new ChangeLogParseException("DatabaseChangeLog: error processing includeAll path '${dirName}.", e);
		}
	}

	/**
	 * Helper method to do the actual work of including a changelog file.
	 * @param filename the file to include.
	 */
	private def includeChangeLog(filename) {
		def parser = ChangeLogParserFactory.getInstance().getParser(filename, resourceAccessor)
		def includedChangeLog = parser.parse(filename, databaseChangeLog.changeLogParameters, resourceAccessor)
		includedChangeLog?.changeSets.each { changeSet ->
			databaseChangeLog.addChangeSet(changeSet)
		}
		includedChangeLog?.preconditionContainer?.nestedPreconditions.each { precondition ->
			databaseChangeLog.preconditionContainer.addNestedPrecondition(precondition)
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
		def unsupportedKeys = params.keySet() - ['name', 'value', 'context', 'labels', 'dbms', 'global', 'file']
		if (unsupportedKeys.size() > 0) {
			throw new ChangeLogParseException("DababaseChangeLog: ${unsupportedKeys.toArray()[0]} is not a supported property attribute")
		}

		ContextExpression context = null
		if (params['context'] != null) {
			context = new ContextExpression(params['context'])
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
			def propertiesStreams = resourceAccessor.getResourcesAsStream(propFile)
			if (!propertiesStreams) {
				throw new ChangeLogParseException("Unable to load file with properties: ${params['file']}")
			} else {
				propertiesStreams.each { stream ->
					props.load(stream)
					props.each { k, v ->
						changeLogParameters.set(k, v, context as ContextExpression, labels as Labels, dbms, global, databaseChangeLog)
					}
				}
			}
		}
	}

	def propertyMissing(String name) {
		def changeLogParameters = databaseChangeLog.changeLogParameters
		if (changeLogParameters.hasValue(name)) {
			return changeLogParameters.getValue(name)
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


}
