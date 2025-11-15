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

import groovy.transform.CompileStatic
import groovy.transform.SelfType
import liquibase.change.AddColumnConfig
import liquibase.change.Change
import liquibase.change.ChangeWithColumns
import liquibase.change.ColumnConfig
import liquibase.change.core.LoadDataColumnConfig
import liquibase.serializer.LiquibaseSerializable
import liquibase.statement.DatabaseFunction
import liquibase.statement.SequenceCurrentValueFunction
import liquibase.statement.SequenceNextValueFunction

import static groovy.lang.Closure.DELEGATE_ONLY
import static DelegateUtil.callOn

/**
 * This class is a delegate for nested columns found frequently in the DSL, such as inside the
 * {@code createTable} change.  It can handle both normal columns, as found in the
 * {@code createTable} change, and the {@code LoadDataColumnConfig} columns that can be found in the
 * {@code loadData} change.  When the {@link ChangeSetDelegate} creates a ColumnDelegate for a,
 * given change, it will need to set the correct columnConfigClass.
 * <p>
 * This class also handles the nested where and whereParams elements that appear in the
 * {@code update} and {@code delete} changes.  This probably does not cohere with the overall
 * purpose of the class, but it is much better than having to duplicate the column processing logic
 * since the {@code update} change uses columns and a where clause.
 * <p>
 * This delegate will expand expressions to make databaseChangeLog property substitutions.  It is
 * important that the caller does not do it again.
 *
 * @author Steven C. Saliman
 */
@CompileStatic
abstract class ColumnDelegate<T extends ColumnConfig> extends ChangeDelegate {
    protected final Class<T> columnConfigClass
    protected ChangeWithColumns getChange() {super.change as ChangeWithColumns}

    ColumnDelegate(ChangeSetDelegate changeSet, ChangeWithColumns change, Class<T> columnConfigClass = ColumnConfig ) {
        super(changeSet, change as Change )
        this.columnConfigClass = columnConfigClass
    }

    /**
     * Parse a single column entry in a closure.
     * @param params the attributes to set.
     * @param closure a child closure to call, such as a constraint clause
     */
    T column(Map params) {
        T column = columnConfigClass.newInstance()

        setProps(column, params)

        // Try to add the column to the change.  If we're dealing with something like a "delete"
        // change, we'll get an exception, which we'll rethrow as a parse exception to tell the user
        // that columns are not allowed in that change.
        //try {
        change.addColumn(column)
//        } catch (MissingMethodException e) {
//            throw new ChangeLogParseException("ChangeSet '${changeSetId}': columns are not allowed in '${changeName}' changes.", e)
//        }
        column
    }

    protected Map<String, Object> argsToMap(Object... args) {
        MethodDef method = methodDefs['column']
        argsAsMap 'column', method, args
    }
}

@CompileStatic
abstract class ColumnDelegateHasConstraint<T extends ColumnConfig> extends ColumnDelegate<T> {
    ColumnDelegateHasConstraint(ChangeSetDelegate changeSet, ChangeWithColumns change, Class<T> columnConfigClass) {
        super(changeSet, change, columnConfigClass)
    }

    /**
     * Parse a single column entry in a closure.
     * @param params the attributes to set.
     * @param closure a child closure to call, such as a constraint clause
     */
    def column(Map params,
               @DelegatesTo(value = ConstraintDelegate, strategy = DELEGATE_ONLY) Closure constraints) {
        T col = column(params)
        // Process nested closure (constraints)
        if ( constraints ) {
            ConstraintDelegate constraintDelegate = new ConstraintDelegate(databaseChangeLog, changeId, parent)
            constraintDelegate.callOn(constraints)
            col.constraints = constraintDelegate.constraint
        }
    }
}

@CompileStatic
class DropColumnDelegate extends ColumnDelegate<ColumnConfig> {
    DropColumnDelegate(ChangeSetDelegate changeSet, ChangeWithColumns change, Class columnConfigClass = ColumnConfig) {
        super(changeSet, change, columnConfigClass)
    }

    void column(String name) {
        column([name: name])
    }
}

@CompileStatic
class CreateIndexDelegate extends DropColumnDelegate {
    CreateIndexDelegate(ChangeSetDelegate changeSet, ChangeWithColumns change) {
        super(changeSet, change, AddColumnConfig)
    }

    void column(String name, Boolean included = null) {
        column([name: name, included: included])
    }
}

