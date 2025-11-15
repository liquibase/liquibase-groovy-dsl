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

import groovy.transform.PackageScope
import groovy.transform.TypeChecked
import liquibase.ContextExpression
import liquibase.Labels
import liquibase.change.visitor.ChangeVisitor
import liquibase.change.visitor.ChangeVisitorFactory // since v4.24.0
import liquibase.changelog.ChangeSet
import liquibase.changelog.ChangeSet.ValidationFailOption
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.IncludeAllFilter
import liquibase.database.DatabaseList
import liquibase.database.ObjectQuotingStrategy
import liquibase.exception.ChangeLogParseException
import liquibase.precondition.core.PreconditionContainer.OnSqlOutputOption
import liquibase.precondition.core.PreconditionContainer.ErrorOption
import liquibase.precondition.core.PreconditionContainer.FailOption
import liquibase.resource.ResourceAccessor
import liquibase.util.FileUtil
import liquibase.parser.ext.GroovyLiquibaseChangeLogParser.Arg
import liquibase.util.LiquibaseUtil

import static PreconditionDelegate.buildPreconditionContainer
import static groovy.lang.Closure.DELEGATE_ONLY
import static groovy.transform.TypeCheckingMode.SKIP
import static liquibase.parser.ext.GroovyLiquibaseChangeLogParser.*
import static org.liquibase.groovy.delegate.DelegateUtil.*

/**
 * This class is the delegate for the {@code databaseChangeLog} element. It is the starting point
 * for parsing the Groovy DSL.
 *
 * @author Steven C. Saliman
 */
@groovy.transform.CompileStatic
class DatabaseChangeLogDelegate extends Delegatee<Tag> {
    static enum Tag { property, include, includeAll, changeSet, preConditions }

    protected final ResourceAccessor resourceAccessor

    DatabaseChangeLogDelegate(DatabaseChangeLog databaseChangeLog, ResourceAccessor resourceAccessor,
                              Map<String, Object> params = [:]) {
        super( databaseChangeLog, dbChangeLogTagName )
        this.resourceAccessor = resourceAccessor
        // It doesn't make sense to expand expressions, since we haven't loaded properties yet.
        params.each { key, value ->
            // The contextFilter attribute needs a little work.  The value needs to be converted
            // into an object, and for now, we'll support the old "context" attribute.
            if ( key.equals("context") || key.equals("contextFilter")) {
                value = new ContextExpression(value as String) {}
                // LB >= 4.16 uses "contextFilter", so convert the pre-4.16 key.
                key = "contextFilter"
            }
            databaseChangeLog[key] = value // TODO closure handling
        }
    }

