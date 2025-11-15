import com.sun.org.apache.xerces.internal.xs.XSSimpleTypeDefinition
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.transform.TupleConstructor

import java.util.regex.Pattern

import static com.sun.org.apache.xerces.internal.xs.XSConstants.*
import static APIGenerator.*

@CompileStatic
@groovy.util.logging.Log
@TupleConstructor(useSetters = true)
abstract class NameAndDesc<This> {
	final String name
	String desc = ''

	This setDesc(String s) {
		if (s) {
			if (desc) {
				log.warning "desc is already set for $name: $desc got: $s"
			}
			desc += corrections.inject(s.strip()) { String str, Tuple2<Pattern, String> c ->
				//str.replaceAll(c.v1, c.v2)
				c.v1.matcher(str).replaceAll(c.v2)
			}
		}
		this as This
	}
}

@CompileStatic
@groovy.util.logging.Log
@TupleConstructor(includeSuperProperties = true, callSuper = true, useSetters = true)
class Method extends NameAndDesc<Method> {
	final List<Arg> args = []
	boolean hasChild
	Arg getChild() {args.last()}
	boolean hasRequired() { args.any { it.required } }

	boolean childOptional() { hasChild && !child.required }

	String toString() { name }

	boolean filterParams(Arg arg, boolean skipArgsWithNoDesc = true) {
		boolean hasDesc = arg.desc
		if (!hasDesc) {
			log.info "$name / $arg.name has no description"
		}
		hasDesc || !skipArgsWithNoDesc
	}

	String argDescs(List<Arg> args, boolean asHtml, boolean skipArgsWithNoDesc = true) {
		def filtered = args.findAll { filterParams(it, skipArgsWithNoDesc) }
		!filtered ? '' :
			(asHtml ?
				"""\n|\t <br>Params:<dl>${filtered.sum {
					"""\n|\t <dt>${it.nameAsHtml()}</dt>
						|\t\t<dd>${it.desc}</dd>""" }}
						|\t</dl>"""
				: filtered.sum { "\n|\t  @param $it.name $it.desc" })
		//return ''
	}

	List<Arg> processArgs(boolean skipOptionalChild = false, List<String> skip = []) {
		args.findAll{ !skip.contains(it.name) }
			.dropRight(childOptional() && skipOptionalChild ? 1 : 0)
	}

	/** Generate the argument list
	 @param addTypeAndDefault add type and default value (=null)
	 */
	String argList( boolean typeNameNDefault,	boolean skipOptionalChild, List<String> skip = [], String asMap = AsMap) {
		String r = processArgs(skipOptionalChild, skip).sum {
			it.asString(typeNameNDefault,	it.isClosure(), asMap)
		}
		r = r.dropRight(1) // Cut last ,
		!typeNameNDefault && (!hasChild || childOptional() && skipOptionalChild) ? r + asMap : r
	}

	String javadoc(List<Arg> args, boolean argsAsHtml = false, boolean skipArgsWithNoDesc = true) {
		"""/** ${desc}${argDescs(args, argsAsHtml, skipArgsWithNoDesc)} */""".stripMargin()
	}

	/** Generate method definition */
	String fnDef(boolean addNamedArgsMap, String methodToCall,
					 boolean skipOptionalChild = false, List<String> skip = [], String asMap = AsMap) {
		"""${javadoc(processArgs(skipOptionalChild, skip), args.size() > 3)}
	void $name(${addNamedArgsMap ? namedArgs.typeNNameNDefault() : ''}${argList(true, skipOptionalChild, skip, asMap)}) {
		$methodToCall chkMap(Tag.$name${addNamedArgsMap ? ','+namedArgs.name : ''}) ${argList(false, skipOptionalChild, skip, asMap)}
	}"""
	}

	static final String AsMap = '.asMap'
	/** Generate method definition with all parameters and if there are more than 2 parameters
		another definition with named Map argument in front for mixe method calls
	 */
	String functionDefinitions(String methodToCall, boolean skipOptionalChild = false, String asMap = AsMap) {
		// When the child is optional and we do not skip it -> skip exclusive parameters (createView)
		List<String> skip = skipOptionalChild ? [] : (child.requiredExcept ?: [])

		String r = fnDef(false, methodToCall, skipOptionalChild, skip, asMap)
		if(args.size() > 2) {
			r += '\n\n\t' + fnDef(true, methodToCall, skipOptionalChild, skip, asMap)
		}
		r
	}

	static Arg namedArgs = new Arg('ǃ','', Arg.YES, 'Map')

	/** move required first */
	List<Arg> resortArgs() {
		args.sort{ a,b -> a.required ^ b.required ? (!a.required || a.isClosure() ? 1 : -1) : 0 }
	}

	Arg arg(String argName) {
		args.find {it.name == argName}
	}

	@ToString
	@TupleConstructor(includeSuperProperties = true, callSuper = true)
	static class Arg extends NameAndDesc<Arg> {
		List<String> requiredExcept = NO// null: never, empty : always otherwise <list of exclusive other arg names>
		String type = 'String'
		boolean directChild = false
		//String since
		static final List<String> YES = null
		static final List<String> NO = []
		public static final String StringClosure = 'Closure<String>'
		public static final String ClosureType = 'Closure'

		/** Always required false exclusive */
		boolean getRequired() { requiredExcept == null}

		boolean isClosure(String clType = ClosureType) {
			type.contains(clType)
		}

		 boolean stringClosure() {
			isClosure(StringClosure)
		}

		String typeNNameNDefault( boolean child = false) {
			" $type $name${!child && !required ? '=null' : ''},"
		}

		String nameNName( boolean child = false, String asMap = '.asMap') {
			if(child) {
				if (isClosure( StringClosure)) {
					return "('$name',$name ? $name() as String: null)$asMap "
				}
				return "$asMap, $name "
			} else {
				return "('$name',$name) " //${child ? '.asMap' : ''}
			}
		}

		String asString(boolean typeNameNDefault, boolean child = false, String asMap) {
			typeNameNDefault ? typeNNameNDefault(child) : nameNName (child, asMap)
		}

		/**  See {@link com.sun.org.apache.xerces.internal.xs.XSConstants} */
		boolean setType(XSSimpleTypeDefinition td) {
			if (td.isDefinedFacet(XSSimpleTypeDefinition.FACET_ENUMERATION)) {
				type = td.name
			} else
				switch (td.builtInKind) { //
					case STRING_DT:
					case NOTATION_DT..ENTITY_DT: type = 'String'
						break
					case BOOLEAN_DT: type = 'Boolean'
						break
					case INTEGER_DT..NEGATIVEINTEGER_DT: type = 'Integer'
						break
					case LONG_DT: type = 'Long'
						break
					default:
						switch (td.name) {
							case 'booleanExp': type = 'Boolean'
								break
							case 'integerExp': type = 'Integer'
								break
							case 'propertyName': type = 'String'
								break
							default:
								return false
						}
				}
			true
		}

		String nameAsHtml() {
			required ? "<b>$name</b>" : name
		}
	}
}
