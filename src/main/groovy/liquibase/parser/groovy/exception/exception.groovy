package liquibase.parser.groovy.exception

import groovy.transform.CompileStatic
import liquibase.exception.ChangeLogParseException
import org.liquibase.groovy.delegate.DelegateUtil.CollectionStringBuilder;

@CompileStatic
/** Helper class to add prefix, file name and line number to the exception after created (not to deepen the stacktrace ) */
class ParseErrorWithFileNLine extends ChangeLogParseException {
    String fileNameAndLine = ""
    String prefix

    ParseErrorWithFileNLine(String msg, String prefix = null, Throwable t=null){
        super(msg, t)
        this.prefix = prefix
    }

    @Override
    String getMessage() { (prefix ? prefix+': ':'') + super.message + fileNameAndLine }
}

@CompileStatic
class MissingClosure extends ParseErrorWithFileNLine {
    MissingClosure(String tagName, String prefix = null) {
        super("'$tagName' missing required closure", prefix)
    }
}

@CompileStatic
class ArgumentSetTwice extends ParseErrorWithFileNLine {
    ArgumentSetTwice(String tagName, String argName, String prefix = null, String altName = null) {
        super(altName ? "Cannot set argument '$argName' twice for '$tagName' with '$altName'! Use '$argName' only!"
           :"Cannot set argument '$argName' twice for '$tagName'! Set either positional or named argument only!", prefix)
    }
}

@CompileStatic
class UnrecognizedElement extends ParseErrorWithFileNLine {
    UnrecognizedElement(String tagName, Collection<String> knownElements, String prefix = null, String msg = null ) {
        super(msg ?: ("Unrecognized element: '$tagName'! ${knownElements ? ('Valid elements are:'+ knownElements.toListString()) :''}") as String, prefix)
    }
}

/** Throw when parameters are known e.g. databaseChangeLog/include */
@CompileStatic
class InvalidArguments extends ParseErrorWithFileNLine {
    InvalidArguments(String tagName, String fnDef, String prefix = null, Object[] args) {
        this(tagName, fnDef, args as List, prefix )
    }
    InvalidArguments(String tagName, String fnDef, Iterable args, String prefix = null) {
        super("'$tagName' element got invalid arguments ${argsToString(args)}. Valid arguments are: [$fnDef]", prefix )
    }

    static String argsToString(Iterable args){
        if(null == args ) {
            return '[]'
        }
        CollectionStringBuilder sb = new CollectionStringBuilder("[")
        args.each{ val ->
            String v
            if(val instanceof Closure) v = '{}'
            else if(val instanceof String) v = "'$val'"
            else v = val.toString()
            sb << v
        }
        sb + ']'
    }
}

/** Throw when unknown parameter passed through to the setProp for an object */
@CompileStatic
class InvalidAttribute extends ParseErrorWithFileNLine {
    InvalidAttribute(String tagName, String attrib, String prefix, String validArgs, String parentName = '',
                     Throwable cause = null) {
        super( "'$attrib' is not valid attribute for '$tagName'" +
                (parentName ? " in '$parentName' changes." :'') +
                (validArgs ? " Valid attributes are: $validArgs" :''), prefix, cause)
    }
}