    /** Add a <a href="https://docs.liquibase.com/concepts/changelogs/changeset.html">Changeset</a>
     to the change log.<br>
     <b>Params:</b>
     <dl>
     <dt><b>{@code id}</b></dt>
         <dd>The 2nd part of the changeset's unique identifier</dd>
     <dt><b>{@code author}</b></dt>
        <dd>Creator of the changeset (the 3rd part of the changeset's unique identifier)</dd>
     <dt><a href='https://docs.liquibase.com/concepts/changelogs/attributes/runonchange.html'>runOnChange</a></dt>
        <dd>Execute changeset the first and each time it changes. Default: false</dd>
     <dt><a href='https://docs.liquibase.com/concepts/changelogs/attributes/run-always.html'>runAlways</a></dt>
     <dd>execute changeset at every database deployment, even if it has been run before. Default: false</dd>
     <dt>{@code contextFilter}</dt>
     <dd>Specifies the changeset context filter to match. <a href="https://docs.liquibase.com/concepts/changelogs/attributes/contexts.html">Contexts</a>
        are tags you can add to changesets to control which changesets will be executed in any particular migration run.
        Renamed from {@code context} since v4.16</dd>
     <dt><a href="https://docs.liquibase.com/concepts/changelogs/attributes/labels.html">labels</a></dt>
     <dd>List of labels required to execute the changeSet. Labels are tags you can add to changesets
        to control which changesets will be executed in any particular migration run.</dd>
     <dt>{@code dbms}</dt>
     <dd>Specifies which database type(s) a changeset is to be used for.
         See valid database type names on <a href='https://docs.liquibase.com/start/tutorials/home.html'>
         Liquibase Database Tutorials</a>.
         Separate multiple databases with commas. Specify that a changeset is not applicable to
         a particular database type by prefixing with !.
        The keywords {@code all} and {@code none} are also available.
        Will run for all dbms' if empty or absent</dd>
     <dt>{@code logicalFilePath}</dt>
     <dd>Overrides the file name and path when creating the unique identifier of changesets.
        (the 1st part of the changeset's unique identifier)
         It is required when you want to move or rename changelogs.</dd>
     <dt><a href='https://docs.liquibase.com/concepts/changelogs/attributes/fail-on-error.html'>failOnError</a></dt>
     <dd>Defines whether a database migration will fail if an error occurs while executing the
        changeset. Default: true.</dd>
     <dt><a href='https://docs.liquibase.com/concepts/changelogs/attributes/on-validation-fail.html'>onValidationFail</a></dt>
     <dd>Controls what Liquibase does when a changeset fails validation. Values are {@code HALT}
        and {@code MARK_RAN}. Default: {@code HALT}.</dd>
     <dt>{@code created}</dt>
     <dd>Stores dates, versions, or any other string of value without using remarks (comments)
        attributes. Since v3.5</dd>
     <dt><a href='https://docs.liquibase.com/concepts/changelogs/attributes/run-in-transaction.html'>runInTransaction</a></dt>
     <dd>Execute the changeset in a transaction. Default: true</dd>
     <dt><a href='https://docs.liquibase.com/concepts/changelogs/attributes/run-order.html'>runOrder</a></dt>
     <dd>Specifies whether a changeset should be run before or after all other changesets
         instead of running it sequentially based on its order in the changelog.
         Valid values are <code>first</code> and <code>last</code>. It is typically used when you
         want a changeset to be always executed after everything else but don’t want to keep moving it
         to the end of the changelog. Setting the runOrder to last will automatically move
         the changeset to the final place in the changeset run order. Since v3.5</dd>
     <dt><a href='https://docs.liquibase.com/concepts/changelogs/attributes/objectquotingstrategy.html'>objectQuotingStrategy</a></dt>
     <dd>Controls how object names are quoted in the SQL files generated by Liquibase and used in
        calls to the database. Default: LEGACY.
        <dl>
        <dt>{@code LEGACY}</dt>
         <dd>The default value. Does not quote objects unless the database specifies that they must be quoted, usually including reserved words and names with hyphens. In PostgreSQL databases, mixed-case names will also be quoted.</dd>
        <dt>{@code QUOTE_ALL_OBJECTS}</dt>
         <dd>Every object gets quoted. For example, person becomes "person".</dd>
        <dt>{@code QUOTE_ONLY_RESERVED_WORDS}</dt>
         <dd>The same logic as LEGACY, but without mixed-case objects in PostgreSQL databases.</dd>
        </dl>
     </dd>
     <dt><a href='https://docs.liquibase.com/concepts/changelogs/attributes/runwith.html'>runWith</a></dt>
     <dd>Specifies a native executor to run your SQL
         (jdbc, mongosh, psql, sqlcmd, sqlpus, or a custom executor). Default: jdbc.</dd>
     <dt><a href='https://docs.liquibase.com/concepts/changelogs/attributes/run-with-spool-file.html'>runWithSpoolFile</a></dt>
     <dd>Specifies a spool file to send output to when you deploy a particular changeset.
        This is useful if you want a changeset to have its own spool file.</dd>
     <dt><a href='https://docs.liquibase.com/concepts/changelogs/attributes/ignore.html'>ignore</a></dt>
     <dd>treat changeset as if it does not exist. Default: false. since: v3.6</dd>
     </dl>
     @param changes closure containing, the refactoring changes the change set should make.
     */    // TODO annotate mandatory params
    void changeSet(String id, String author, Boolean runOnChange = null,
                   @Since(value=v4_16, oldName = 'context') String contextFilter = null,
                   Boolean runAlways = null, String labels = null,
                   String dbms = null, String logicalFilePath = null,
                   ValidationFailOption onValidationFail = null,
                   Boolean runInTransaction = null, String runOrder = null, Boolean failOnError = null,
                   ObjectQuotingStrategy objectQuotingStrategy = null, String runWith = null,
                   String created = null,
                   @Since('4.21') String runWithSpoolFile = null,
                   Boolean ignore = null,
                   @DelegatesTo(value=ChangeSetDelegate, strategy=DELEGATE_ONLY) Closure changes) {
        changeSet( [:], id, author, runOnChange, contextFilter, runAlways, labels, dbms,
                    logicalFilePath, onValidationFail, runInTransaction, runOrder, failOnError ,
                    objectQuotingStrategy, runWith, created, runWithSpoolFile, ignore, changes)
    }

