// This is a root changelog that can be loaded as a classpath resource to see if filtering works
// when we load a changelog from a classpath.
databaseChangeLog {
    preConditions {
        dbms(type: 'mysql')
    }
    includeAll(path: 'include', relativeToChangelogFile: true, endsWithFilter: 'sql')
    changeSet(author: 'ssaliman', id: 'root-change-set') {
        addColumn(tableName: 'monkey') {
            column(name: 'emotion', type: 'varchar(50)')
        }
    }
}

