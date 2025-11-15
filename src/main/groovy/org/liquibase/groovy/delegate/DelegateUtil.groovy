/*
 * Copyright 2011-2024 Tim Berglund and Steven C. Saliman
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

package org.liquibase.groovy.delegate

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.parser.groovy.exception.ArgumentSetTwice
import liquibase.parser.groovy.exception.InvalidArguments
import liquibase.parser.groovy.exception.MissingClosure
import liquibase.util.LiquibaseUtil
import org.apache.commons.lang3.StringUtils
import org.codehaus.groovy.runtime.metaclass.MethodSelectionException

import static groovy.lang.Closure.DELEGATE_FIRST
import static groovy.lang.Closure.DELEGATE_ONLY
import static liquibase.parser.ext.GroovyLiquibaseChangeLogParser.getMethods
import static org.liquibase.groovy.delegate.DelegateUtil.MapCategory.ifNotNull

/**
 * Little utility with helper methods that all the delegates can use.
 *
 * @author Steven C. Saliman
 */
@CompileStatic
@groovy.util.logging.Log
class DelegateUtil {
    public static final String v4_30 = '4.30.0'
    public static final String v4_16 = '4.16.0'
    /**
     * Helper method that expands a text expression, replacing variables inside strings with their
     * values from the database change log parameters.
     * @param expression the text to expand, or null if the expression is null.
     * @param databaseChangeLog the database change log
     * @return the text, after substitutions have been made.
     */
    static String expandExpressions(expression, DatabaseChangeLog databaseChangeLog) {
        // Don't expand a null into the text "null", just return null
        if ( expression == null ) {
            return null
        }

        // Don't try to expand if we have no parameters.
        if ( databaseChangeLog.changeLogParameters == null ) {
            return expression
        }
        return databaseChangeLog.changeLogParameters.expandExpressions(expression.toString(), databaseChangeLog)
    }

    /**
     * Helper method to determine the truth of a value.  We need this because Groovy's
     * {@code asBoolean} method for Strings treats any non-empty string as true, including the
     * string whose contents are "false".  This means that a property whose value is "false" would
     * be set to true in a simple if statement.
     * <p>
     * We get around this problem by using the {@code toBoolean} method if the given value is a
     * String.  This way, only the strings "1", "true", and "y" are treated as true.  All others are
     * treated as false.
     * <p>
     * Examples of "true" values are {@code true}, {@code 1}, and {@code "true"}.  Examples of
     * "false" are {@code false}, {@code 0}, and {@code "false"}.
     * @param value the value to parse
     * @param defaultValue the default value to use if there is no value given.
     * @return whether or not the given value is "true", or the defaultValue if no value is given.
     */
    static boolean parseTruth(value, defaultValue = false) {
        if ( value == null ) {
            return defaultValue
        }
        if ( value instanceof String ) {
            return value.toBoolean()
        }
        value
    }

    @CompileStatic
    static class MapCategory {
        /** If obj not null call the given closure */
        static <I,O> O ifNotNull( I o, @ClosureParams(FirstParam) Closure<O> cl) {
            if ( null != o ) {
                cl(o)
            }
            else o as O
        }
        static Map<String, Object> putNotNull(Map<String, Object> map, String key, value) {
            ifNotNull(value){map.put(key, it)}
            map
        }
    }

    /** cast vararg object to Object[] */
    static Object[] objArr(Object... o){o}

    /** Safe cast `o` tp `cls` if possible. Return null otherwise */
    static <T> T cast(o, Class<T> cls) {
        cls.isInstance(o) ? o as T : null
    }

    /** compare 2 semver strings */
    static int compareSemvers(String ver1, String ver2) {
        List v1segs = ver1.tokenize('.')
        List v2segs = ver2.tokenize('.')

        int commonIndices = Math.min(v1segs.size(), v2segs.size())

        for (int i = 0; i < commonIndices; ++i) {
            def v1 = v1segs[i].toInteger()
            def v2 = v2segs[i].toInteger()

            if (v1 != v2) {
                return v1 <=> v2
            }
        }

        // If we got this far then all the common indices are identical, so whichever version is longer must be more recent
        v1segs.size() <=> v2segs.size()
    }

    @CompileStatic
    static class CollectionStringBuilder {
        final StringBuilder self
        final String separator
        CollectionStringBuilder(String init = null, String separator = ', ', StringBuilder sb = new StringBuilder()) {
            this.separator = separator
            self = sb
            if(init) self.append (init)
        }
        /** Add separator + value */
        def leftShift(String s) {
            if(null == s) return this
            if(self.length() > 0) this + separator
            this + s
        }
        int size() {self.length()}
        /** Simple append */
        def plus(String s) {
            self.append( s)
            this
        }
        @Override
        String toString(){self.toString()}
    }

	/**
	 * Compare the version of Liquibase being used to a target semver and return if we're using a
	 * version of Liquibase that is at least at the version of the target.
	 *
	 * @param targetSemver the target version to use as a comparison.
	 * @return @{code true} if the Liquibase version is greater than or equal to the target semver.
	 */
	static boolean lbVersionAtLeast(String targetSemver) {
      if(!targetSemver) return true
		String liquibaseVersion = LiquibaseUtil.getBuildVersion().replaceFirst(/-\w+$/,'')
		compareSemvers (liquibaseVersion, targetSemver) >= 0
	}

