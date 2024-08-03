// This is a root changelog that can be loaded as a classpath resource to see if includeAll works
// when loading from a classpath.  This change log is our happy path.
databaseChangeLog {
    preConditions {
        dbms(type: 'mysql')
    }
    // Remember, maxDepth is inclusive
    includeAllSql(path: 'sql', relativeToChangelogFile: true, maxDepth: 0)
    changeSet(author: 'ssaliman', id: 'root-change-set') {
        addColumn(tableName: 'monkey') {
            column(name: 'emotion', type: 'varchar(50)')
        }
    }
}