@CompileStatic
class CreateTableDelegate extends ColumnDelegateHasConstraint<ColumnConfig> {
    CreateTableDelegate(ChangeSetDelegate changeSet, ChangeWithColumns change) {
        super( changeSet, change, ColumnConfig)
    }

    void column(String name,
                String type,
                Boolean computed=null,
                String defaultValue=null,
                Number defaultValueNumeric=null,
                Date defaultValueDate=null,
                Boolean defaultValueBoolean=null,
                DatabaseFunction defaultValueComputed=null,
                SequenceNextValueFunction defaultValueSequenceNext=null,
                String defaultValueConstraintName=null,
                Boolean autoIncrement=null,
                String generationType=null,
                Boolean defaultOnNull=null,
                BigInteger startWith=null,
                BigInteger incrementBy=null,
                String remarks=null,
                @DelegatesTo(value = ConstraintDelegate, strategy = DELEGATE_ONLY) Closure constraints) {
        column argsToMap(name,
                type,
                computed,
                defaultValue,
                defaultValueNumeric,
                defaultValueDate,
                defaultValueBoolean,
                defaultValueComputed,
                defaultValueSequenceNext,
                defaultValueConstraintName,
                autoIncrement,
                generationType,
                defaultOnNull,
                startWith,
                incrementBy,
                remarks), constraints
    }

    void column(String name,
                String type,
                Boolean computed=null,
                String defaultValue=null,
                Number defaultValueNumeric=null,
                Date defaultValueDate=null,
                Boolean defaultValueBoolean=null,
                DatabaseFunction defaultValueComputed=null,
                SequenceNextValueFunction defaultValueSequenceNext=null,
                String defaultValueConstraintName=null,
                Boolean autoIncrement=null,
                String generationType=null,
                Boolean defaultOnNull=null,
                BigInteger startWith=null,
                BigInteger incrementBy=null,
                String remarks=null){
        column argsToMap(name,
                type,
                computed,
                defaultValue,
                defaultValueNumeric,
                defaultValueDate,
                defaultValueBoolean,
                defaultValueComputed,
                defaultValueSequenceNext,
                defaultValueConstraintName,
                autoIncrement,
                generationType,
                defaultOnNull,
                startWith,
                incrementBy,
                remarks)
    }

    void column(Map ǃ,
                String name,
                String type,
                Boolean computed=null,
                String defaultValue=null,
                Number defaultValueNumeric=null,
                Date defaultValueDate=null,
                Boolean defaultValueBoolean=null,
                DatabaseFunction defaultValueComputed=null,
                SequenceNextValueFunction defaultValueSequenceNext=null,
                String defaultValueConstraintName=null,
                Boolean autoIncrement=null,
                String generationType=null,
                Boolean defaultOnNull=null,
                BigInteger startWith=null,
                BigInteger incrementBy=null,
                String remarks=null,
                @DelegatesTo(value = ConstraintDelegate, strategy = DELEGATE_ONLY)
                        Closure constraints) {
        column argsToMap(ǃ,
                name,
                type,
                computed,
                defaultValue,
                defaultValueNumeric,
                defaultValueDate,
                defaultValueBoolean,
                defaultValueComputed,
                defaultValueSequenceNext,
                defaultValueConstraintName,
                autoIncrement,
                generationType,
                defaultOnNull,
                startWith,
                incrementBy,
                remarks), constraints
    }

    void column(Map ǃ,
                String name,
                String type,
                Boolean computed=null,
                String defaultValue=null,
                Number defaultValueNumeric=null,
                Date defaultValueDate=null,
                Boolean defaultValueBoolean=null,
                DatabaseFunction defaultValueComputed=null,
                SequenceNextValueFunction defaultValueSequenceNext=null,
                String defaultValueConstraintName=null,
                Boolean autoIncrement=null,
                String generationType=null,
                Boolean defaultOnNull=null,
                BigInteger startWith=null,
                BigInteger incrementBy=null,
                String remarks=null){
        column argsToMap(ǃ,
                name,
                type,
                computed,
                defaultValue,
                defaultValueNumeric,
                defaultValueDate,
                defaultValueBoolean,
                defaultValueComputed,
                defaultValueSequenceNext,
                defaultValueConstraintName,
                autoIncrement,
                generationType,
                defaultOnNull,
                startWith,
                incrementBy,
                remarks)
    }
}

