// This file is named alphabetically, but it's directory is between two filenames in the parent.
// This tests the default sorting, which considers the full pathname, resulting in this file being
// included 3rd because of the subdirectory.  It needs an ID that matches the constants in
// DatabaseChangeLogDelegateIncludeAllTests.groovy
databaseChangeLog {
    changeSet(author: 'ssaliman', id: 'third-included-change-set') {
        addColumn(tableName: 'monkey') {
            column(name: 'location', type: 'varchar(30)')
        }
    }
}