    /** {@link #changeSet} */
    void changeSet(Map ǃ, String id, String author = null,
                   Boolean runOnChange = null, String contextFilter = null,
                   Boolean runAlways = null, String labels = null, String dbms = null,
                   String logicalFilePath = null, ValidationFailOption onValidationFail = null,
                   Boolean runInTransaction = null, String runOrder = null, Boolean failOnError = null,
                   ObjectQuotingStrategy objectQuotingStrategy = null, String runWith = null,
                   String created = null, String runWithSpoolFile = null, Boolean ignore = null,
                   @DelegatesTo(value=ChangeSetDelegate, strategy=DELEGATE_ONLY) Closure changes) {
        changeSet chkMap( Tag.changeSet, ǃ)
            .call('id', id)
            .call('author', author)
            .call(Arg.dbms, dbms)
            .call('runAlways', runAlways)
            .call('runOnChange', runOnChange)
            .call(Arg.contextFilter, contextFilter)
            .call(Arg.labels, labels)
            .call('runInTransaction', runInTransaction)
            .call('failOnError', failOnError)
            .call('onValidationFail', onValidationFail)
            .call('objectQuotingStrategy', objectQuotingStrategy)
            .call(Arg.logicalFilePath, logicalFilePath) // filePath until TODO
            .call('created', created)
            .call('runOrder', runOrder)
            .call(Arg.ignore, ignore)
            .call(Arg.runWith, runWith)
            .call(Arg.runWithSpoolFile, runWithSpoolFile) // TODO since...
            .asMap, changes
    }

    /** {@link #changeSet} */
    void changeSet(Map params,
                   @DelegatesTo(value = ChangeSetDelegate, strategy = DELEGATE_ONLY)  Closure changes) {
        // Most of the time, we just pass any parameters through to a newly created Liquibase
        // object, but we need to do things a little differently for a ChangeSet because the
        // Liquibase object does not have setters for its properties. We'll need to figure it all
        // out for the constructor.  We want to warn people if they try to pass in something that is
        // not supported because we don't want to silently ignore things, so first get a list of
        // unsupported keys.
        if (params.containsKey('alwaysRun')) {
            throw new ChangeLogParseException("Error: ChangeSet '${params.id}': the alwaysRun attribute of a changeSet has been removed.  Please use 'runAlways' instead.")
        }
        // TODO use validateArgs
        def unsupportedKeys = params.keySet() - [
                'id',
                'author',
                Arg.dbms,
                'runAlways',
                'runOnChange',
                Arg.contextFilter,
                'context',
                Arg.labels,
                'runInTransaction',
                'failOnError',
                'onValidationFail',
                'objectQuotingStrategy',
                Arg.logicalFilePath,
                'filePath', // TODO Do we need to support it?????
                'created',
                'runOrder',
                Arg.ignore,
            Arg.runWith,
            Arg.runWithSpoolFile
        ]
        if (unsupportedKeys.size() > 0) {
            throw new ChangeLogParseException("ChangeSet '${params.id}': ${unsupportedKeys.toArray()[0]} is not a supported ChangeSet attribute")
        }
        // TODO Using ParsedNode + changeSet.load would remove duplicated code and version dependency
        ObjectQuotingStrategy objectQuotingStrategy = null
        if ( params.containsKey("objectQuotingStrategy") ) {
            try {
                objectQuotingStrategy = params.objectQuotingStrategy as ObjectQuotingStrategy
            } catch ( IllegalArgumentException ignored) {
                throw new ChangeLogParseException("ChangeSet '${params.id}': ${params.objectQuotingStrategy} is not a supported ChangeSet ObjectQuotingStrategy")
            }
        }

        String filePath = databaseChangeLog.filePath // default
        if ( params.containsKey('filePath') ) {
            filePath = params.filePath
        }

        // Liquibase 4.16 deprecated "context" in favor of "contextFilter", but it still supports
        // both.  A null here is fine.
        def contextFilter = params.contextFilter? params.contextFilter : params.context

        def changeSet = new ChangeSet(
                DelegateUtil.expandExpressions(params.id, databaseChangeLog),
                DelegateUtil.expandExpressions(params.author, databaseChangeLog),
                DelegateUtil.parseTruth(params.runAlways, false),
                parseTruth(params.runOnChange, false),
                filePath,
                DelegateUtil.expandExpressions(contextFilter, databaseChangeLog),
                DelegateUtil.expandExpressions(params.dbms, databaseChangeLog),
                DelegateUtil.parseTruth(params.runInTransaction, true),
                objectQuotingStrategy,
                databaseChangeLog)

        changeSet.changeLogParameters = databaseChangeLog.changeLogParameters
            // 4 backward compatibility of earlier Liquibase
        if(params.runWith && changeSet.hasProperty(Arg.runWith))
            changeSet.runWith = expandExpressions(params.runWith, databaseChangeLog)
        if(params.runWithSpoolFile && changeSet.hasProperty(Arg.runWithSpoolFile))
            changeSet.runWithSpoolFile = expandExpressions(params.runWithSpoolFile, databaseChangeLog)

        if ( params.containsKey('failOnError') ) {
            changeSet.failOnError = parseTruth(params.failOnError, false)
        }

        if ( params.onValidationFail ) {
            changeSet.onValidationFail = params.onValidationFail as ChangeSet.ValidationFailOption
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
            changeSet.ignore = parseTruth(params.ignore, false)
        }

        if ( params.logicalFilePath ) {
            changeSet.logicalFilePath = params.logicalFilePath
        }

        callOn(new ChangeSetDelegate(changeSet), changes)

        databaseChangeLog.addChangeSet(changeSet)
    }

