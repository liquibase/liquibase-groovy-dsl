package org.liquibase.groovy.delegate

import liquibase.change.core.*
import liquibase.parser.groovy.exception.*
import spock.lang.*
import static ChangeSetTests.*
import static org.liquibase.groovy.helper.constants.*
import static org.liquibase.groovy.helper.util.*

/**
 * @author Gyula Bibernath
 */
class ChangeSetSpec extends Specification {
    /** StructuralRefactoringTests **/
    /**
     * Test adding a column with a full set of attributes, and only one column, which does not have
     * any constraints.  We don't worry about the contents of the column itself, as we do that when
     * we test the ColumnDelegate.
     */
    void "addColumn with '#type' arguments"() {
        AddColumnChange ch = verify(expPropsTableSchemaCatalogName, AddColumnChange, cl)

        expect:
        ch.columns.size() == 2
        ch.columns.eachWithIndex { c, i -> assertPropsSet(expAddColumns[i], c) }

        where:
        type         | cl
        'named'      | { addColumn expPropsTableSchemaCatalogName, addColumns }
        'positional' | { addColumn tableName, schemaName, catalogName, addColumns }
        'mixed'      | { addColumn tableName, catalogName: catalogName, schemaName, addColumns }
    }

    static final expPropsCreateTable = expPropsTableSchemaCatalogName + [
            remarks    : remarks,
            ifNotExists: false,
            tablespace : tablespace,
            //rowDependencies: true, // since 4.29
            tableType  : 'rhesus']

    static final expAddColumns = [
            [name: columnName, type: intType],
            [name: column2Name, type: dataType]
    ]

    static final Closure addColumns = {
        column columnName, intType
        column column2Name, dataType
    }

    /** Test parsing a createTable change with all supported attributes and columns. */
    void "createTable with '#type' arguments"() {
        CreateTableChange ch = verify(expPropsCreateTable, CreateTableChange, cl)

        expect:
        ch.columns.size() == 2
        ch.columns.eachWithIndex { c, i -> assertPropsSet(expAddColumns[i], c) }

        where:
        type         | cl
        'named'      | { createTable expPropsCreateTable, addColumns }
        'positional' | { createTable tableName, it.ifNotExists, schemaName, catalogName, tablespace, it.tableType, remarks, addColumns }
        'mixed'      | { createTable tableName, it.ifNotExists, schemaName, catalogName, tablespace, remarks: remarks, it.tableType, addColumns }
    }


    static final expPropsCreateViewPath = expPropsViewSchemaCatalogName + [
            remarks                : 'monkey see, monkey do',
            replaceIfExists        : false,
            fullDefinition         : false,
            path                   : 'monkey_view.sql',
            encoding               : utf8,
            relativeToChangelogFile: true]

    /** Test parsing a createView change with all supported attributes, but no closure  */
    void "createView from path with '#type' arguments"() {
        CreateViewChange ch = verify(expPropsCreateViewPath, CreateViewChange, cl)
        expect:
        ch.selectQuery == null

        where:
        type         | cl
        'named'      | { createView(expPropsCreateViewPath) }
        'positional' | { createView(viewName, it.path, it.replaceIfExists, it.fullDefinition, true, it.remarks, utf8, schemaName, catalogName) }
        'mixed'      | { createView(viewName, it.path, it.replaceIfExists, it.fullDefinition, catalogName: catalogName, it.relativeToChangelogFile, it.remarks, utf8, schemaName) }
    }

    static final expPropsCreateView = expPropsViewSchemaCatalogName + [
       remarks                : 'monkey see, monkey do',
       replaceIfExists        : false,
       fullDefinition         : false ]

    /** Test parsing a createView change with all supported attributes, with closure  */
    void "createView with closure with '#type' arguments"() {
        CreateViewChange ch = verify(expPropsCreateView, CreateViewChange, cl)
        expect:
        ch.selectQuery == sqlSelect
        ch.path == null
        where:
        type         | cl
        'named'      | { createView expPropsCreateView, {sqlSelect} }
        'positional' | { createView(viewName, it.replaceIfExists, it.fullDefinition, it.remarks,  schemaName, catalogName) {sqlSelect} }
        'mixed'      | { createView(viewName, it.replaceIfExists, it.fullDefinition, catalogName: catalogName, it.remarks, schemaName) {sqlSelect} }
    }

    /** Test parsing a dropColumn change without a closure. This is the use case when we put the
     * column name in the columnName attribute instead of the closure
     */
    void "dropColumn single column with '#type' arguments"() {
        DropColumnChange ch = verify(expPropsColumnTableSchemaCatalogName, DropColumnChange, cl)
        expect:
        ch.columns.size() == 0
        where:
        type         | cl
        'named'      | { dropColumn expPropsColumnTableSchemaCatalogName }
        'positional' | { dropColumn columnName, tableName, schemaName, catalogName }
        'mixed'      | { dropColumn columnName, tableName, catalogName: catalogName, schemaName }
    }

