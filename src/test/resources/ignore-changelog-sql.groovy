// This is a root changelog that can be loaded as a classpath resource to see if includeAll works
// when we load the changelog from the classpath, have an invalid includeAll, and want to ignore
// errors.
databaseChangeLog {
    preConditions {
        dbms(type: 'mysql')
    }
    includeAllSql(path: 'no-such-dir', relativeToChangelogFile: true,
            errorIfMissingOrEmpty: false)
    changeSet(author: 'ssaliman', id: 'root-change-set') {
        addColumn(tableName: 'monkey') {
            column(name: 'emotion', type: 'varchar(50)')
        }
    }
}