    /** <a href="https://docs.liquibase.com/change-types/include.html">Include</a> a file with change sets.
     <br>Params:
     <dl>
     <dt><b>{@code file}</b></dt>
        <dd>Path of the file to include (required)</dd>
     <dt>{@code relativeToChangelogFile}</dt>
        <dd>Specifies whether the {file} path is relative to the changelog file rather than looked up in the search path. Default: false</dd>
     <dt>{@code contextFilter}</dt>
        <dd>Appends a <a href="https://docs.liquibase.com/concepts/changelogs/attributes/contexts.html">context</a> (using an AND statement) to all contained changesets</dd>
     <dt>{@code labels}</dt>
        <dd>Appends a <a href="https://docs.liquibase.com/concepts/changelogs/attributes/labels.html">label</a> (using an AND statement) to all contained changesets.</dd>
     <dt>{@code errorIfMissing}</dt>
        <dd>Controls what happens if the file listed does not exist. If set to true, the update fails. Default: true.</dd>
     <dt>{@code ignore}</dt>
        <dd>If true changesets in the included file treated as if it does not exist. The file still has to exist if @errorIfMissing true. Since v3.7.0. Default: false.</dd>
     </dl>
     */
    void include(String file, Boolean relativeToChangelogFile = null,
                @Since(value=v4_16,oldName='context') String contextFilter = null,
                String labels = null, Boolean errorIfMissing = null,
                @Since(v4_30) String logicalFilePath = null,
                Boolean ignore = null){
        include [:], file, relativeToChangelogFile, contextFilter, labels, errorIfMissing,
                          logicalFilePath, ignore
    }

    /** {@link #include} */
    void include(Map ǃ, String file, Boolean relativeToChangelogFile = null,
                 String contextFilter = null, String labels = null,
                 Boolean errorIfMissing = null, String logicalFilePath = null,
                 Boolean ignore = null) {
        include chkMap(Tag.include, ǃ) (Arg.file, file)
                .call(Arg.relativeToChangelogFile, relativeToChangelogFile)
                .call(Arg.contextFilter, contextFilter)(Arg.labels, labels)
                .call(Arg.errorIfMissing, errorIfMissing)
                .call(Arg.logicalFilePath, logicalFilePath)(Arg.ignore, ignore)
                .asMap
    }

    @TypeChecked(SKIP)
    /** {@link #include} */
    void include(Map params) {
        validateArgs(Tag.include, params)

        def relativeToChangelogFile = parseTruth(params.relativeToChangelogFile, false)
        def errorIfMissing = parseTruth(params.errorIfMissing, true)

        String fileName = expandExpressions(params.file?.toString(), databaseChangeLog)
        String context = params.contextFilter? params.contextFilter : params.context
        def includeContexts = new ContextExpression(context)
        def labels = new Labels(params.labels?.toString())
        boolean ignore = parseTruth(params.ignore, false)

        // TODO not 3.10.3 compatible
        // The Resource Accessor we need to use depends on whether we are including a relative file
        // or an absolute file, and which version of Liquibase we're using.  For now, we'll assume
        // that we have a relative include, which uses the resource accessor we've been given.
        if ( lbVersionAtLeast(v4_30) ) {
            // Liquibase 4.30+, include the logicalFilePath.
            databaseChangeLog.include(fileName, relativeToChangelogFile, errorIfMissing,
                    resourceAccessor, includeContexts, labels, ignore, params.logicalFilePath,
                    DatabaseChangeLog.OnUnknownFileFormat.FAIL)
        } else {
            // Pre-4.30, exclude logicalFilePath
            if(params.logicalFilePath) { // TODO should be in validate Args
                logTagPropertyNotYetSupportedWarning Tag.include, Arg.logicalFilePath, v4_30, LiquibaseUtil.getBuildVersion()
            }
            databaseChangeLog.include(fileName, relativeToChangelogFile, errorIfMissing,
                    resourceAccessor, includeContexts, labels, ignore,
                    DatabaseChangeLog.OnUnknownFileFormat.FAIL)
        }
    }