@CompileStatic
class LoadDataDelegate extends ColumnDelegate<LoadDataColumnConfig>{
    LoadDataDelegate(ChangeSetDelegate changeSet, ChangeWithColumns change) {
        super(changeSet, change, LoadDataColumnConfig)
    }

    /** Column definition.
     Either {@code name} or {@code index} must be defined to be able to identify the column in the CSV
     If the column name in the CSV is different than in the table, {@code header} needs to be defined to the column name in the CSV.
      {@code defaultValue[*]} attributes can define values for empty fields.
     <br>Params:<dl>
     <dt><b>name</b></dt>
     <dd>Name of the table column. If the column name in CSV is different {@code header} or {@code index} shall be also defined</dd>
     <dt>type</dt>
     <dd>Type of the column. If not defined, it is taken from the database
      Special value 'skip' force not to change the column content</dd>
      <dt>defaultValue[*]</dt>
      <dd>One of the {@code defaultValue[*]} attributes can define value for empty values in CSV</dd>
      <dt>header</dt>
      <dd>Name of the column in the CSV file from which the value for the column will be taken if it's different from the column name. Ignored if {@code index} is also defined.</dd>
      <dt>index</dt>
      <dd>Index of the column in the CSV file from which the value for the column will be taken. Required if column name in the CSV is different from the table's column name</dd>
      <dt>allowUpdate</dt>
      <dd>If set to false, only inserts are generated for the column. Default: true</dd>
     </dl>
     */
    void column( String name,
                 String type=null,
                 String defaultValue=null,
                 Number defaultValueNumeric=null,
                 Date defaultValueDate=null,
                 Boolean defaultValueBoolean=null,
                 DatabaseFunction defaultValueComputed=null,
                 String header=null,
                 Integer index=null,
                 Boolean allowUpdate=null){ // TODO should go only to loadUpdateData
        column argsToMap(name,
                type,
                defaultValue,
                defaultValueNumeric,
                defaultValueDate,
                defaultValueBoolean,
                defaultValueComputed,
                header,
                index,
                allowUpdate )
    }

    void column( Map ǃ,
                 String name,
                 String type=null,
                 String defaultValue=null,
                 Number defaultValueNumeric=null,
                 Date defaultValueDate=null,
                 Boolean defaultValueBoolean=null,
                 DatabaseFunction defaultValueComputed=null,
                 String header=null,
                 Integer index=null,
                 Boolean allowUpdate=null){
        column argsToMap(ǃ,
                name,
                type,
                defaultValue,
                defaultValueNumeric,
                defaultValueDate,
                defaultValueBoolean,
                defaultValueComputed,
                header,
                index,
                allowUpdate )
    }
}

@CompileStatic
class LoadUpdateDataDelegate extends LoadDataDelegate{
    LoadUpdateDataDelegate(ChangeSetDelegate changeSet, ChangeWithColumns change) {
        super(changeSet, change)
    }
}

@CompileStatic
class AddColumnDelegate extends ColumnDelegateHasConstraint<AddColumnConfig> {
    AddColumnDelegate(ChangeSetDelegate changeSet, ChangeWithColumns change) {
        super( changeSet, change, AddColumnConfig)
    }

    void column(Map ǃ,
                String name,
                String type,
                Boolean computed=null,
                String value=null,
                Number valueNumeric=null,
                Date valueDate=null,
                Boolean valueBoolean=null,
                String valueBlobFile=null,
                String valueClobFile=null,
                String encoding=null,
                DatabaseFunction valueComputed=null,
                SequenceNextValueFunction valueSequenceNext=null,
                SequenceCurrentValueFunction valueSequenceCurrent=null,
                String defaultValue=null,
                Number defaultValueNumeric=null,
                Date defaultValueDate=null,
                Boolean defaultValueBoolean=null,
                DatabaseFunction defaultValueComputed=null,
                SequenceNextValueFunction defaultValueSequenceNext=null,
                String defaultValueConstraintName=null,
                Boolean autoIncrement=null,
                String generationType=null,
                Boolean defaultOnNull=null,
                BigInteger startWith=null,
                BigInteger incrementBy=null,
                String remarks=null,
                String afterColumn=null,
                String beforeColumn=null,
                Integer position=null,
                @DelegatesTo(value = ConstraintDelegate, strategy = DELEGATE_ONLY) Closure constraints) {
        column argsToMap(ǃ,
                name,
                type,
                computed,
                value,
                valueNumeric,
                valueDate,
                valueBoolean,
                valueBlobFile,
                valueClobFile,
                encoding,
                valueComputed,
                valueSequenceNext,
                valueSequenceCurrent,
                defaultValue,
                defaultValueNumeric,
                defaultValueDate,
                defaultValueBoolean,
                defaultValueComputed,
                defaultValueSequenceNext,
                defaultValueConstraintName,
                autoIncrement,
                generationType,
                defaultOnNull,
                startWith,
                incrementBy,
                remarks,
                afterColumn,
                beforeColumn,
                position), constraints
    }

