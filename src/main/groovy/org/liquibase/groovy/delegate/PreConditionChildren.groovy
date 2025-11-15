package org.liquibase.groovy.delegate
/* Generated @ Thu Oct 02 21:42:02 CEST 2025 on M600 */
import groovy.transform.CompileStatic
import groovy.transform.SelfType
import liquibase.database.ObjectQuotingStrategy
import static org.liquibase.groovy.delegate.PreconditionDelegate.Tag

@CompileStatic
@SelfType(PreconditionDelegate)
trait PreConditionChildren {

	/** Check if the database the changelog executed on matches to {@code type}
	  @param type Expected database type */
	void dbms( String type) {
		addPrecondition chkMap(Tag.dbms) ('type',type)
	}

	/** Check if the name of the database user executing the change log matches the {@code username}
	  @param username The name of the database user expected executing the change log */
	void runningAs( String username) {
		addPrecondition chkMap(Tag.runningAs) ('username',username)
	}

	/** Check if the changeset identified by {@code changeLogFile} :: {@code id} :: {@code author}
		has already been executed in a previous deployment
	  @param changeLogFile File name of the changelog. If not set the actual (logical) file name is used */
	void changeSetExecuted( String id, String author, String changeLogFile=null) {
		addPrecondition chkMap(Tag.changeSetExecuted) ('id',id) ('author',author) ('changeLogFile',changeLogFile)
	}

	/** Check if the changeset identified by {@code changeLogFile} :: {@code id} :: {@code author}
		has already been executed in a previous deployment
	  @param changeLogFile File name of the changelog. If not set the actual (logical) file name is used */
	void changeSetExecuted( Map ǃ, String id, String author, String changeLogFile=null) {
		addPrecondition chkMap(Tag.changeSetExecuted,ǃ) ('id',id) ('author',author) ('changeLogFile',changeLogFile)
	}

	/** Check if the changeset identified by {@code changeLogFile} :: {@code id} :: {@code author}
		has already been executed in a previous deployment
	  @param changeLogFile File name of the changelog. If not set the actual (logical) file name is used */
	void changeSetExecuted(Map params) {
		addPrecondition Tag.changeSetExecuted, params
	}