    /** <a href="https://docs.liquibase.com/change-types/includeall.html">Include all</a> files from
     the folder defined by `path`
     <br>Params:
     <dl>
     <dt><b>{@code path}</b></dt>
        <dd>Path of the folder to include files from (required)</dd>
     <dt>{@code relativeToChangelogFile}</dt>
         <dd>Specifies whether the {path} is relative to the changelog file rather than looked up in the search path. Default: false</dd>
     <dt>{@code contextFilter}</dt>
        <dd>Appends a <a href="https://docs.liquibase.com/concepts/changelogs/attributes/contexts.html">context</a>
        (using an AND statement) to all contained changesets</dd>
     <dt>{@code labels}</dt>
        <dd>Appends a <a href="https://docs.liquibase.com/concepts/changelogs/attributes/labels.html">label</a>
        (using an AND statement) to all contained changesets.</dd>
     <dt>{@code endsWithFilter}</dt>
         <dd>Allows you to filter which packages are include based on their file name ending. since V4.24.</dd>
     <dt>{@code filter}</dt>
         <dd>Allows you to specify a custom filter class to include or exclude files from the
            <includeAll> search. Your class must implement the {@link IncludeAllFilter} interface.
            See Add an <a href='https://contribute.liquibase.com/extensions-integrations/extension-guides/add-an-includeall-filter'>IncludeAll Filter</a>.</dd>
     <dt>{@code maxDepth}</dt>
         <dd>Allows you to control the maximum depth of recursion applied by includeAll,
            starting from the directory in path.
            If maxDepth=1, only files in path will be included no subdirectories are searched.
            Values are inclusive. If maxDepth < minDepth, Liquibase returns an error. Default: Integer.MAX_VALUE.</dd>
     <dt>{@code minDepth}</dt>
         <dd>Allows you to control the minimum depth of recursion applied by includeAll.
            If minDepth=1, search includes files from the directory in path.
            If minDepth=2, search excludes files from the directory in path and starts from subdirectories of path.
            Values are inclusive. Default: 1</dd>
     <dt>{@code resourceComparator}</dt>
         <dd>A string containing the name of the class you want to use for sorting.
            Your class must implement the {@link Comparator} interface.
            See <a href='https://contribute.liquibase.com/extensions-integrations/extension-guides/add-an-includeall-comparator/'>Add an IncludeAll Comparator</a></dd>
     <dt>{@code errorIfMissingOrEmpty}</dt>
         <dd>Controls what happens if the path listed does not exist or is an empty directory.
            If set to true, the update fails. Default: true</dd>
     <dt>{@code ignore}</dt>
        <dd>Treat changesets in the included file as if it does not exist. Since 4.27.0. Default: false.</dd>
     </dl>
     */
    void includeAll(String path, Boolean relativeToChangelogFile=null,
                    @Since(value=v4_16,oldName='context') String contextFilter=null,
                    String labels=null, String endsWithFilter=null, String filter=null,
                    Integer maxDepth=null, Integer minDepth=null,
                    String resourceComparator=null,
                    Boolean errorIfMissingOrEmpty=null,
                    @Since(v4_30) String logicalFilePath = null,
                    Boolean ignore = null) {
        includeAll [:], path, relativeToChangelogFile,
                    contextFilter, labels,
                    endsWithFilter, filter,
                    maxDepth, minDepth,
                    resourceComparator,
                    errorIfMissingOrEmpty, logicalFilePath, ignore
    }

    /** {@link #includeAll} */
    void includeAll(Map ǃ, String path, Boolean relativeToChangelogFile=null,
                    String contextFilter=null, String labels=null,
                    String endsWithFilter=null, String filter=null,
                    Integer maxDepth=null, Integer minDepth=null,
                    String resourceComparator=null,
                    Boolean errorIfMissingOrEmpty=null,
                    String logicalFilePath = null,
                    Boolean ignore = null) {
        includeAll argsAsMap(Tag.includeAll, ǃ, path, relativeToChangelogFile,
                    contextFilter, labels,
                    endsWithFilter, filter,
                    maxDepth, minDepth,
                    resourceComparator,
                    errorIfMissingOrEmpty, logicalFilePath, ignore)
    }