    /** Test parsing a dropColumn change with a closure containing the column names to drop.
     */
    void "dropColumn multiple columns with  '#type' arguments"() {
        DropColumnChange ch = verify(expPropsTableSchemaCatalogName, DropColumnChange, cl)
        expect:
        ch.columns.size() == 2
        columnName == ch.columns[0].name
        column2Name == ch.columns[1].name
        null == ch.columnName
        where:
        type         | cl
        'named'      | {
            dropColumn expPropsTableSchemaCatalogName, {
                column name: columnName
                column name: column2Name
            }
        }
        'positional' | {
            dropColumn tableName, schemaName, catalogName, {
                column columnName
                column column2Name
            }
        }
        'mixed'      | {
            dropColumn tableName, catalogName: catalogName, schemaName, {
                column columnName
                column column2Name
            }
        }
    }

    static final expPropsDropProcedure = expPropsSchemaAndCatalogName + [
            procedureName: procedureName
    ]

    void "dropProcedure with '#type' arguments"() {
        verify(expPropsDropProcedure, DropProcedureChange, cl)
        where:
        type         | cl
        'named'      | { dropProcedure expPropsDropProcedure }
        'positional' | { dropProcedure procedureName, schemaName, catalogName }
        'mixed'      | { dropProcedure procedureName, catalogName: catalogName, schemaName }
    }

    static final expDropTableProps = expPropsTableSchemaCatalogName + [
            cascadeConstraints: true
    ]

    void "dropTable with '#type' arguments"() {
        verify(expDropTableProps, DropTableChange, cl)
        where:
        type         | cl
        'named'      | { dropTable expDropTableProps }
        'positional' | { dropTable tableName, true, schemaName, catalogName }
        'mixed'      | { dropTable tableName, true, catalogName: catalogName, schemaName }
    }

    static final expPropsDropView = expPropsViewSchemaCatalogName + [
        ifExists: true
    ]

    void "dropView with '#type' arguments"() {
        verify(expPropsDropView, DropViewChange, cl)
        where:
        type         | cl
        'named'      | { dropView expPropsDropView }
        'positional' | { dropView viewName, true, schemaName, catalogName }
        'mixed'      | { dropView viewName, true, catalogName: catalogName, schemaName }
    }

    static final expPropsMergeColumns = expPropsTableSchemaCatalogName + [
            column1Name    : columnName,
            column2Name    : column2Name,
            finalColumnName: 'full_name',
            finalColumnType: 'varchar(99)',
            joinString     : ' '  ]

    void "mergeColumns with '#type' arguments"() {
        verify(expPropsMergeColumns, MergeColumnChange, cl)
        where:
        type         | cl
        'named'      | { mergeColumns expPropsMergeColumns }
        'positional' | { mergeColumns columnName, it.joinString, column2Name, it.finalColumnName, it.finalColumnType, tableName, schemaName, catalogName }
        'mixed'      | { mergeColumns columnName, it.joinString, column2Name, it.finalColumnName, it.finalColumnType, tableName, catalogName: catalogName, schemaName }
    }

    static final expPropsModifyDataType = expPropsColumnTableSchemaCatalogName + [
        newDataType: dataType
    ]

    void "modifyDataType with '#type' arguments"() {
        verify(expPropsModifyDataType, ModifyDataTypeChange, cl)
        where:
        type         | cl
        'named'      | { modifyDataType expPropsModifyDataType }
        'positional' | { modifyDataType it.newDataType, columnName, tableName, schemaName, catalogName }
        'mixed'      | { modifyDataType catalogName: catalogName, dataType, columnName, tableName, schemaName }
    }

    static final expPropsRenameColumn = expPropsTableSchemaCatalogName + [
            oldColumnName : columnName,
            newColumnName : column2Name,
            columnDataType: dataType,
            remarks       : remarks ]

    void "renameColumn with '#type' arguments"() {
        verify(expPropsRenameColumn, RenameColumnChange, cl)
        where:
        type         | cl
        'named'      | { renameColumn expPropsRenameColumn }
        'positional' | { renameColumn columnName, column2Name, tableName, schemaName, catalogName, dataType, remarks }
        'mixed'      | { renameColumn remarks: remarks, columnName, column2Name, tableName, schemaName, catalogName, dataType }
    }

    static final expPropsRenameTable = expPropsSchemaAndCatalogName + [
            oldTableName: tableName,
            newTableName: 'win_table'
    ]

    void "renameTable with '#type' arguments"() {
        verify(expPropsRenameTable, RenameTableChange, cl)
        where:
        type         | cl
        'named'      | { renameTable expPropsRenameTable }
        'positional' | { renameTable tableName, it.newTableName, schemaName, catalogName }
        'mixed'      | { renameTable tableName, it.newTableName, catalogName: catalogName, schemaName }
    }