    void column(Map ǃ,
                String name,
                String type,
                Boolean computed=null,
                String value=null,
                Number valueNumeric=null,
                Date valueDate=null,
                Boolean valueBoolean=null,
                String valueBlobFile=null,
                String valueClobFile=null,
                String encoding=null,
                DatabaseFunction valueComputed=null,
                SequenceNextValueFunction valueSequenceNext=null,
                SequenceCurrentValueFunction valueSequenceCurrent=null,
                String defaultValue=null,
                Number defaultValueNumeric=null,
                Date defaultValueDate=null,
                Boolean defaultValueBoolean=null,
                DatabaseFunction defaultValueComputed=null,
                SequenceNextValueFunction defaultValueSequenceNext=null,
                String defaultValueConstraintName=null,
                Boolean autoIncrement=null,
                String generationType=null,
                Boolean defaultOnNull=null,
                BigInteger startWith=null,
                BigInteger incrementBy=null,
                String remarks=null,
                String afterColumn=null,
                String beforeColumn=null,
                Integer position=null) {
        column argsToMap(ǃ,
                name,
                type,
                computed,
                value,
                valueNumeric,
                valueDate,
                valueBoolean,
                valueBlobFile,
                valueClobFile,
                encoding,
                valueComputed,
                valueSequenceNext,
                valueSequenceCurrent,
                defaultValue,
                defaultValueNumeric,
                defaultValueDate,
                defaultValueBoolean,
                defaultValueComputed,
                defaultValueSequenceNext,
                defaultValueConstraintName,
                autoIncrement,
                generationType,
                defaultOnNull,
                startWith,
                incrementBy,
                remarks,
                afterColumn,
                beforeColumn,
                position)
    }

    void column(String name,
                String type,
                Boolean computed=null,
                String value=null,
                Number valueNumeric=null,
                Date valueDate=null,
                Boolean valueBoolean=null,
                String valueBlobFile=null,
                String valueClobFile=null,
                String encoding=null,
                DatabaseFunction valueComputed=null,
                SequenceNextValueFunction valueSequenceNext=null,
                SequenceCurrentValueFunction valueSequenceCurrent=null,
                String defaultValue=null,
                Number defaultValueNumeric=null,
                Date defaultValueDate=null,
                Boolean defaultValueBoolean=null,
                DatabaseFunction defaultValueComputed=null,
                SequenceNextValueFunction defaultValueSequenceNext=null,
                String defaultValueConstraintName=null,
                Boolean autoIncrement=null,
                String generationType=null,
                Boolean defaultOnNull=null,
                BigInteger startWith=null,
                BigInteger incrementBy=null,
                String remarks=null,
                String afterColumn=null,
                String beforeColumn=null,
                Integer position=null,
                @DelegatesTo(value = ConstraintDelegate, strategy = DELEGATE_ONLY) Closure constraints){
        column argsToMap(name,
                type,
                computed,
                value,
                valueNumeric,
                valueDate,
                valueBoolean,
                valueBlobFile,
                valueClobFile,
                encoding,
                valueComputed,
                valueSequenceNext,
                valueSequenceCurrent,
                defaultValue,
                defaultValueNumeric,
                defaultValueDate,
                defaultValueBoolean,
                defaultValueComputed,
                defaultValueSequenceNext,
                defaultValueConstraintName,
                autoIncrement,
                generationType,
                defaultOnNull,
                startWith,
                incrementBy,
                remarks,
                afterColumn,
                beforeColumn,
                position), constraints
    }

