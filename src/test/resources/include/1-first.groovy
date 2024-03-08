// This file should be included first, and needs an ID that matches the constants in 
// DatabaseChangeLogDelegateIncludeAllTests.groovy
databaseChangeLog {
    preConditions {
        runningAs(username: 'ssaliman')
    }

    changeSet(author: 'ssaliman', id: 'first-included-change-set') {
        renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
    }
}