    static final expPropsRenameView = expPropsSchemaAndCatalogName + [
            oldViewName: viewName,
            newViewName: 'win_view'
    ]

    void "renameView with '#type' arguments"() {
        verify(expPropsRenameView, RenameViewChange, cl)
        where:
        type         | cl
        'named'      | { renameView expPropsRenameView }
        'positional' | { renameView viewName, it.newViewName, schemaName, catalogName }
        'mixed'      | { renameView viewName, it.newViewName, catalogName: catalogName, schemaName }
    }

    /***** ReferentialIntegrityRefactoringTests ******/
    static final expPropsAddForeignKeyConstraint = [
            constraintName            : constraintName,
            baseTableCatalogName      : catalogName,
            baseTableSchemaName       : schemaName,
            baseColumnNames           : columnNames,
            baseTableName             : tableName,
            referencedTableCatalogName: 'referenced_catalog',
            referencedTableSchemaName : 'referenced_schema',
            referencedTableName       : 'emotions',
            referencedColumnNames     : 'id',
            deferrable                : true,
            initiallyDeferred         : false,
            onDelete                  : 'RESTRICT',
            onUpdate                  : 'CASCADE',
            validate                  : false
    ]

    void "addForeignKeyConstraint with '#type' arguments"() {
        verify(expPropsAddForeignKeyConstraint, AddForeignKeyConstraintChange, cl)
        where:
        type         | cl
        'named'      | { addForeignKeyConstraint expPropsAddForeignKeyConstraint }
        'positional' | {
            addForeignKeyConstraint tableName, columnNames, constraintName, it.referencedTableName, it.referencedColumnNames,
                    schemaName, catalogName, it.referencedTableSchemaName, it.referencedTableCatalogName, it.deferrable,
                    it.initiallyDeferred, it.deleteCascade, it.onDelete, it.onUpdate, it.referencesUniqueColumn, it.validate
        }
        'mixed'      | {
            addForeignKeyConstraint tableName, columnNames, constraintName, it.referencedTableName, it.referencedColumnNames,
                    schemaName, catalogName, it.referencedTableSchemaName, it.referencedTableCatalogName, it.deferrable,
                    it.initiallyDeferred, it.deleteCascade, it.onDelete, validate: it.validate, it.onUpdate, it.referencesUniqueColumn
        }
    }

    static final expPropsAddPrimaryKey = expPropsTableSchemaCatalogName + [
            constraintName     : constraintName,
            columnNames        : columnNames,
            tablespace         : tablespace,
            clustered          : true,
            forIndexCatalogName: 'index_catalog',
            forIndexSchemaName : 'index_schema',
            forIndexName       : 'pk_monkey_idx',
            validate           : false
    ]

    void "addPrimaryKey with '#type' arguments"() {
        verify(expPropsAddPrimaryKey, AddPrimaryKeyChange, cl)
        where:
        type         | cl
        'named'      | { addPrimaryKey expPropsAddPrimaryKey }
        'positional' | {
            addPrimaryKey columnNames, tableName, schemaName, catalogName, constraintName, tablespace,
                    it.clustered, it.forIndexName, it.forIndexSchemaName, it.forIndexCatalogName, it.validate
        }
        'mixed'      | {
            addPrimaryKey columnNames, tableName, schemaName, catalogName, constraintName, tablespace,
                    it.clustered, it.forIndexName, it.forIndexSchemaName, validate: it.validate, it.forIndexCatalogName
        }
    }
    static final expPropsDropPrimaryKey = expPropsTableSchemaCatalogName + [
            constraintName: constraintName,
            dropIndex     : false
    ]

    void "dropPrimaryKey with '#type' arguments"() {
        verify(expPropsDropPrimaryKey, DropPrimaryKeyChange, cl)
        where:
        type         | cl
        'named'      | { dropPrimaryKey expPropsDropPrimaryKey }
        'positional' | { dropPrimaryKey tableName, constraintName, schemaName, catalogName, it.dropIndex }
        'mixed'      | { dropPrimaryKey tableName, constraintName, dropIndex: it.dropIndex, schemaName, catalogName }
    }

    static final expPropsDropAllForeignKeyConstraints = [
            baseTableCatalogName: catalogName,
            baseTableSchemaName : schemaName,
            baseTableName       : tableName,
    ]

    void "dropAllForeignKeyConstraints with '#type' arguments"() {
        verify(expPropsDropAllForeignKeyConstraints, DropAllForeignKeyConstraintsChange, cl)
        where:
        type         | cl
        'named'      | { dropAllForeignKeyConstraints expPropsDropAllForeignKeyConstraints }
        'positional' | { dropAllForeignKeyConstraints tableName, schemaName, catalogName }
        'mixed'      | { dropAllForeignKeyConstraints tableName, baseTableCatalogName: catalogName, schemaName }
    }

