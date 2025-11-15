package org.liquibase.groovy.delegate


import liquibase.change.core.LoadDataChange
import liquibase.change.core.LoadDataColumnConfig
import liquibase.statement.DatabaseFunction
import spock.lang.Specification

import static org.liquibase.groovy.delegate.ChangeSetDelegate.Tag.loadData
import static org.liquibase.groovy.delegate.ChangeSetDelegate.Tag.loadUpdateData
import static org.liquibase.groovy.delegate.ChangeSetTests.buildChange
import static org.liquibase.groovy.helper.constants.columnName
import static org.liquibase.groovy.helper.util.assertPropsSet

class ColumnDelegatesSpec extends Specification {

    static <T> T verify( Map<String, Object> expectedProps, List columns, Class<T> cls) {
        1 == columns.size()
        T col = cls.cast(columns[0])
        assertPropsSet expectedProps, col
        col
    }


    final static loadDataColumnType = 'STRING'

    final static expPropsLoadData = [
            name: columnName,
            type: loadDataColumnType,
            defaultValue: 's',
            defaultValueNumeric: 1,
            defaultValueDate: "2010-11-02 07:52:04.0",
            defaultValueBoolean: true,
            defaultValueComputed: new DatabaseFunction("defaultDatabaseValue"),
            header: 'h',
            index: 1,
    ]

    def "loadData with #type arguments" () {
        LoadDataChange ch = buildChange loadData, [:], cl, expPropsLoadData
        expect:
        verify expPropsLoadData, ch.columns, LoadDataColumnConfig
        where:
        type         | cl
        'named'      | { column expPropsLoadData }
        'positional' | {
                    it.with {
                        column columnName, it.type, defaultValue,
                                defaultValueNumeric, defaultValueDate, defaultValueBoolean,
                                defaultValueComputed, header, index
                    }
                }
    }


    final static expPropsLoadUpdateData = [
            name: columnName,
            type: loadDataColumnType,
            defaultValue: 's',
            defaultValueNumeric: 1,
            defaultValueDate: new Date(),
            defaultValueBoolean: true,
            defaultValueComputed: "defaultDatabaseValue",
            header: 'h',
            index: 1,
            allowUpdate: true
    ]

    def "loadUpdateData with #type arguments" () {
        LoadDataChange ch = buildChange loadUpdateData, [:], cl, expPropsLoadUpdateData
        expect:
        verify expPropsLoadUpdateData, ch.columns, LoadDataColumnConfig
        where:
        type         | cl
        'mixed'      | { it.with {
            column columnName, it.type, defaultValue,
                    defaultValueNumeric, defaultValueDate, defaultValueBoolean,
                    defaultValueComputed, header, allowUpdate: allowUpdate, index
        }
        }
        'named'      | { column it }
        'positional' | { it.with {
                column columnName, it.type, defaultValue,
                        defaultValueNumeric, defaultValueDate, defaultValueBoolean,
                        defaultValueComputed, header, index, allowUpdate
            }
        }

    }
}
