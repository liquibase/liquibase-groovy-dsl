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

package liquibase.parser.ext

import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.parser.ChangeLogParser
import liquibase.parser.groovy.exception.MissingClosure
import liquibase.parser.groovy.exception.ParseErrorWithFileNLine
import liquibase.parser.groovy.exception.UnrecognizedElement
import liquibase.resource.ResourceAccessor
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.reflection.CachedMethod
import org.codehaus.groovy.runtime.metaclass.MethodSelectionException
import org.codehaus.groovy.util.FastArray
import org.liquibase.groovy.delegate.DelegateUtil
import org.liquibase.groovy.delegate.MethodDef

import java.lang.reflect.Modifier

/**
 * This is the main parser class for the Liquibase Groovy DSL.  It is the integration point to
 * Liquibase itself.  It must be in the liquibase.parser.ext package to be found by Liquibase at
 * runtime.
 *
 * @author Tim Berglund
 * @author Steven C. Saliman
 */
@groovy.transform.CompileStatic
@groovy.util.logging.Log
class GroovyLiquibaseChangeLogParser implements ChangeLogParser {

    DatabaseChangeLog parse(String physicalChangeLogLocation,
                            ChangeLogParameters changeLogParameters,
                            ResourceAccessor resourceAccessor) {

        physicalChangeLogLocation = physicalChangeLogLocation.replaceAll('\\\\', '/')
        def inputStream = resourceAccessor.openStream(null, physicalChangeLogLocation)
        if ( !inputStream ) {
            throw new ChangeLogParseException(physicalChangeLogLocation + " does not exist")
        }
        parse(inputStream, resourceAccessor, changeLogParameters, physicalChangeLogLocation)
    }

    static DatabaseChangeLog parse(InputStream inputStream, ResourceAccessor resourceAccessor,
                                   ChangeLogParameters changeLogParameters = new ChangeLogParameters(),
                                   String physicalChangeLogLocation = 'memtest' ) {
        try {
            def binding = new Binding()
            def config = new CompilerConfiguration()
            config.scriptBaseClass = 'liquibase.GroovyScript'
            config.addCompilationCustomizers(new ImportCustomizer()
                .addStaticStars('liquibase.database.ObjectQuotingStrategy'
                                ,'liquibase.changelog.ChangeSet.ValidationFailOption'
                                ,'liquibase.database.ColumnParentTypeEnum'
                                ,'liquibase.precondition.core.PreconditionContainer.OnSqlOutputOption'
                                ,'liquibase.precondition.core.PreconditionContainer.FailOption'
//                                        ,'liquibase.database.FkCascadeActionOptions'
                )
                .addImport ('OnError','liquibase.precondition.core.PreconditionContainer.ErrorOption')
                //.addImport ('OnFail' ,'liquibase.precondition.core.PreconditionContainer.FailOption')
            )
            def shell = new GroovyShell(binding, config)

            // Parse the script, give it the local changeLog instance, give it access to root-level
            // method delegates, and call.
            Script script = shell.parse(new InputStreamReader(inputStream, "UTF8")
                                        ,physicalChangeLogLocation)

            runScript script, resourceAccessor, changeLogParameters, physicalChangeLogLocation
        }
        finally {
            try {
                inputStream.close()
            }
            catch (Exception ignored) {
                // Can't do much more than hope for the best here
            }
        }
    }

    static DatabaseChangeLog runScript (Script script, ResourceAccessor resourceAccessor,
                                        ChangeLogParameters changeLogParameters = new ChangeLogParameters(),
                                        String physicalChangeLogLocation = 'memtest') {
        DatabaseChangeLog changeLog = new DatabaseChangeLog(physicalChangeLogLocation)
        changeLog.setChangeLogParameters(changeLogParameters)
        script.setProperty("changeLog", changeLog)
        script.setProperty("resourceAccessor", resourceAccessor)
        try {
            script.run()
        } catch (CompilationFailedException e){
            throw e // Contains file + line
        } catch(MethodSelectionException e) {
            // Get private fields
            String methodName = DelegateUtil.getMethodName(e)
            Class[] argTypes = e.metaClass.getAttribute(e, 'arguments') as Class[]
            FastArray methods = e.metaClass.getAttribute(e, 'methods') as FastArray
            //MetaMethod method = script.metaClass.methods.find {it.name == methodName}
            MetaMethod method = methods.array.find { (it as CachedMethod).name == methodName} as MetaMethod

            // Most common problem: closure missing as last parameter
            if(method.nativeParameterTypes.last() == Closure.class &&
               (argTypes.length < 1 || argTypes.last() != Closure.class)) {
                throw ChangeLogParseExceptionWithfileAndLineNumber(changeLog,
                   new MissingClosure(methodName), script.class,
                )
            }
            throw ChangeLogParseExceptionWithfileAndLineNumber(changeLog, e, script.class)
        }
        catch (e) {
            throw ChangeLogParseExceptionWithfileAndLineNumber(changeLog, e, script.class)
        }
        // The changeLog will have been populated by the script
        changeLog
    }