    @TypeChecked(SKIP)
    /** {@link #includeAll} */
    void includeAll(Map params) {
        // validate parameters.
        validateArgs(Tag.includeAll, params)
/*       def unsupportedKeys = params.keySet() - [
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
*/
        def includeAllParams = createIncludeAllParams(params)
        if ( lbVersionAtLeast(v4_30) ) {
            // Liquibase 4.30+, include the logicalFilePath.
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
        } else {
            // Pre-4.30, exclude logicalFilePath
            if(params.logicalFilePath) { // TODO should be in validateArgs
                logTagPropertyNotYetSupportedWarning Tag.includeAll, Arg.logicalFilePath, v4_30, LiquibaseUtil.getBuildVersion()
            }
            databaseChangeLog.includeAll(includeAllParams.path,
                    includeAllParams.relativeToChangelogFile,
                    includeAllParams.filter,
                    includeAllParams.errorIfMissingOrEmpty,
                    includeAllParams.resourceComparator,
                    resourceAccessor,
                    includeAllParams.includeContexts,
                    includeAllParams.labels,
                    includeAllParams.ignore,  // after this, need logical file path, maybe pass null?
                    includeAllParams.minDepth,
                    includeAllParams.maxDepth,
                    includeAllParams.endsWithFilter,
                    null)
        }
    }

    /**
     * Process the Groovy DSL's special includeAllSql element that creates a changeSet with a
     * sqlFile change for each file found in the specified path.
     * @param params the params that affect how files are found, and how the changeSets are created
     *         from each one.
     */
    @TypeChecked(SKIP)
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
                'logicalFilePath',
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
        changeSetParams.runAlways = parseTruth(params.runAlways, false)
        changeSetParams.runOnChange = parseTruth(params.runOnChange, true)
        changeSetParams.failOnError = parseTruth(params.failOnError, false)
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

        def idKeepsExtension = parseTruth(params.idKeepsExtension, false)

