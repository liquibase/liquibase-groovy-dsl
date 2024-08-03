package org.liquibase.groovy.delegate

import liquibase.change.ColumnConfig
import liquibase.exception.ChangeLogParseException
import liquibase.util.PatchedObjectUtil

/**
 * This class is a delegate for "whereParams" that can be nested in columnar changes like the
 * {@code createTable} change.  It is given a change to which it adds the params it processes.
 *
 * @author Steven C. Saliman
 */
class WhereParamsDelegate {
    def databaseChangeLog
    def changeSetId = '<unknown>' // used for error messages
    def changeName = '<unknown>' // used for error messages
    def change // the change being modified.

    /**
     * Process one "param" from a "whereParams" closure, and add it to the change.  We'll get an
     * exception if whereParams are not supported by the change. which we'll rethrow as a parse
     * exception to tell the user that columns are not allowed in that change.
     * @param params the parameters for the the "param"
     */
    def param(Map params) {
        def columnConfig = new ColumnConfig()
        params.each { key, value ->
            try {
                PatchedObjectUtil.setProperty(columnConfig, key, DelegateUtil.expandExpressions(value, databaseChangeLog))
            } catch (RuntimeException e) {
                // Rethrow as an ChangeLogParseException with a more helpful message than you'll get
                // from the Liquibase helper.
                throw new ChangeLogParseException("ChangeSet '${changeSetId}': '${key}' is not a valid whereParams attribute for '${changeName}' changes.", e)
            }
        }

        // try to add the columnConfig to the whereParams of the change
        try {
            change.addWhereParam columnConfig
        } catch (MissingMethodException e) {
            throw new ChangeLogParseException("ChangeSet '${changeSetId}': whereParams are not allowed in '${changeName}' changes.", e)
        }
    }
}

