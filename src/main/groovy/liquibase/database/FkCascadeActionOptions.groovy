package liquibase.database

enum FkCascadeActionOptions {
    CASCADE, SET_NULL, SET_DEFAULT, RESTRICT, NO_ACTION
}

enum ColumnParentTypeEnum {
    TABLE, VIEW
}