    void column(String name,
                String type,
                Boolean computed=null,
                String value=null,
                Number valueNumeric=null,
                Date valueDate=null,
                Boolean valueBoolean=null,
                String valueBlobFile=null,
                String valueClobFile=null,
                String encoding=null,
                DatabaseFunction valueComputed=null,
                SequenceNextValueFunction valueSequenceNext=null,
                SequenceCurrentValueFunction valueSequenceCurrent=null,
                String defaultValue=null,
                Number defaultValueNumeric=null,
                Date defaultValueDate=null,
                Boolean defaultValueBoolean=null,
                DatabaseFunction defaultValueComputed=null,
                SequenceNextValueFunction defaultValueSequenceNext=null,
                String defaultValueConstraintName=null,
                Boolean autoIncrement=null,
                String generationType=null,
                Boolean defaultOnNull=null,
                BigInteger startWith=null,
                BigInteger incrementBy=null,
                String remarks=null,
                String afterColumn=null,
                String beforeColumn=null,
                Integer position=null
                ){
        column argsToMap(name,
                type,
                computed,
                value,
                valueNumeric,
                valueDate,
                valueBoolean,
                valueBlobFile,
                valueClobFile,
                encoding,
                valueComputed,
                valueSequenceNext,
                valueSequenceCurrent,
                defaultValue,
                defaultValueNumeric,
                defaultValueDate,
                defaultValueBoolean,
                defaultValueComputed,
                defaultValueSequenceNext,
                defaultValueConstraintName,
                autoIncrement,
                generationType,
                defaultOnNull,
                startWith,
                incrementBy,
                remarks,
                afterColumn,
                beforeColumn,
                position)
    }
}

@CompileStatic
class InsertDelegate extends ColumnDelegate<ColumnConfig> { // DataColumn in XSD
    InsertDelegate(ChangeSetDelegate changeSet, ChangeWithColumns change) {
        super(changeSet, change)
    }

    void column( Map ǃ,
                 String name,
                 String value=null,
                 Number valueNumeric=null,
                 Date valueDate=null,
                 Boolean valueBoolean=null,
                 String valueBlobFile=null,
                 String valueClobFile=null,
                 String encoding=null,
                 DatabaseFunction valueComputed=null,
                 SequenceNextValueFunction valueSequenceNext=null,
                 SequenceCurrentValueFunction valueSequenceCurrent=null) {
        column argsToMap( ǃ,
                name,
                value,
                valueNumeric,
                valueDate,
                valueBoolean,
                valueBlobFile,
                valueClobFile,
                encoding,
                valueComputed,
                valueSequenceNext,
                valueSequenceCurrent)
    }

    void column( String name,
                 String value=null,
                 Number valueNumeric=null,
                 Date valueDate=null,
                 Boolean valueBoolean=null,
                 String valueBlobFile=null,
                 String valueClobFile=null,
                 String encoding=null,
                 DatabaseFunction valueComputed=null,
                 SequenceNextValueFunction valueSequenceNext=null,
                 SequenceCurrentValueFunction valueSequenceCurrent=null) {
        column argsToMap(name,
                value,
                valueNumeric,
                valueDate,
                valueBoolean,
                valueBlobFile,
                valueClobFile,
                encoding,
                valueComputed,
                valueSequenceNext,
                valueSequenceCurrent)
    }
}


//@CompileStatic // @SelfType cannot handle generics
@SelfType(ChangeDelegate)
trait WhereDelegate {
/**
     * Process the where clause for the closure and add it to the change.  If the change doesn't
     * support where clauses, we'll get a ChangeLogParseException.
     * @param whereClause the where clause to use.
     */
    def where(String whereClause) {
        whereClause = expandExpressions(whereClause)
        // If we have a where clause, try to set it in the change.
        setProp(change, 'where', whereClause)
    }

/**
     * Process the whereParams clause for the closure and add the parameters to the change.  If the
     * change doesn't support whereParams, we'll get a ChangeLogParseException.
     * @param params the nested closure with the parameters themselves.
     */
    def whereParams(@DelegatesTo (value = WhereParamsDelegate, strategy = DELEGATE_ONLY) Closure params) {
        def whereParamsDelegate = new WhereParamsDelegate(databaseChangeLog: databaseChangeLog,
                changeSetId: changeId,
                changeName: (change as LiquibaseSerializable).serializedObjectName,
                change: change)
        callOn(whereParamsDelegate, params)
    }

/**
     * Groovy calls methodMissing when it can't find a matching method to call.  We use it to tell
     * the user which changeSet had the invalid element.
     * @param name the name of the method Groovy wanted to call.
     * @param args the original arguments to that method.

    @PackageScope def methodMissing(String name, args) {
        error UnrecognizedElement(name)
    }  */
}
