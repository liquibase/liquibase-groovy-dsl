// This is a root changelog that can be loaded as a classpath resource to see if includeAll works
// when loading from a classpath.  This change log is our happy path.
databaseChangeLog {
    preConditions {
        dbms(type: 'mysql')
    }
    includeAll(path: 'include', relativeToChangelogFile: true)
    changeSet(author: 'ssaliman', id: 'root-change-set') {
        addColumn(tableName: 'monkey') {
            column(name: 'emotion', type: 'varchar(50)')
        }
    }
}

