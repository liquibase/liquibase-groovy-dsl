package org.liquibase.groovy.helper

import groovy.transform.CompileStatic

@CompileStatic
interface constants {
    String tableName = 'monkey'
    String columnName = 'emotion'
    String schemaName = 'schema'
    String catalogName = 'cat'
    String viewName = 'monkey_view'
    String sequenceName = 'seq_next_monkey'
    String primaryKeyName = 'pk_monkey'
    String mysql = 'mysql'
    String constraintName = 'uk_monkey_name'
    String columnNames = 'col1,col2'
    String utf8 = 'UTF-8'
    String tablespace = 'tablespace'
    String sqlSelect = "SELECT * FROM monkey WHERE state='angry'"
    String indexName = 'ix_monkey'
    Map expPropsSchemaAndCatalogName = [schemaName   : schemaName
                                        , catalogName: catalogName]
    Map expPropsSequenceSchemaCatalogName = expPropsSchemaAndCatalogName + [sequenceName: sequenceName]
    Map expPropsViewSchemaCatalogName = expPropsSchemaAndCatalogName + [viewName: viewName]
    Map expPropsTableSchemaCatalogName = expPropsSchemaAndCatalogName + [tableName: tableName]
    Map expPropsIndexTableSchemaCatalogName = expPropsSchemaAndCatalogName + [indexName: indexName]
    Map expPropsColumnTableSchemaCatalogName = expPropsTableSchemaCatalogName + [columnName: columnName ]
    String column2Name = 'col2'
    String procedureName = 'procedureName'
    String dataType = 'varchar(9001)'
    String remarks = 'remarks'
    String intType = 'int'
    String file = 'data.csv'
    String STRING = 'STRING'
    String NUMERIC = 'NUMERIC'
    String fail = 'fail'
    String err = 'err'
    String strComment = 'comment'
    String tlberglund = 'tlberglund'
}