    // TODO get method by name and check if its declared in this class
    static List<MetaMethod> findMethod(MetaClass metaClass, String name) {
        metaClass.methods.findAll {it.name == name }}

    boolean supports(String changeLogFile, ResourceAccessor resourceAccessor) {
        changeLogFile.endsWith('.groovy')
    }

    int getPriority() {
        PRIORITY_DEFAULT
    }

    /** If t is ParseErrorWithFileNLine
     * Create a new ChangeLogParseException with the message `errMsg` if not null
     otherwise t.message + the filename from `databaseChangeLog` and the line number from
     the stacktrace of `t` searching for the classname of `clazz`
     If errMsg is null, t added to the created exception as cause
     */
    static ChangeLogParseException ChangeLogParseExceptionWithfileAndLineNumber(
            DatabaseChangeLog databaseChangeLog, Throwable t, Class calzz) {

        StackTraceElement st = t.stackTrace.find { it.className.startsWith(calzz.name) }
        String fileNameAndLine = " @$databaseChangeLog.physicalFilePath:${st ? st.lineNumber : ''}"
        if(t instanceof ParseErrorWithFileNLine) { // Do not deepen the stackTrace
            ParseErrorWithFileNLine fn = t as ParseErrorWithFileNLine
            fn.fileNameAndLine = fileNameAndLine
            return t
        }
        new ChangeLogParseException(t.message + fileNameAndLine, t)
    }

    /** Collect all public methods with the longest parameter list not starting with Map using java reflection
      from 2 equal longest pick the one with closure at the end ! */
    static Map<String, MethodDef> getMethods(Class cls) {
        Map<String, MethodDef> map = new HashMap<>()
        //def s = cls.methods return all methods
        cls.declaredMethods.each {
            if(it.name.indexOf('$') == -1
               && Modifier.isPublic(it.modifiers)
               && it.name != 'methodMissing'
               && it.parameterTypes.length > 0 && !isMap(it.parameterTypes.first())) {
                MethodDef stored = map[it.name]
                if ( stored ) {
                    if (stored.argCount() < it.parameterCount // Found a longer param list
                       || ( stored.argCount() == it.parameterCount // Or same but it has last closure
                           && !stored.lastArgClosure && it.parameterTypes.last() == Closure)) {
                        map[it.name].args = it.parameters // Update
                    }
                    else { // param list is shorter
                        stored.needsClosure &= MethodDef.isClosure(it.parameters.last())
                    }
                } else if(it.parameterTypes.length > 0 ){ // Store the first
                    map[it.name] = new MethodDef(it.parameters)
                }
            }
        }
        map
    }

    static boolean isMap(Class cls) { Map.class.isAssignableFrom (cls)}

    static UnrecognizedElement unrecognizedRootElement(String name) {
        new UnrecognizedElement(name, [],'', "Unrecognized root element '$name'! Only '$dbChangeLogTagName' expected")
    }

    static String nonEmptyParameterRequiredFor(Enum tag, String propName) {
        nonEmptyParameterRequiredFor tag as String, propName
    }

    static String nonEmptyParameterRequiredFor(String tag, String propName) {
        "'$propName' parameter cannot be empty for '$tag'"
    }

    static String dbChangeLogTagName = "databaseChangeLog"

    interface Arg {
        static final String dbms = 'dbms'
        static final String file = 'file'
        static final String path = 'path'
        static final String context = 'context'
        static final String contextFilter = 'contextFilter'
        static final String labels = 'labels'
        static final String global = 'global'
        static final String relativeToChangelogFile = 'relativeToChangelogFile'
        static final String name = 'name'
        static final String ignore = 'ignore'
        static final String value = 'value'
        static final String errorIfMissing = 'errorIfMissing'
        static final String logicalFilePath = 'logicalFilePath'
        static final String runWith = 'runWith'
        static final String runWithSpoolFile = 'runWithSpoolFile'
    }
}