	/** Checks if table with {@code tableName} exists in the database */
	void tableExists( String tableName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.tableExists) ('tableName',tableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/** Checks if table with {@code tableName} exists in the database */
	void tableExists( Map ǃ, String tableName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.tableExists,ǃ) ('tableName',tableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/** Checks if table with {@code tableName} exists in the database */
	void tableExists(Map params) {
		addPrecondition Tag.tableExists, params
	}

	/** Check if column {@code columnName} exists in the table {@code tableName} */
	void columnExists( String columnName, String tableName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.columnExists) ('columnName',columnName) ('tableName',tableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/** Check if column {@code columnName} exists in the table {@code tableName} */
	void columnExists( Map ǃ, String columnName, String tableName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.columnExists,ǃ) ('columnName',columnName) ('tableName',tableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/** Check if column {@code columnName} exists in the table {@code tableName} */
	void columnExists(Map params) {
		addPrecondition Tag.columnExists, params
	}

	/**  */
	void sequenceExists( String sequenceName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.sequenceExists) ('sequenceName',sequenceName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/**  */
	void sequenceExists( Map ǃ, String sequenceName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.sequenceExists,ǃ) ('sequenceName',sequenceName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/**  */
	void sequenceExists(Map params) {
		addPrecondition Tag.sequenceExists, params
	}

	/**  */
	void foreignKeyConstraintExists( String foreignKeyName, String foreignKeyTableName=null, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.foreignKeyConstraintExists) ('foreignKeyName',foreignKeyName) ('foreignKeyTableName',foreignKeyTableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/**  */
	void foreignKeyConstraintExists( Map ǃ, String foreignKeyName, String foreignKeyTableName=null, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.foreignKeyConstraintExists,ǃ) ('foreignKeyName',foreignKeyName) ('foreignKeyTableName',foreignKeyTableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/**  */
	void foreignKeyConstraintExists(Map params) {
		addPrecondition Tag.foreignKeyConstraintExists, params
	}

	/** Check if the index referenced by {@code indexName} or {@code tableName} and {@code columnNames}
		Either {@code indexName} or {@code tableName} and {@code columnNames} is required */
	void indexExists( String indexName=null, String tableName=null, String columnNames=null, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.indexExists) ('indexName',indexName) ('tableName',tableName) ('columnNames',columnNames) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/** Check if the index referenced by {@code indexName} or {@code tableName} and {@code columnNames}
		Either {@code indexName} or {@code tableName} and {@code columnNames} is required */
	void indexExists( Map ǃ, String indexName=null, String tableName=null, String columnNames=null, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.indexExists,ǃ) ('indexName',indexName) ('tableName',tableName) ('columnNames',columnNames) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/** Check if unique key exists on table defined by {@code tableName.} Either {@code primaryKeyName} or {@code tableName} required */
	void uniqueConstraintExists( String tableName, String constraintName=null, String columnNames=null, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.uniqueConstraintExists) ('tableName',tableName) ('constraintName',constraintName) ('columnNames',columnNames) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/** Check if unique key exists on table defined by {@code tableName.} Either {@code primaryKeyName} or {@code tableName} required */
	void uniqueConstraintExists( Map ǃ, String tableName, String constraintName=null, String columnNames=null, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.uniqueConstraintExists,ǃ) ('tableName',tableName) ('constraintName',constraintName) ('columnNames',columnNames) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/** Check if unique key exists on table defined by {@code tableName.} Either {@code primaryKeyName} or {@code tableName} required */
	void uniqueConstraintExists(Map params) {
		addPrecondition Tag.uniqueConstraintExists, params
	}

	/** Check if primary key exists. Either {@code primaryKeyName} or {@code tableName} required depending on the database type */
	void primaryKeyExists( String primaryKeyName=null, String tableName=null, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.primaryKeyExists) ('primaryKeyName',primaryKeyName) ('tableName',tableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/** Check if primary key exists. Either {@code primaryKeyName} or {@code tableName} required depending on the database type */
	void primaryKeyExists( Map ǃ, String primaryKeyName=null, String tableName=null, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.primaryKeyExists,ǃ) ('primaryKeyName',primaryKeyName) ('tableName',tableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/**  */
	void viewExists( String viewName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.viewExists) ('viewName',viewName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/**  */
	void viewExists( Map ǃ, String viewName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.viewExists,ǃ) ('viewName',viewName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/**  */
	void viewExists(Map params) {
		addPrecondition Tag.viewExists, params
	}

	/**  */
	void tableIsEmpty( String tableName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.tableIsEmpty) ('tableName',tableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/**  */
	void tableIsEmpty( Map ǃ, String tableName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.tableIsEmpty,ǃ) ('tableName',tableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/**  */
	void tableIsEmpty(Map params) {
		addPrecondition Tag.tableIsEmpty, params
	}

	/**  */
	void rowCount( Long expectedRows, String tableName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.rowCount) ('expectedRows',expectedRows) ('tableName',tableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/**  */
	void rowCount( Map ǃ, Long expectedRows, String tableName, String schemaName=null, String catalogName=null) {
		addPrecondition chkMap(Tag.rowCount,ǃ) ('expectedRows',expectedRows) ('tableName',tableName) ('schemaName',schemaName) ('catalogName',catalogName)
	}

	/**  */
	void rowCount(Map params) {
		addPrecondition Tag.rowCount, params
	}

	/** Check if the changeLog property with the name in {@code property} is set to {@code value.}
		If {@code value} is not defined checks only if the property is defined at all */
	void changeLogPropertyDefined( String property, String value=null) {
		addPrecondition chkMap(Tag.changeLogPropertyDefined) ('property',property) ('value',value)
	}

	/** Precondition that checks if objectQuotingStrategy set for the changelog matches to {@code strategy}
	  @param strategy ObjectQuotingStrategy expected to be set for the changelog */
	void expectedQuotingStrategy( ObjectQuotingStrategy strategy) {
		addPrecondition chkMap(Tag.expectedQuotingStrategy) ('strategy',strategy)
	}

}