    static final expPropsDropForeignKeyConstraint = expPropsDropAllForeignKeyConstraints + [
            constraintName: constraintName
    ]

    void "dropForeignKeyConstraint with '#type' arguments"() {
        verify(expPropsDropForeignKeyConstraint, DropForeignKeyConstraintChange, cl)
        where:
        type         | cl
        'named'      | { dropForeignKeyConstraint expPropsDropForeignKeyConstraint }
        'positional' | { dropForeignKeyConstraint constraintName, tableName, schemaName, catalogName }
        'mixed'      | { dropForeignKeyConstraint constraintName, tableName, baseTableCatalogName: catalogName, schemaName }
    }

    /**** NonRefactoringTransformationTests ****/
    static final whereClause = "emotion='angry' AND active=true"
    static final expPropsDelete = expPropsTableSchemaCatalogName + [where: whereClause]

    void "delete with where with '#type' arguments"() {
        verify(expPropsDelete, DeleteDataChange, cl)

        where:
        type         | cl
        'mixed'      | { delete tableName, catalogName: catalogName, schemaName, { where whereClause } }
        'named'      | { delete expPropsDelete, { where whereClause } }
        'positional' | { delete tableName, schemaName, catalogName, { where whereClause } }
    }

    void "delete with '#type' arguments"() {
        verify(expPropsTableSchemaCatalogName, DeleteDataChange, cl)
        where:
        type         | cl
        'named'      | { delete expPropsTableSchemaCatalogName }
        'positional' | { delete tableName, schemaName, catalogName }
        'mixed'      | { delete tableName, catalogName: catalogName, schemaName }
    }

    static final expPropsSetColumnRemarks = expPropsColumnTableSchemaCatalogName + [
            remarks         : remarks,
            columnDataType  : dataType,
            columnParentType: 'VIEW'
    ]

    void "setColumnRemarks with '#type' arguments"() {
        verify(expPropsSetColumnRemarks, SetColumnRemarksChange, cl)
        where:
        type         | cl
        'named'      | { setColumnRemarks expPropsSetColumnRemarks }
        'positional' | { setColumnRemarks remarks, columnName, tableName, schemaName, catalogName, dataType, it.columnParentType }
        'mixed'      | { setColumnRemarks remarks, columnName, tableName, schemaName, catalogName, columnParentType: it.columnParentType, dataType }
    }

    static final expPropsSetTableRemarks = expPropsTableSchemaCatalogName + [
            remarks: remarks,
    ]

    void "setTableRemarks with '#type' arguments"() {
        verify(expPropsSetTableRemarks, SetTableRemarksChange, cl)
        where:
        type         | cl
        'named'      | { setTableRemarks expPropsSetTableRemarks }
        'positional' | { setTableRemarks remarks, tableName, schemaName, catalogName }
        'mixed'      | { setTableRemarks remarks, tableName, catalogName:catalogName, schemaName }
    }


    static final expPropsDataColumns = [
            [name: columnName, value: intType],
            [name: column2Name, value: dataType]
    ]

    static final Closure dataColumnsAndWhere = {
        column columnName, intType
        column column2Name, dataType
        where whereClause // TODO add whereParams
    }

    void "update with where '#type' arguments"() {
        UpdateDataChange ch = verify(expPropsTableSchemaCatalogName, UpdateDataChange, cl)

        expect:
        ch.columns.size() == 2
        ch.columns.eachWithIndex { c, i -> assertPropsSet(expPropsDataColumns[i], c) }
        ch.where == whereClause

        where:
        type         | cl
        'named'      | { update expPropsTableSchemaCatalogName, dataColumnsAndWhere }
        'positional' | { update tableName, schemaName, catalogName, dataColumnsAndWhere }
        'mixed'      | { update tableName, catalogName: catalogName, schemaName, dataColumnsAndWhere }
    }

    static final expPropsInsert = expPropsTableSchemaCatalogName + [
            dbms: mysql,
    ]

    static final Closure dataColumns = {
        column columnName, intType
        column column2Name, dataType
    }

    void "insert with '#type' arguments"() {
        InsertDataChange ch = verify(expPropsInsert, InsertDataChange, cl)

        expect:
        ch.columns.size() == 2
        ch.columns.eachWithIndex { c, i -> assertPropsSet(expPropsDataColumns[i], c) }

        where:
        type         | cl
        'named'      | { insert expPropsInsert, dataColumns }
        'positional' | { insert tableName, schemaName, catalogName, mysql, dataColumns }
        'mixed'      | { insert tableName, dbms: mysql, schemaName, catalogName, dataColumns }
    }

    static final expPropsLoadData = expPropsTableSchemaCatalogName + [
            file: file,
            relativeToChangelogFile: true,
            usePreparedStatements: false,
            encoding: utf8,
            separator: ';',
            quotchar: "'",
            commentLineStartsWith: "-"
    ]

