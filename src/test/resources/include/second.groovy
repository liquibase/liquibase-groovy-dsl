// This file should be included second
databaseChangeLog {
    changeSet(author: 'ssaliman', id: 'included-change-set-2') {
        addColumn(tableName: 'monkey') {
            column(name: 'emotion', type: 'varchar(30)')
        }
    }
}

