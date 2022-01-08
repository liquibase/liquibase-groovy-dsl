// This is a root changelog that can be loaded as a classpath resource to see if includeAll
// correctly errors when we specify an invalid include path.
databaseChangeLog {
    preConditions {
        dbms(type: 'mysql')
    }
    includeAll(path: 'no-such-dir', relativeToChangelogFile: true)
    changeSet(author: 'ssaliman', id: 'root-change-set') {
        addColumn(tableName: 'monkey') {
            column(name: 'emotion', type: 'varchar(50)')
        }
    }
}

