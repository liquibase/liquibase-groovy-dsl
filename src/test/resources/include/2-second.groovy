// This file should be included second, and needs an ID that matches the constants in 
// DatabaseChangeLogDelegateIncludeAllTests.groovy
databaseChangeLog {
    changeSet(author: 'ssaliman', id: 'second-included-change-set') {
        addColumn(tableName: 'monkey') {
            column(name: 'emotion', type: 'varchar(30)')
        }
    }
}