    static final expLoadDataColumns = [
            [name: columnName, type: NUMERIC] // TODO change loadData type to LOADDATA_TYPE
            ,[name: column2Name, type: STRING]
    ]

    static final Closure loadDataColumns = {
        column columnName, NUMERIC
        column column2Name, STRING
    }

    void "keep defaults"() {
        expect:
        verify file: file, tableName: tableName, separator:',', quotchar: '"', LoadDataChange, {
            loadData tableName, file
        }
    }

    void "loadData with '#type' arguments"() {
        LoadDataChange ch = verify(expPropsLoadData, LoadDataChange, cl)

        expect:
        ch.columns.size() == 2
        ch.columns.eachWithIndex { c, i -> assertPropsSet(expLoadDataColumns[i], c) }

        where:
        type         | cl
        'named'      | { loadData expPropsLoadData, loadDataColumns }
        'positional' | { loadData tableName, file, it.relativeToChangelogFile, it.encoding, it.separator, it.quotchar, it.commentLineStartsWith, it.usePreparedStatements, schemaName, catalogName, loadDataColumns }
        'mixed'      | { loadData tableName, catalogName: catalogName, file, it.relativeToChangelogFile, it.encoding, it.separator, it.quotchar, it.commentLineStartsWith, it.usePreparedStatements, schemaName, loadDataColumns }
    }

    static final expPropsLoadUpdateData = expPropsLoadData + [
            primaryKey: columnName
            ,onlyUpdate: true
    ]

    void "loadUpdateData with '#type' arguments"() {
        LoadUpdateDataChange ch = verify(expPropsLoadUpdateData, LoadUpdateDataChange, cl)

        expect:
        ch.columns.size() == 2
        ch.columns.eachWithIndex { c, i -> assertPropsSet(expLoadDataColumns[i], c) }

        where:
        type         | cl
        'named'      | { loadUpdateData expPropsLoadUpdateData, loadDataColumns }
        'positional' | { loadUpdateData tableName, it.primaryKey, file, it.relativeToChangelogFile, it.encoding, it.separator, it.quotchar, it.commentLineStartsWith, it.usePreparedStatements, schemaName, catalogName, it.onlyUpdate, loadDataColumns }
        'mixed'      | { loadUpdateData tableName, onlyUpdate:it.onlyUpdate, it.primaryKey, file, it.relativeToChangelogFile, it.encoding, it.separator, it.quotchar, it.commentLineStartsWith, it.usePreparedStatements, schemaName, catalogName, loadDataColumns }
    }

    /**** DataQualityRefactoringTests ****/
    static final expPropsAddAutoIncrement = expPropsColumnTableSchemaCatalogName + [
            columnDataType: dataType,
            startWith: 10,
            incrementBy: 5,
            defaultOnNull: true,
            generationType: 'magic'
    ]

    void "addAutoIncrement with '#type' arguments"() {
        verify(expPropsAddAutoIncrement, AddAutoIncrementChange, cl)

        where:
        type         | cl
        'named'      | { addAutoIncrement expPropsAddAutoIncrement }
        'positional' | { addAutoIncrement columnName, tableName, dataType, it.startWith, it.incrementBy, it.defaultOnNull, it.generationType, schemaName, catalogName}
        'mixed'      | { addAutoIncrement columnName, tableName, dataType, catalogName: catalogName, it.startWith, it.incrementBy, it.defaultOnNull, it.generationType, schemaName}
    }

    static final expPropsDropDefaultValue = expPropsColumnTableSchemaCatalogName + [
            columnDataType: dataType
    ]

    void "dropDefaultValue with '#type' arguments"() {
        verify(expPropsDropDefaultValue, DropDefaultValueChange, cl)

        where:
        type         | cl
        'named'      | { dropDefaultValue expPropsDropDefaultValue }
        'positional' | { dropDefaultValue columnName, tableName, schemaName, catalogName, dataType}
        'mixed'      | { dropDefaultValue columnName, tableName, schemaName, columnDataType: dataType, catalogName}
    }

    static final expPropsAddDefaultValue = expPropsDropDefaultValue + [
            defaultValue: 'extremely',
            defaultValueBoolean: true,
            defaultValueComputed: 'max',
            defaultValueDate: '20101109T130400Z',
            defaultValueNumeric: '2.718281828459045',
            defaultValueSequenceNext: 'sequence',
            defaultValueConstraintName: 'monkey_strength_default'
    ]

