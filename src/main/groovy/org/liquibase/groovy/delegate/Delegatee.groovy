package org.liquibase.groovy.delegate

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.TupleConstructor
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.parser.groovy.exception.*
import liquibase.serializer.LiquibaseSerializable
import liquibase.util.PatchedObjectUtil
import org.liquibase.groovy.delegate.DelegateUtil.CollectionStringBuilder

import java.lang.reflect.Parameter

import static groovy.lang.Closure.DELEGATE_FIRST
import static groovy.lang.Closure.DELEGATE_ONLY
import static liquibase.Scope.getCurrentScope
import static org.liquibase.groovy.delegate.DelegateUtil.*

@CompileStatic
/** Class for generic functions in ...Delegate classes */
// TODO @Delegate pattern might, to remove public methods from auto-complete
abstract class Delegatee<Tag extends Enum<Tag>> {
    protected final DatabaseChangeLog databaseChangeLog
    final String changeId // used for error messages
    final String parent

    /** method definition cache used for error messages It contains only the longest argument list with closure
      TODO some tag like property / sql / createView / createProcedure / dropColumn requires 2 MethodDef / method
     */
    protected @Lazy Map<String, MethodDef> methodDefs = methodDefs(this.class)
    protected MethodDef methodDef(String methodName) {methodDefs[methodName]}
    protected MethodDef methodDef(Tag methodName) {methodDefs[methodName.name()]}

    Delegatee(DatabaseChangeLog dbChangeLog, String changeId, String parent = null) {
        this.databaseChangeLog = dbChangeLog
        this.changeId = changeId
        this.parent = parent
    }

    //@TypeChecked(TypeCheckingMode.SKIP)
    /** call the given closure with this as delegate for the IDE editor auto complete all methods
     declared with DELEGATE_ONLY, but actually executed as DELEGATE_FIRST
     */
    @PackageScope def callOn( @DelegatesTo(strategy = DELEGATE_ONLY) Closure closure, args = null) {
        DelegateUtil.callOn(this, closure, args)
    }

    Set<String> knownElements() {methodDefs.keySet()}

    /** Prefix the given {@code msg} with {@code changeId} */
    protected String prefix(String msg) {"$changeId: $msg"}

    /** Prefix Exception */
    ParseErrorWithFileNLine prefix(ParseErrorWithFileNLine e) {
        e.prefix = changeId
        e
    }

    /** Call this to throw an exception to make sure, the prefix properly is set */
    protected void error(ParseErrorWithFileNLine e) {
        throw prefix(e)
    }

    /** Create generic ChangeLogParseException with the message msg prefixed with the changeId
        For specific cases specific Exception shall be used
     */
    protected ParseErrorWithFileNLine changeLogParseException(String msg, Throwable t=null) {
        new ParseErrorWithFileNLine(msg, changeId, t)
    }

    protected NullChecker<Tag> chkMap(Tag t, Map<String, Object> args = [:]) {
        new NullChecker<Tag>(changeId, t, args)
    }

    /** {@link #argsToMap} */
    protected Map<String, Object> argsAsMap(Tag tag, Object... args ) {
        String name = tag as String
        MethodDef method = methodDef(name)
        argsAsMap name, method, args
    }

    /** {@link #argsToMap} */
    protected Map<String, Object> argsAsMap(String name, MethodDef method, Object... args ) {
        argsToMap changeId, name, method.needsClosure, method.toString(),
                method.args*.name, args
    }

    protected Map<String, Object> argsToMap( String methodName, boolean needsClosure,
                                         String fnDef, List<String> argNames, Object... args ) {
        argsToMap(changeId, methodName, needsClosure, fnDef, argNames, args )
    }

    protected void logTagPropertyNotYetSupportedWarning(Tag tag, String attrib, String minVersion, String actLbVersion) {
        logWarning(prefix "Property '$attrib' of '$tag' ignored beacuse Liquibase v'$minVersion' supported first. v'$actLbVersion' not")
    }

