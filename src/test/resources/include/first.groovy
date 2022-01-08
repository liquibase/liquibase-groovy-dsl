//  This file should be included first.
databaseChangeLog {
    preConditions {
        runningAs(username: 'ssaliman')
    }

    changeSet(author: 'ssaliman', id: 'included-change-set-1') {
        renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
    }
}

