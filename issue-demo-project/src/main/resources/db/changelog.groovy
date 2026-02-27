databaseChangeLog {
    changeSet(author: 'demo', id: 'create-demo-table') {
        createTable(tableName: 'demo_item') {
            column(name: 'id', type: 'BIGINT') {
                constraints(primaryKey: true)
            }
            column(name: 'name', type: 'VARCHAR(255)')
        }
    }
}