    void "addDefaultValue with '#type' arguments"() {
        verify(expPropsAddDefaultValue, AddDefaultValueChange, cl)

        where:
        type         | cl
        'named'      | { addDefaultValue expPropsAddDefaultValue }
        'positional' | { addDefaultValue columnName, tableName, it.defaultValue, it.defaultValueNumeric, it.defaultValueDate, it.defaultValueBoolean, it.defaultValueComputed, it.defaultValueSequenceNext, it.defaultValueConstraintName, dataType, schemaName, catalogName}
        'mixed'      | { addDefaultValue columnName, tableName, catalogName: catalogName, it.defaultValue, it.defaultValueNumeric, it.defaultValueDate, it.defaultValueBoolean, it.defaultValueComputed, it.defaultValueSequenceNext, it.defaultValueConstraintName, dataType, schemaName}
    }

    static final expPropsAddLookupTable =  [
            existingTableCatalogName: catalogName,
            existingTableSchemaName: schemaName,
            existingTableName: tableName,
            existingColumnName: columnName,
            newTableCatalogName: 'new_catalog',
            newTableSchemaName: 'new_schema',
            newTableName: 'monkey_emotion',
            newColumnName: 'emotion_display',
            newColumnDataType: dataType,
            constraintName: constraintName
    ]

    void "addLookupTable with '#type' arguments"() {
        verify(expPropsAddLookupTable, AddLookupTableChange, cl)

        where:
        type         | cl
        'named'      | { addLookupTable expPropsAddLookupTable }
        'positional' | { addLookupTable tableName, columnName, it.newTableName, it.newColumnName, catalogName, schemaName, it.newTableCatalogName, it.newTableSchemaName, dataType, constraintName}
        'mixed'      | { addLookupTable tableName, columnName, it.newTableName, it.newColumnName, catalogName, schemaName, constraintName: constraintName, it.newTableCatalogName, it.newTableSchemaName, dataType}
    }

    static final expPropsDropNotNullConstraint = expPropsColumnTableSchemaCatalogName + [
        columnDataType: dataType,
        constraintName: constraintName
    ]

    void "dropNotNullConstraint with '#type' arguments"() {
        verify(expPropsDropNotNullConstraint, DropNotNullConstraintChange, cl)

        where:
        type         | cl
        'named'      | { dropNotNullConstraint expPropsDropNotNullConstraint }
        'positional' | { dropNotNullConstraint constraintName, tableName, schemaName, catalogName, columnName, dataType }
        'mixed'      | { dropNotNullConstraint constraintName, tableName, schemaName, catalogName, columnDataType: dataType, columnName }
    }

    static final expPropsAddNotNullConstraint = expPropsDropNotNullConstraint + [
            defaultNullValue: 'angry',
            validate: true
    ]

    void "addNotNullConstraint with '#type' arguments"() {
        verify(expPropsAddNotNullConstraint, AddNotNullConstraintChange, cl)

        where:
        type         | cl
        'named'      | { addNotNullConstraint expPropsAddNotNullConstraint }
        'positional' | { addNotNullConstraint columnName, tableName, schemaName, catalogName, it.defaultNullValue, dataType, constraintName, it.validate}
        'mixed'      | { addNotNullConstraint columnName, tableName, schemaName, catalogName, validate: it.validate, it.defaultNullValue, dataType, constraintName}
    }

    static final expPropsAddUniqueConstraint = expPropsTableSchemaCatalogName + [
            tablespace: tablespace,
            columnNames: columnNames,
            constraintName: constraintName,
            deferrable: true,
            initiallyDeferred: false,
            disabled: false,
            forIndexCatalogName: 'index_catalog',
            forIndexSchemaName: 'index_schema',
            forIndexName: 'unique_constraint_idx',
            validate: false,
            clustered: false
    ]

    void "addUniqueConstraint with '#type' arguments"() {
        verify(expPropsAddUniqueConstraint, AddUniqueConstraintChange, cl)

        where:
        type         | cl
        'named'      | { addUniqueConstraint expPropsAddUniqueConstraint }
        'positional' | { addUniqueConstraint columnNames, tableName, schemaName, catalogName, constraintName, tablespace, it.disabled, it.deferrable, it.initiallyDeferred, it.forIndexCatalogName, it.forIndexSchemaName, it.forIndexName, it.clustered, it.validate }
        'mixed'      | { addUniqueConstraint columnNames, tableName, schemaName, catalogName, constraintName, tablespace, validate: it.validate, it.disabled, it.deferrable, it.initiallyDeferred, it.forIndexCatalogName, it.forIndexSchemaName, it.forIndexName, it.clustered }
    }

    static final expPropsDropUniqueConstraint = expPropsTableSchemaCatalogName + [
        constraintName: constraintName,
        uniqueColumns: columnNames
    ]

    void "dropUniqueConstraint with '#type' arguments"() {
        verify(expPropsDropUniqueConstraint, DropUniqueConstraintChange, cl)

        where:
        type         | cl
        'named'      | { dropUniqueConstraint expPropsDropUniqueConstraint }
        'positional' | { dropUniqueConstraint constraintName, tableName, schemaName, catalogName, columnNames }
        'mixed'      | { dropUniqueConstraint constraintName, tableName, schemaName, uniqueColumns:columnNames, catalogName }
    }

