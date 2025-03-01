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
package liquibase.util;

import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.statement.DatabaseFunction;
import liquibase.statement.SequenceCurrentValueFunction;
import liquibase.statement.SequenceNextValueFunction
import liquibase.structure.core.ForeignKeyConstraintType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
class PatchedObjectUtil {

    private static Map<Class<?>, Method[]> methodCache = new HashMap<Class<?>, Method[]>();

    static Object getProperty(Object object, String propertyName) throws IllegalAccessException, InvocationTargetException {
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
    }

    static void setProperty(Object object, String propertyName, String propertyValue) {
        Method method = getWriteMethod(object, propertyName);
        if ( method == null ) {
            throw new UnexpectedLiquibaseException("Property '" + propertyName + "' not found on object type " + object.getClass().getName());
        }

        Class<?> parameterType = method.getParameterTypes()[0];
        Object finalValue = propertyValue;
        if ( parameterType.equals(Boolean.class) || parameterType.equals(boolean.class) ) {
            finalValue = Boolean.valueOf(propertyValue);
        } else if ( parameterType.equals(Integer.class) ) {
            finalValue = Integer.valueOf(propertyValue);
        } else if ( parameterType.equals(Long.class) ) {
            finalValue = Long.valueOf(propertyValue);
        } else if ( parameterType.equals(BigInteger.class) ) {
            finalValue = new BigInteger(propertyValue);
        } else if ( parameterType.equals(DatabaseFunction.class) ) {
            finalValue = new DatabaseFunction(propertyValue);
        } else if ( parameterType.equals(SequenceNextValueFunction.class) ) {
            finalValue = new SequenceNextValueFunction(propertyValue);
        } else if ( parameterType.equals(SequenceCurrentValueFunction.class) ) {
            finalValue = new SequenceCurrentValueFunction(propertyValue);
        } else if ( Enum.class.isAssignableFrom(parameterType) ) {
            finalValue = Enum.valueOf((Class<Enum>) parameterType, propertyValue);
        }
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

    private static Method getReadMethod(Object object, String propertyName) {
        String getMethodName = "get" + propertyName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propertyName.substring(1);
        String isMethodName = "is" + propertyName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propertyName.substring(1);

        Method[] methods = getMethods(object);

        for ( Method method : methods ) {
            if ( (method.getName().equals(getMethodName) || method.getName().equals(isMethodName)) && method.getParameterTypes().length == 0 ) {
                return method;
            }
        }
        return null;
    }

    private static Method getWriteMethod(Object object, String propertyName) {
        String methodName = "set" + propertyName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propertyName.substring(1);
        String alternateName = "should" + propertyName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propertyName.substring(1);
        // Another ugly hack courtesy of the Liquibase 3.7.0 code.  It added some new attributes to
        // the ConstraintsConfig class that don't have proper accessors.
        Method[] methods = getMethods(object);

        for ( Method method : methods ) {
            if ( (method.getName().equals(methodName) || method.getName().equals(alternateName)) && method.getParameterTypes().length == 1 ) {
                // This is where the patch is.  The Liquibase version of this method simply returns
                // the first one arg method that it finds.  The patched one returns the first one
                // that has an argument type we can use. We need a special little bit of logic for
                // the ForeignKeyConstraintType enum because it's string doesn't match the enum
                // constant.  The AddForiegnKeyConstraintChange class has 2 different one-arg
                // setters for onDelete and onUpdate but you never know which one you will get on
                // any given run, so force the String one.  Basically, we allow any enum EXCEPT
                // our problematic ForeignKeyConstraintType
                Class<?> c = method.getParameterTypes()[0];
                if ( c.equals(Boolean.class) ||
                        c.equals(boolean.class) ||
                        c.equals(Integer.class) ||
                        c.equals(Long.class) ||
                        c.equals(BigInteger.class) ||
                        c.equals(DatabaseFunction.class) ||
                        c.equals(SequenceNextValueFunction.class) ||
                        c.equals(SequenceCurrentValueFunction.class) ||
                        c.equals(String.class) ||
                        (Enum.class.isAssignableFrom(c)) && !c.equals(ForeignKeyConstraintType.class) ) {
                    return method;
                }
            }
        }
        return null;
    }

    private static Method[] getMethods(Object object) {
        Method[] methods = methodCache.get(object.getClass());

        if ( methods == null ) {
            methods = object.getClass().getMethods();
            methodCache.put(object.getClass(), methods);
        }
        return methods;
    }

}
