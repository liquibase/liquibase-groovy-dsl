// This is a root changelog that can be loaded as a classpath resource to see if includeAll works
// when loading from a classpath.  This changelog has a maxDepth of 1 to see if we exclude changes
// in a subdirectory.
databaseChangeLog {
    preConditions {
        dbms(type: 'mysql')
    }
    // Remember, maxDepth is inclusive
    includeAll(path: 'include', relativeToChangelogFile: true, maxDepth: 1)
    changeSet(author: 'ssaliman', id: 'root-change-set') {
        addColumn(tableName: 'monkey') {
            column(name: 'emotion', type: 'varchar(50)')
        }
    }
}