    static final expPropsAlterSequence = expPropsSequenceSchemaCatalogName + [
            dataType: dataType,
            incrementBy: 314,
            minValue: 300,
            maxValue: 400,
            ordered: true,
            cacheSize: 10,
            cycle: true,
    ]

    void "alterSequence with '#type' arguments"() {
        verify(expPropsAlterSequence, AlterSequenceChange, cl)

        where:
        type         | cl
        'named'      | { alterSequence expPropsAlterSequence }
        'positional' | { alterSequence sequenceName, it.incrementBy, it.minValue, it.maxValue, it.ordered, it.cacheSize, dataType, it.cycle, schemaName, catalogName }
        'mixed'      | { alterSequence sequenceName, it.incrementBy, it.minValue, it.maxValue, catalogName: catalogName, it.ordered, it.cacheSize, dataType, it.cycle, schemaName }
    }

    static final expPropsCreateSequence = expPropsAlterSequence + [
            startValue: 301
    ]

    void "createSequence with '#type' arguments"() {
        verify(expPropsCreateSequence, CreateSequenceChange, cl)

        where:
        type         | cl
        'named'      | { createSequence expPropsCreateSequence }
        'positional' | { createSequence sequenceName, it.startValue, it.incrementBy, it.minValue, it.maxValue, it.ordered, it.cacheSize, dataType, it.cycle, schemaName, catalogName }
        'mixed'      | { createSequence sequenceName, it.startValue, it.incrementBy, it.minValue, it.maxValue, catalogName: catalogName, it.ordered, it.cacheSize, dataType, it.cycle, schemaName }
    }

    void "dropSequence with '#type' arguments"() {
        verify(expPropsSequenceSchemaCatalogName, DropSequenceChange, cl)

        where:
        type         | cl
        'named'      | { dropSequence expPropsSequenceSchemaCatalogName }
        'positional' | { dropSequence sequenceName, schemaName, catalogName }
        'mixed'      | { dropSequence sequenceName, catalogName: catalogName, schemaName }
    }


    static final expPropsRenameSequence = expPropsSchemaAndCatalogName + [
            oldSequenceName: sequenceName,
            newSequenceName: 'new_sequence'
    ]

    void "renameSequence with '#type' arguments"() {
        verify(expPropsRenameSequence, RenameSequenceChange, cl)

        where:
        type         | cl
        'named'      | { renameSequence expPropsRenameSequence }
        'positional' | { renameSequence sequenceName, it.newSequenceName, schemaName, catalogName }
        'mixed'      | { renameSequence sequenceName, it.newSequenceName, catalogName: catalogName, schemaName }
    }

    /**** ArchitecturalRefactoringTests ****/

    static final expPropsCreateIndex = expPropsIndexTableSchemaCatalogName + [
            tablespace: tablespace,
            unique: true,
            clustered: false,
            associatedWith: 'foreignKey'
    ]

    static final expCreateIndexColumns = [
            [name: columnName]
            ,[name: column2Name] //TODO , included: true
    ]

    static final Closure createIndexColumns = {
        column columnName
        column column2Name //TODO     , true
    }

    void "createIndex with '#type' arguments"() {
        CreateIndexChange ch = verify(expPropsCreateIndex, CreateIndexChange, cl)

        expect:
        expCreateIndexColumns.size() == ch.columns.size()
        ch.columns.eachWithIndex { c, i -> assertPropsSet(expCreateIndexColumns[i], c) }

        where:
        type         | cl
        'named'      | { createIndex expPropsCreateIndex, createIndexColumns }
        'positional' | { createIndex indexName, tableName, schemaName, catalogName, it.associatedWith, it.unique, it.clustered, tablespace, createIndexColumns }
        'mixed'      | { createIndex indexName, tableName, schemaName, catalogName, it.associatedWith, it.unique, tablespace:tablespace, it.clustered, createIndexColumns }
    }

    static final expPropsDropIndex = expPropsIndexTableSchemaCatalogName + [
            associatedWith: 'foreignKey'
    ]

    void "dropIndex with '#type' arguments"() {
        verify(expPropsDropIndex, DropIndexChange, cl)

        where:
        type         | cl
        'named'      | { dropIndex expPropsDropIndex }
        'positional' | { dropIndex indexName, tableName, schemaName, catalogName, it.associatedWith }
        'mixed'      | { dropIndex indexName, tableName, associatedWith:it.associatedWith, schemaName, catalogName }
    }

    static final expPropsSql = [
            stripComments:true,
            splitStatements:true,
            endDelimiter: ',',
            dbms: mysql
            ]