    /** Collect new properties for backward compatibility
     could be populated from @Since annotation on method parameter generated by the AI Generator when to information is in the XSD
     element -> property -> min Lb semver

    static final Map<String, Map<String, String >> supportMap = [
        include: [logicalFilePath: '4.30.0']
       ,includeAll: [logicalFilePath: '4.30.0']
       ,includeAllSql: [logicalFilePath: '4.30.0']
    ] as Map<String, Map<String, String >>
       */
    /** @return true if the actually executing Liquibase version supportes the given {@code property}
     * of {@code element}

    static boolean runningLbSupports(String element, String property) {
        String reqver = supportMap[element]?[property]
        reqver ? lbVersionAtLeast(reqver) : true
    }*/

    protected static final Map<String, Map<String, MethodDef>> allMethods = [:]

    static String delegateClassSimpleName(String tagName) {
        StringUtils.capitalize(tagName) + "Delegate"
    }

    /** Get Delegate class for tagname */
    static Class<Delegate> delegateClass4tag(String tagName) {
        (Class<Delegate> )Class.forName("org.liquibase.groovy.delegate." + delegateClassSimpleName(tagName))
    }

    static Map<String, MethodDef> methodDefs4Tag(String tagName) {
        getMethodDefs( delegateClass4tag(tagName ))
    }

    static Map<String, MethodDef> getMethodDefs(Class cls) {
        Map<String, MethodDef> methods = getMethods(cls)
        if(cls.simpleName.contains('Update')) { // Help inheritance
            methods += getMethods(cls.superclass)
        }
        log.fine("Methods for $cls.simpleName: $methods")
        methods
    }

    static Map<String, MethodDef> methodDefs(Class cls) {
        allMethods.computeIfAbsent(cls.simpleName,{ getMethodDefs(cls) })
    }

    /** object to allow fluently call {@link #call} */
    static class NullChecker<E extends Enum<E>>{
        final E elem
        public final Map<String, Object> asMap
        public final String changeId
        NullChecker(String changeId, E e, Map<String, Object> args = [:]) {
            elem = e
            asMap = args
            this.changeId = changeId
        }

        /** If `value` not null & `key` is not in the map yet put them in the map
         @throws liquibase.parser.groovy.exception.ArgumentSetTwice if key is in the map already
         */
        NullChecker<E> call(String key, value) throws ArgumentSetTwice {
            ifNotNull(value){
                if(asMap.get(key)){
                    throw new ArgumentSetTwice(elem as String, key, changeId)
                }
                asMap.put(key, it)
            }
            this
        }
    }

    static String fullChangeSetId(ChangeSet changeSet){"changeSet $changeSet"}

    /**
     * Generates a map: argNames[i] -> args[i] skips null values
     * Skips last args if {needsClosure} true
     * @param args expected to get all arguments including the starting Map and closing Closure
     * @param argNames expected to contain all parameter names excluding the first Map parameter
     * @throws liquibase.parser.groovy.exception.InvalidArguments if mandatory parameter(s) are missing or there are more args than expected
     * @throws ArgumentSetTwice
     * @throws liquibase.parser.groovy.exception.MissingClosure if {needsClosure} true and the last args not Closure
     *
     */
    static Map<String, Object> argsToMap(String prefix, String methodName, boolean needsClosure,
                                         String fnDef, List<String> argNames, Object... args )
       throws ArgumentSetTwice, InvalidArguments, MissingClosure {
        assert argNames.size() > 0
        // Make sure there are at least 1 args if needed
        if( !args || !args.length ) {
            throw needsClosure ? new MissingClosure(methodName, prefix)
               : new InvalidArguments(methodName, fnDef, prefix, args)
        }

        int cl = args.last() instanceof Closure ? 1 : 0
        if(needsClosure && !cl) {
            throw new MissingClosure(methodName, prefix)
        }
        int i = args.first() instanceof Map ? 1 : 0
        Map<String, Object> map = i ? (Map)args.first(): new LinkedHashMap<>()
        // Make sure there are no more args, than expected
        if(args.length - i > argNames.size()) {
            throw new InvalidArguments(methodName, fnDef, prefix, args)
        } // TODO check if map contains only known args
        for(int n = 0; i < args.length-cl; i++) {
            String name = argNames[n++]
            def val = args[i]
//            if(val instanceof Closure) { // Any argument can be a closure
//                val = (val as Closure)()
//            }
            if(val != null) {
                if(map[name] != null){
                    throw new ArgumentSetTwice(methodName, name, prefix)
                }
                map[name] = val
            }
        }
        map
    }

    /** call the given closure with this as delegate for the IDE editor auto complete all methods
     declared with DELEGATE_ONLY, but actually executed as DELEGATE_FIRST */
    static def callOn(self, @DelegatesTo(strategy = DELEGATE_ONLY) Closure closure, args = null) {
        if(closure) {
            closure.delegate = self
            closure.resolveStrategy = DELEGATE_FIRST
            try {
                return closure.call(args)
            } catch (MethodSelectionException e) { // Happens if there are
                log.fine("$e.message caught and redirected to missingMethod")
                self.metaClass.invokeMissingMethod(self, getMethodName(e), objArr())
            }
        }
        null
    }

    static String getMethodName(MethodSelectionException e) {
        e.metaClass.getAttribute(e, 'methodName')
    }

    /** Cache for element Name -> Delegate class */
    static final Map<String, Class<Delegate>> _closureDelegate = [:]

    static Class<Delegate> closureDelegate(String tagName) {
        _closureDelegate.computeIfAbsent(tagName, DelegateUtil::delegateClass4tag)
    }
}
