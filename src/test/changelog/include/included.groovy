// This changelog is designed to be included in the full-changelog.groovy
databaseChangeLog {
    changeSet(id: 'included-change-set-1', author: 'ssaliman') {
        renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
    }
}
