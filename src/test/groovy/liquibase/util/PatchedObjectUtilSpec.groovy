package liquibase.util

import liquibase.change.ColumnConfig
import liquibase.change.core.AddForeignKeyConstraintChange
import spock.lang.*

import java.lang.reflect.Method

//@CompileStatic
class PatchedObjectUtilSpec extends Specification{
    def i ="addForeignKeyConstraint"

    void verify(obj, String prop, Class<?> expClass) {
        Method m = PatchedObjectUtil.getWriteMethod(obj, prop, expClass)
        assert null != m : 'Method not found'
        assert 1 == m.parameterTypes.size()
        assert expClass == m.parameterTypes[0]
    }

    def "getWriteMethod ColumnConfig.#prop as #expClass.simpleName"() {
        verify new ColumnConfig(), prop, expClass
        where:
         prop        | expClass
         "valueDate" | Date
         "valueDate" | String
    }

    def "getWriteMethod AddForeignKeyConstraintChange.#prop as #expClass.simpleName"() {
        verify new AddForeignKeyConstraintChange(), prop, expClass
        where:
        prop       | expClass
        "onDelete" | String
        "onUpdate" | String
    }

}
