/*
 * Copyright 2011-2025 Tim Berglund and Steven C. Saliman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package liquibase.util

import groovy.transform.CompileStatic
import liquibase.exception.UnexpectedLiquibaseException
import liquibase.statement.DatabaseFunction
import liquibase.statement.SequenceCurrentValueFunction
import liquibase.statement.SequenceNextValueFunction
import org.liquibase.groovy.delegate.DelegateUtil

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import static java.util.Optional.ofNullable

/**
 * This class is a copy of the ObjectUtil class in Liquibase itself, but patched to work with the
 * Groovy DSL.  This is a short term hack until Liquibase ParsedNode parsing properly rejects
 * invalid nodes with an error instead of silently ignoring them.  See
 * https://liquibase.jira.com/browse/CORE-1968?focusedCommentId=24201#comment-24201
 * for mor information.
 *
 * @author Nathan Voxland
 * @author Steven C. Saliman
 */
@CompileStatic
class PatchedObjectUtil {

    private static Map<Class<?>, Map<String, List<Method>>> methodCache = [:];

/*    static Object getProperty(Object object, String propertyName) throws IllegalAccessException, InvocationTargetException {
        Method readMethod = getReadMethod(object, propertyName);
        if ( readMethod == null ) {
            throw new UnexpectedLiquibaseException("Property '" + propertyName + "' not found on object type " + object.getClass().getName());
        }

        return readMethod.invoke(object);
    }

    static boolean hasProperty(Object object, String propertyName) {
        return hasReadProperty(object, propertyName) && hasWriteProperty(object, propertyName);
    }

    static boolean hasReadProperty(Object object, String propertyName) {
        return getReadMethod(object, propertyName) != null;
    }

    static boolean hasWriteProperty(Object object, String propertyName) {
        return getWriteMethod(object, propertyName) != null;
    }*/

    static <T> T convert(Object o, Class<T> type) {
        if(null == o) return o
        if(! type.isAssignableFrom(o.class)) {
            if ( type.equals(Boolean.class) || type.equals(boolean.class) ) {
                return DelegateUtil.parseTruth(o);
            } else if ( type.equals(Integer.class) ) {
                return o as Integer
            } else if ( type.equals(Long.class) ) {
                return o as Long
            } else if ( type.equals(BigInteger.class) ) {
                return o as BigInteger
            } else if ( type.equals(DatabaseFunction.class) ) {
                return new DatabaseFunction(o as String);
            } else if ( type.equals(SequenceNextValueFunction.class) ) {
                return new SequenceNextValueFunction(o as String);
            } else if ( type.equals(SequenceCurrentValueFunction.class) ) {
                return new SequenceCurrentValueFunction(o as String);
            } else if ( Enum.class.isAssignableFrom(type) ) { // TODO add nicer error  than 'type' is not a valid column attribute for 'loadData' changes
                return Enum.valueOf((Class<Enum>) type, o as String);
            } else if( type.equals(String.class)) {
                return o as String
            }
        }
        o
    }

    static void setProperty(Object object, String propertyName, propertyValue) {
        Method method = getWriteMethod(object, propertyName, propertyValue.class)
        if ( method == null ) {
            throw new UnexpectedLiquibaseException("Property '" + propertyName + "' not found on object type " + object.getClass().getName());
        }

        Class<?> parameterType = method.getParameterTypes()[0];
        Object finalValue = convert( propertyValue, parameterType)
        try {
            method.invoke(object, finalValue);
        } catch (IllegalAccessException e) {
            throw new UnexpectedLiquibaseException(e);
        } catch (IllegalArgumentException e) {
            throw new UnexpectedLiquibaseException("Cannot call " + method.toString() + " with value of type " + finalValue.getClass().getName());
        } catch (InvocationTargetException e) {
            throw new UnexpectedLiquibaseException(e);
        }
    }

    /** Get the write method of the property matching the supplied type
     * If not found return the string version
     * If that is not found either return the first
     * @param object
     * @param propName
     * @param type
     * @return
     */
    static Method getWriteMethod(Object object, String propName, Class type) {
        List<Method> methods = getMethods(object, propName)
        if(methods)
            methods.find {it.parameterTypes[0].isAssignableFrom(type) } ?:
                methods.find {it.parameterTypes[0].isAssignableFrom(String) } ?:
                        methods.first()
        else null
    }

    static String propSetLike(String name, String prefix) {
        name.startsWith(prefix) && Character.isUpperCase(name.charAt(prefix.length())) ?
                name[prefix.length()].toLowerCase(Locale.ENGLISH) + name.substring(prefix.length()+1)
        : null
    }

    static Map<String, List<Method>> getWriteMethods(Class cls) {
        Map<String, List<Method>> r = [:]
        cls.getMethods().each { m ->
            if( m.parameterTypes.length == 1 ) {
                String propSetMethodName = ofNullable(propSetLike(m.name, 'set'))
                   .orElseGet({propSetLike(m.name, 'should') })
                if (propSetMethodName) {
                    r.computeIfAbsent(propSetMethodName, {[]}) add m
                }
            }
        }
        r
    }

    static List<Method> getMethods(Object object, String prop) {
        methodCache.computeIfAbsent(object.class, PatchedObjectUtil::getWriteMethods )[prop]
    }
}
