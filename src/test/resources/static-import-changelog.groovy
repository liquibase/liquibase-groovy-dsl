import static org.liquibase.groovy.delegate.ScriptDelegate.databaseChangeLog

// This is a root changelog that can be loaded as a classpath resource to see
// if statically importing databaseChangeLog works
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

