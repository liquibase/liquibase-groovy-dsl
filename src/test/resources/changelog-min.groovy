// This is a root changelog that can be loaded as a classpath resource to see if includeAll works
// when loading from a classpath.  This changelog has a minDepth of 1 to see if we only include
// changes in a subdirectory..
databaseChangeLog {
    preConditions {
        dbms(type: 'mysql')
    }
    includeAll(path: 'include', relativeToChangelogFile: true, minDepth: 2)
    changeSet(author: 'ssaliman', id: 'root-change-set') {
        addColumn(tableName: 'monkey') {
            column(name: 'emotion', type: 'varchar(50)')
        }
    }
}