    void "sql with '#type' arguments"() {
        verify(expected, RawSQLChange, cl).sql == sqlSelect

        where:
        type                | expected    | cl
        'closure+named'     | expPropsSql | { sql expPropsSql, {sqlSelect} } // Map,,,Closure
        'closure+positional'| expPropsSql | { sql it.stripComments, it.dbms, it.splitStatements, it.endDelimiter, {sqlSelect} } // ,,,Closure
        'closure+mixed'     | expPropsSql | { sql it.stripComments, it.dbms, endDelimiter:it.endDelimiter, it.splitStatements, {sqlSelect}} // Map,,,Closure
        'named'       | [stripComments:true] | { sql sqlSelect, stripComments:true } // Map, String,,,,,
        'positional'  | expPropsSql | { sql sqlSelect, it.stripComments, it.dbms, it.splitStatements, it.endDelimiter } // String,,,,,
        'mixed'       | expPropsSql | { sql sqlSelect, it.stripComments, it.dbms, endDelimiter:it.endDelimiter, it.splitStatements } // Map, String,,,,,

    }

    static final expPropsSqlFile = expPropsSql + [
            path: file,
            relativeToChangelogFile: true,
            encoding: 'ASCII'
    ]

    void "sqlFile with '#type' arguments"() {
        verify(expPropsSqlFile, SQLFileChange, cl)

        where:
        type         | cl
        'named'      | { sqlFile expPropsSqlFile }
        'positional' | { sqlFile file, it.relativeToChangelogFile, it.stripComments, it.dbms, it.splitStatements, it.endDelimiter, it.encoding }
        'mixed'      | { sqlFile file, it.relativeToChangelogFile, it.stripComments, it.dbms, it.splitStatements, it.endDelimiter, encoding: it.encoding }
    }

    static final mac = 'mac'

    static final expPropsExecuteCommand = [
        executable: 'ls',
        timeout: '10s'
        ]

    def "executeCommand with '#type' arguments"() {
        verify(expPropsExecuteCommand + [os: [mac]], ExecuteShellCommandChange, cl)

        where:
        type         | cl
        'named'      | { executeCommand expPropsExecuteCommand + [os: mac] }
        'positional' | { executeCommand it.executable, mac, it.timeout }
        'mixed'      | { executeCommand it.executable, timeout: it.timeout, mac }
    }

    static final expPropsCreateProcedure = expPropsSchemaAndCatalogName + [
       procedureName          : procedureName,
       replaceIfExists        : false,
       dbms: 'db2'
    ]

    static final expPropsCreateProcedurePath = expPropsCreateProcedure + [
       path                   : 'monkey_view.sql',
       encoding               : utf8,
       relativeToChangelogFile: true
    ]

    /** Test parsing a createProcedure change with all supported attributes, but no closure  */
    void "createProcedure from path with '#type' arguments"() {
        CreateProcedureChange ch = verify(expPropsCreateProcedurePath, CreateProcedureChange, cl)
        expect:
        ch.procedureText == null

        where:
        type         | cl
        'named'      | { createProcedure expPropsCreateProcedurePath }
        'positional' | { createProcedure(it.path, procedureName, true, it.replaceIfExists, it.dbms, utf8, schemaName, catalogName) }
        'mixed'      | { createProcedure(it.path, procedureName, it.relativeToChangelogFile, it.replaceIfExists, it.dbms, catalogName: catalogName, utf8, schemaName) }
    }

    /** Test parsing a createProcedure change with all supported attributes, with closure  */
    void "createProcedure with closure and '#type' arguments"() {
        CreateProcedureChange ch = verify(expPropsCreateProcedure, CreateProcedureChange, cl)
        expect:
        ch.procedureText == sqlSelect
        ch.path == null
        where:
        type         | cl
        'named'      | { createProcedure expPropsCreateProcedure, {sqlSelect} }
        'positional' | { createProcedure(procedureName, it.replaceIfExists, it.dbms, schemaName, catalogName) {sqlSelect} }
        'mixed'      | { createProcedure(procedureName, it.replaceIfExists, it.dbms, catalogName: catalogName, schemaName) {sqlSelect} }
    }

//    void "error #expectedErr" () {
//        when:
//        buildChanges (input)
//        then:
//        thrown(expectedErr)
//        where:
//        expectedErr     | input
//        MissingClosure  | {sql dbms: 'd'}
//    }

    static final String target = 'STDOUT'
    static final String outMsg = 'some helpful message'

    static final expPropsOutput = [
       message: outMsg,
       target : target
    ]

    /** Test an output change with all supported properties */
    void "output with '#type' arguments"() {
        verify(expPropsOutput, OutputChange, cl)

        where:
        type         | cl
//        'child'      | { output {outMsg} } //TODO DOES NOT WORK!
        'named'      | { output it }
        'positional' | { output it.message, it.target}
        'mixed'      | { output target: it.target, it.message}
        'mixed+child'| { output target: it.target, {outMsg} }

    }
    // TODO check all known changes have a method in the methoddDefs
}