    /** Check if all arguments are known for the {@code tag} in {@code args} using the method
      definition.
      Using the {code @Since} annotation on the argument definition recognises if the old name of
      the property is set only in {@code args}. <i>Sets value with the actual name and removes the
      entry with the old name</i><br>
      throws {@link InvalidArguments} with the list of unknown argument names
      throws {@link ArgumentSetTwice} if both the an argument's actual and old name are defined in {@code args}
     */
    protected void validateArgs(Tag tag, Map<String, Object> args)
       throws InvalidArguments, ArgumentSetTwice {
        MethodDef method = methodDef(tag.name())
        if(method) {
            Set<String> unsupportedKeys = args.keySet() - method.args*.name
            if(unsupportedKeys) { // Check alternative name
                method.args.each { String altName = it.getDeclaredAnnotation(Since)?.oldName()
                    if(altName) {
                        if(args[it.name]) {
                            error new ArgumentSetTwice(tag.name(), it.name, null, altName)
                        } else { // Silently set
                            unsupportedKeys -= altName
                            args[it.name] = args[altName]
                            args.remove(altName)
                        }
                    }
                }
                if(unsupportedKeys) {
                    error new InvalidArguments(tag.name(), method.toString(), unsupportedKeys)
                }
            }
        }
    }

    /** Log a warning prefixed with the change id using Liquibase's logger*/
    protected void logWarning(String msg) {
        getCurrentScope().getLog(getClass()).warning(prefix(msg))
    }

    protected static String fullChangeSetId(ChangeSet changeSet){"changeSet $changeSet"}

    /** Wrapper for PatchedObjectUtil.setProperty adds detailed error message */
    protected <T extends LiquibaseSerializable> T setProp(T entity, String name, Object value) {
        try {
            if(value != null) {
                PatchedObjectUtil.setProperty(entity, name, expandExpressions(value))
            }
            entity
        } catch (RuntimeException e) {
            throw new InvalidAttribute(entity.serializedObjectName, name, changeId, entity.serializableFields.toListString(), parent, e)
        }
    }

    @PackageScope <T extends LiquibaseSerializable> T setProps(T entity, Map<String, Object> props) {
        props.each {key, value ->
            setProp(entity, key, value)
        }
        entity
    }

    protected <V> V expandExpressions(V value) {
        value instanceof String ?
        expandExpressions(value, databaseChangeLog) as V : value
    }

    //@TypeChecked(TypeCheckingMode.SKIP)
    protected def propertyMissing(String name) {
        methodMissing name, objArr() // Simply forward to methodMissing with empty object array (CANNOT BE null!!)
    }

    protected UnrecognizedElement unrecognizedElement(String name){
        new UnrecognizedElement(name, knownElements())
    }

    @PackageScope InvalidAttribute InvalidAttribute(Tag tag, String name) {
        MethodDef m = methodDefs[tag.name()]
        new InvalidAttribute(tag.name(), name, changeId, m.toString(), parent)
    }

    /**
     * Groovy calls methodMissing when it can't find a matching method to call.
     * We use it to tell the user which changeSet had the invalid element.
     * @param name the name of the method Groovy wanted to call.
     * @param params the original arguments to that method.
     */
    protected def methodMissing(String name, params) {
        MethodDef method = methodDef(name)
        if(!method) {
            error unrecognizedElement(name)
        }
        callSingleMapArgVersion(name, method, params as Object[])
    }

    protected def callSingleMapArgVersion(String name, MethodDef method, Object[] args) {
        Map map = argsAsMap(name, method, args)

        // Make sure it exists to avoid infinite loop
        def m = metaClass.pickMethod(name, (method.lastArgClosure ? [Map, Closure] : [Map]) as Class[])
        if(!m) {
            throw new ParseErrorWithFileNLine("Unable to find method: '$name' for object $this with args: Map, Closure", changeId)
        }

        method.lastArgClosure ? m.invoke (this, objArr(map, args.last())) : m.invoke (this, map)
    }
}

@CompileStatic
@TupleConstructor(useSetters = true, includes = ['args']) //'name',
class MethodDef {
    //final String name
    Parameter[] args
    boolean needsClosure = true
    boolean lastArgClosure // Cache
    int argCount() { args ? args.size() : 0}

    void setArgs(Parameter[] params) {
        this.lastArgClosure = isClosure(params.last())
        needsClosure &= this.lastArgClosure
        args = params
    }

    static boolean isClosure(Parameter p) { p.type == Closure }

    /** Create human readable list of parameter names + types
     * Expects Closure to be the last parameter */
    @Override
    String toString() {
        args.inject(new CollectionStringBuilder()) { r, p ->
            switch ( p.type.simpleName ) {
                case 'Closure': return r << "{ $p.name }"
                    break
                case 'Map': break
                default :
                    r << "$p.type.simpleName $p.name"
            }
            r
        }
    }
}