        // if we have files, sort them and make a change set for each one.
        sqlFiles.each { fileName ->
            // We want the id to be based off the filename, minus any directories, and with the
            // extension stripped off, unless the user wanted to keep extensions.
            String baseName = fileName.path.tokenize('/').last().tokenize('\\').last()
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

    /** <a href='https://docs.liquibase.com/concepts/changelogs/preconditions.html'>Preconditions</a>
     required to execute the changelog. The closure containing nested elements of a precondition.
     If no conditional tags are specified, the default logic is AND for multiple conditions
     <br>Params:
     <dl>
     <dt>{@code onError}</dt>
     <dd>Controls what happens if there is an error checking whether the precondition passed or not.
        Default: HALT. Valid values are:<br>
        HALT - Halts the execution of the entire changelog (default).<br>
        WARN - Sends a warning and continues executing the changelog as normal.
     </dd>
     <dt>{@code onErrorMessage}</dt>
     <dd>Provides a custom message to output when preconditions fail. Since 2.0</dd>
     <dt>{@code onFail}</dt>
     <dd>Controls what happens if the preconditions check fails. Default: HALT
         Valid values are:<br>
         HALT - Halts the execution of the entire changelog (default).<br>
         WARN - Sends a warning and continues executing the changelog as normal.
     </dd>
     <dt>{@code onFailMessage}</dt>
     <dd>Provides a custom message to output when preconditions fail. Since 2.0</dd>
     <dt>{@code onSqlOutput}</dt>
     <dd>Controls how preconditions are evaluated with the update-sql command for XML, YAML, and JSON changelogs. Since 1.9.5</dd>
      </dl>
     */
    void preConditions(FailOption onFail = null, ErrorOption onError = null,
                       String onFailMessage = null, String onErrorMessage = null,
                       OnSqlOutputOption onSqlOutput = null,
                       @DelegatesTo(value= PreconditionDelegate, strategy=DELEGATE_ONLY) Closure preconditions) {
        preConditions [:], onFail, onError, onFailMessage, onErrorMessage, onSqlOutput, preconditions
    }

    /** {@link #preConditions} */
    void preConditions(Map ǃ,
                       FailOption onFail = null, ErrorOption onError = null,
                       String onFailMessage = null, String onErrorMessage = null,
                       OnSqlOutputOption onSqlOutput = null,
                       @DelegatesTo(value= PreconditionDelegate, strategy=DELEGATE_ONLY) Closure preconditions) {
        argsAsMap(Tag.preConditions, ǃ, onFail, onError, onFailMessage, onErrorMessage, onSqlOutput, preconditions)
        databaseChangeLog.preconditions = buildPreconditionContainer(databaseChangeLog, ǃ, preconditions)
    }

    /**
     Define a property for substitution in your changelog.
     The tokens to replace in your changelog are using the ${property-name} syntax.
     For example, your tablespace name in Oracle may differ from environment to environment,
     but you want to only write one create table changeset that can be used in all your environments.
     See: <a href='https://docs.liquibase.com/concepts/changelogs/property-substitution.html'>property substitution</a>
     <br>Params:
     <dl>
     <dt><b>{@code name}</b></dt>
        <dd>The name of the property</dd>
     <dt><b>{@code value}</b></dt>
        <dd>The value of the property.</dd>
     <dt>{@code contextFilter}</dt>
        <dd><a href="https://docs.liquibase.com/concepts/changelogs/attributes/contexts.html">Contexts</a> in which the property is valid. Expected as a comma-separated list.</dd>
     <dt>{@code dbms}</dt>
        <dd>Comma separated list of database type(s) a changeset is to be used for. See valid database type
            names on <a href='https://docs.liquibase.com/start/tutorials/home.html'>
            Liquibase Database Tutorials</a> Specify that a changeset is not applicable to a particular database type by prefixing with !.
            The keywords all and none are also available.</dd>
     <dt>{@code global}</dt>
         <dd>Defines whether the property is global (available in included changeLogs also)
            or limited to the actual changeLog. Default: true.</dd>
     </dl>*/
    void property(String name, String value,
                  String contextFilter = null, String labels = null,
                  String dbms = null, Boolean global = null) {
        property [:], name, value, contextFilter, labels ,dbms, global
    }

    /** {@link #property} */
    void property(Map ǃ, String name, String value,
                  String contextFilter = null, String labels = null,
                  String dbms = null, Boolean global = null) {
        property chkMap(Tag.property, ǃ)
                .call(Arg.name, name)
                .call(Arg.value, value)
                .call(Arg.contextFilter, contextFilter)
                .call(Arg.labels, labels)
                .call(Arg.dbms, dbms)
                .call(Arg.global, global)
                .asMap
    }

    /**
     Define a property for substitution in your changelog.
     The tokens to replace in your changelog are using the ${property-name} syntax.
     For example, your tablespace name in Oracle may differ from environment to environment,
     but you want to only write one create table changeset that can be used in all your environments.
     See: <a href='https://docs.liquibase.com/concepts/changelogs/property-substitution.html'>property substitution</a>
     <br>Params:
     <dl>
     <dt><b>{@code file}</b></dt>
         <dd>The name of the file from which the properties should be loaded.
             It will create a property for all properties in the file. The content of the file must
             follow the Java properties file format.</dd>
     <dt>{@code relativeToChangelogFile}</dt>
         <dd>The relativeToChangelogFile attribute is used in conjunction with the file attribute to
             allow Liquibase to find the referenced file without having to configure search-path.
             The default for relativeToChangelogFile remains FALSE for backwards compatibility.</dd>
     <dt>{@code contextFilter}</dt>
        <dd><a href="https://docs.liquibase.com/concepts/changelogs/attributes/contexts.html">Contexts</a> in which the property is valid. </dd>
     <dt>{@code dbms}</dt>
         <dd>Comma separated list of database type(s) a changeset is to be used for. See valid database type
             names on <a href='https://docs.liquibase.com/start/tutorials/home.html'>
             Liquibase Database Tutorials</a> Specify that a changeset is not applicable to a particular database type by prefixing with !.
             The keywords all and none are also available.</dd>
     <dt>{@code global}</dt>
        <dd>Defines whether the property is global (available in included changeLogs also)
            or limited to the actual changeLog. Default: true.</dd>
     </dl>*/
    void property(String file, Boolean relativeToChangelogFile = null,
                  String contextFilter = null, String labels = null,
                  String dbms = null, Boolean global = null, Boolean errorIfMissing = null) {
        property [:], file, relativeToChangelogFile, contextFilter, labels, dbms, global, errorIfMissing
    }

    /** {@link #property} */
    void property(Map ǃ, String file, Boolean relativeToChangelogFile = null,
                  String contextFilter = null, String labels = null,
                  String dbms = null, Boolean global = null, Boolean errorIfMissing = null) {
        property chkMap(Tag.property, ǃ) // TODO use method definition
                .call(Arg.contextFilter, contextFilter)
                .call(Arg.labels, labels)
                .call(Arg.file, file)
                .call(Arg.dbms, dbms)
                .call(Arg.global, global)
                .call(Arg.relativeToChangelogFile, relativeToChangelogFile)
                .call(Arg.errorIfMissing, errorIfMissing)
                .asMap
    }

    /** {@link #property} */
    void property(Map<String, Object> params) {
        // Start by validating input
        def unsupportedKeys = params.keySet() - [
                Arg.name,
                Arg.value,
                Arg.context,
                Arg.contextFilter,
                Arg.labels,
                Arg.dbms,
                Arg.global,
                Arg.file,
                Arg.relativeToChangelogFile,
                Arg.errorIfMissing,
        ]
        if (unsupportedKeys.size() > 0) {
            throw new ChangeLogParseException("DatabaseChangeLog: ${unsupportedKeys.toArray()[0]} is not a supported property attribute")
        }

        String contextFilter = params.contextFilter? params.contextFilter : params.context
        def context = new ContextExpression(contextFilter)

        Labels labels = new Labels(params.labels as String)

        String dbms = expandExpressions(params.dbms, databaseChangeLog)
        // The default for global was true prior to Liquibase 3.4, and the other parsers still use
        // true as the default.
        def global = DelegateUtil.parseTruth(params.global, true)

        def changeLogParameters = databaseChangeLog.changeLogParameters

        if (null == params.file) {
            changeLogParameters.set(params.name as String, params.value as String, context, labels, dbms, global, databaseChangeLog)
        } else {
            String file = expandExpressions( params.file, databaseChangeLog )
            if(!file) {
                throw changeLogParseException(nonEmptyParameterRequiredFor(Tag.property, Arg.file))
            }
            if(params.name || params.value){ // TODO: Should go into Liquibase
                logWarning("'name' and 'value' parameters are ignored if 'file' is set")
            }
            def relativeTo = null // Default to a path relative to the working directory
            if ( parseTruth(params[Arg.relativeToChangelogFile], false) ) {
                relativeTo = databaseChangeLog.physicalFilePath
            }
            def errorIfMissing = parseTruth(params[Arg.errorIfMissing], true)
            def props = new Properties()

            def stream = resourceAccessor.openStream(relativeTo, file)
            if ( stream ) {
                props.load(stream)
                props.each {k, v ->
                    changeLogParameters.set(k.toString(), v.toString(), context, labels, dbms, global, databaseChangeLog)
                }
            } else {
                if ( errorIfMissing ) {
                    throw changeLogParseException("Unable to load file with properties: $file}")
                }
                logWarning(FileUtil.getFileNotFoundMessage(file))
            }
        }
    }

    /**
     * Process nested removeChangeSetProperty elements in a changelog.
     * @param params the attributes of the removeChangeSetProperty change.
     */
    @TypeChecked(SKIP)
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

    protected def propertyMissing(String name) {
        def changeLogParameters = databaseChangeLog.changeLogParameters
        if (changeLogParameters.hasValue(name, databaseChangeLog)) { // TODO: Test?
            return changeLogParameters.getValue(name, databaseChangeLog)
        } else {
            methodMissing( name, objArr())
        }
    }

    /**
     * Helper method that "fixes" incoming parameters to be used with includeAll and includeAllSql
     * elements.  It makes sure we have sensible defaults for all the required items.
     * @param params the incoming parameters to "fix"
     * @return a copy of the parameters with various items replaced by objects we can use elsewhere.
     */
    @TypeChecked(SKIP)
    private createIncludeAllParams(Map<String, Object> params) {
        def includeAllParams = params.collectEntries(Closure.IDENTITY)

        // If the incoming params contain certain keys, copy them to the final params, if not, use
        // a default.  Groovy's way of getting a value with myMap.someKey, combined with the elvis
        // operator works well, and is concise, but there is a hidden "gotcha" we need to watch out
        // for...  The number 0 is "falsy" in Groovy, which means that If the value of a parameter
        // is 0 (as maxDepth could be), then the elvis operator will return false, and we'll get the
        // default value instead of the given value of 0.  This means that if a param could be 0, we
        // we need to use containsKey instead.
        includeAllParams.relativeToChangelogFile = parseTruth(params.relativeToChangelogFile, false)
        includeAllParams.errorIfMissingOrEmpty = parseTruth(params.errorIfMissingOrEmpty, true)
        def context = params.contextFilter? params.contextFilter : params.context
        includeAllParams.includeContexts = new ContextExpression(context)
        includeAllParams.ignore = parseTruth(params.ignore, false)
        includeAllParams.logicalFilePath = params.logicalFilePath
        includeAllParams.labels = new Labels(params.labels)
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
    @TypeChecked(SKIP)
    private static Comparator<String> getStandardChangeLogComparator() {
        // Liquibase won't let us send a null comparator, but doesn't expose the default to us.  So
        // we'll just return what Liquibase uses.  It might be worth DatabaseChangeLog from time
        // to time to make sure they don't change this out from under us.
        Comparator.comparing(o -> o.replace("WEB-INF/classes/", "")) as Comparator<String>
    }
}
