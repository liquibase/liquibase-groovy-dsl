import com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaLoader
import com.sun.org.apache.xerces.internal.impl.xs.XSElementDecl
import com.sun.org.apache.xerces.internal.xni.grammars.XMLGrammarDescription
import com.sun.org.apache.xerces.internal.xs.*
import groovy.namespace.QName
import groovy.text.GStringTemplateEngine
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import groovy.xml.XmlParser
import org.apache.commons.lang3.StringUtils

import static APIGenerator.Name.liquibaseNS
import static Method.Arg
import static Method.Arg.*
import static com.sun.org.apache.xerces.internal.xs.XSComplexTypeDefinition.*
import static groovy.lang.Tuple.tuple as t
import static org.liquibase.groovy.delegate.DelegateUtil.cast


@CompileStatic
@groovy.util.logging.Log
@Newify(File)
class APIGenerator {

	static interface Name {
		String liquibaseNS = 'http://www.liquibase.org/xml/ns/dbchangelog'
	}

	/** Generate class(es) from XSD element definitions extracted from the XSD given as the first parameter
		./dbchangelog-latest.xsd used by default.
	 2 element groups are processed: 'ChangeSetChildren', 'PreConditionChildren' the extracted tag
	 information is passed to the template files repectively
	 */
	static void main(String[] args) {
		String xsd = args ? args[0] : 'dbchangelog-latest.xsd'
		File outPath = File(args.length > 1 ? args[1] : '.') // 'src/main/groovy/org/liquibase/groovy/delegate'
		if (!outPath.exists()) {
			log.severe("Output path $outPath.absolutePath does not exists!")
			System.exit(1)
		}

		File rootPath = File('gen')

		XSModel model = new XMLSchemaLoader().loadURI(File(rootPath, xsd).toURI().toString())
		List<Method> allMethods = []
		/** List of global defined model group names */
		['ChangeSetChildren'
		,'PreConditionChildren'
		].each { modelGroup ->
			List<Method> methods = getMethods([], model, modelGroup)
			generateFile( File(rootPath, "${modelGroup}.templ.groovy")
							, File(outPath, "${modelGroup}.groovy")
							, methods)
			allMethods += methods
		}
	}

	static List<Method> getMethods(List<Method> methods, XSModel model, String modelGroup) {
		model.getModelGroupDefinition(modelGroup, liquibaseNS)
			.modelGroup
			.particles.each {
			XSTerm t = (it as XSParticle).term
			switch (t) {
				case XSElementDecl: methods += process(t as XSElementDecl, new Method(t.name))
					break
			}
		}
		methods
	}


	static XmlParser xmlParser = new XmlParser()

	static String closureType(String enity) {
		StringUtils.capitalize(enity) + 'Delegate'
	}

	/** mutually exclusive attributes per change */
	static Map<String, Map<String, List<String>>> exclusiveParams = [
		createView: [selectQuery: ['path', 'encoding', 'relativeToChangelogFile']]
		,createProcedure: [procedureText: ['path', 'encoding', 'relativeToChangelogFile']]
		,dropColumn: [column: ['columnName']]
	] as Map<String, Map<String, List<String>>>

	/** child argument definitions  required for mixed / single value tags, where there is no name in the XSD for them */
	static Map<String,Arg> mixedValueChild = [
		output: new Arg('message',"Message to send to output", YES,'String',true),
		sql: new Arg('sql',"SQL to execute", YES, genClosureType('sql'),true),
		sqlCheck: new Arg('sql',"SQL to execute", YES,'String',true),
		createProcedure: new Arg('procedureText','The SQL creating the procedure.',
			exclusiveParams.createProcedure.procedureText, genClosureType('createProcedure'), true),
		createView: new Arg('selectQuery', 'SQL generating the view',
			exclusiveParams.createView.selectQuery, StringClosure, true),
		//column:'',		validCheckSum:''
	]

	static void generateFile(File templ, File out, List<Method> methods) {
		if (!templ.exists()) {
			log.severe("Template $templ.absolutePath does not exists!")
			System.exit(2)
		}
		if (out.exists()) {
			out.delete()
		}
		out << new GStringTemplateEngine()
			.createTemplate(templ)
			.make([methods   : methods
					,timestamp : timestamp()
			])
	}

	static String genClosureType(String tag) {
		String clType = closureType(tag)
		"\n\t\t\t\t@DelegatesTo(${clType ? "value=$clType," : ''} strategy=DELEGATE_ONLY) " + ClosureType
	}

	static String timestamp() {
		"/* Generated @ ${new Date()} on ${InetAddress.localHost.hostName} */"
	}

	static String annotations2String(XSObjectList objs) {
		objs ? objs.sum {
			if (it instanceof XSAnnotation) {
				return annotations2String(it as XSAnnotation)
			}
			''
		} : ''
	}

	static String annotations2String(XSAnnotation ann) {
		ann ? getDoc(ann.annotationString) : ''
	}

	/** Parse documentation from annotation body because xerces does not parse it further */
	static String getDoc(String annText) {
		def annotation = xmlParser.parseText(annText)
			.getAt(new QName(XMLGrammarDescription.XML_SCHEMA, 'documentation'))
			.text()
		annotation
	}

	/** String corrections to perform on descriptions */
	static def corrections = [
		t(~'\n +', '\n\t\t'),
		t(~'@(\\S+)', '{@code \$1}'),
		t(~"\\*/", '*\\\\/')
	]

	/** Collect method definition from {@code elemDecl} into {@code m}
	 *
	 * @param elemDecl
	 * @param m
	 */
	static Method process(XSElementDecl elemDecl, Method m) {
		m.setDesc annotations2String(elemDecl.annotation)
		switch (elemDecl.typeDefinition) {
			case XSComplexTypeDefinition:
				XSComplexTypeDefinition typeDef = (XSComplexTypeDefinition) elemDecl.typeDefinition
				m.desc += annotations2String(typeDef.annotations)
				typeDef.attributeUses.each {
					XSAttributeUse attr = it as XSAttributeUse
					Arg arg = new Arg(attr.attrDeclaration.name
						, annotations2String(attr.annotations)
						, attr.required ? YES : NO)
					XSSimpleTypeDefinition t = attr.attrDeclaration.typeDefinition as XSSimpleTypeDefinition
					if (!arg.setType(t)) {
						log.severe("Unable to set type from $t for $m.name / $arg.name")
						throw new Exception("Unable to set type from $t for $m.name / $arg.name")
					}
					m.args << arg
				}

				if (typeDef.contentType & CONTENTTYPE_ELEMENT)
					switch (typeDef.particle?.term) { // Find the first element declaration that makes us treat as closure
						case XSModelGroup:
							XSModelGroup g = typeDef.particle?.term as XSModelGroup
							def elems = g.particles.findResults {
								def p = (XSParticle) it
								def e = cast(p.term, XSElementDecl)
								e ? new Child(p, e) : null
							}

							if(typeDef.contentType & CONTENTTYPE_SIMPLE && addDirectArg(m) ){
							} else if (elems) {
								elems[0].with {
									m.args << new Arg(
										elems.size() > 1 ? 'children' : elem.name + 's'// (particle.maxOccurs != 1 ? elem.name + 's' : elem.name)
										, annotations2String(particle.annotations)
										, particle.minOccurs > 0 ? YES : mkExcluisive(m, elem.name) ?: NO
										, elems.size() == 1 && particle.maxOccurs == 1 ? 'String' : // Use a simple attribute like sql comment -> Arg
										genClosureType(m.name))
									m.hasChild = elems.size() > 1 || particle.maxOccurs != 1
								}
							}
					}
				// Get the content element name as the last argName
				if(typeDef.contentType & CONTENTTYPE_SIMPLE && !m.hasChild) {// or mixed
					addDirectArg(m)
				}
		}
		m.resortArgs()
		m
	}

	static <T,R> R when( T v, @ClosureParams(FirstParam) Closure<R> cl = {v}) {
		v ? cl(v) : null
	}

	static List<String> mkExcluisive(Method m, String attrib) {
		when(exclusiveParams[m.name]) {
			when(it[attrib]) {exclusives ->
				when(m.arg(exclusives[0])) {
					it.requiredExcept = YES
				}
				exclusives
			}
		}
	}


	/** Add the direct content as a Closure argument to the method from mixedValueChild
	 Description could be taken from the Liquibase meta data, but attribute info is neither in XSD
	 nor in Liquibase meta data
	 (Description is not available in XSD) */
	static boolean addDirectArg(Method m) {
		Arg directChild = mixedValueChild[m.name]
		if(directChild) {
			m.args << directChild
			m.hasChild = true
			if(directChild.requiredExcept) {
				when(m.arg(directChild.requiredExcept[0])) {
					it.requiredExcept = YES
				}
			}
			return true
		}
		else {
			log.warning("No argument definition found for direct value of $m.name")
		}
		false
/*		ChangeMetaData meta = Scope.getCurrentScope().getSingleton(ChangeFactory.class).getChangeMetaData(m.name)
		ChangeParameterMetaData argDef = meta.parameters.findResult{ if( it.value.serializationType == SerializationType.DIRECT_VALUE) it.value }
		if(argDef) {
			m.args << new Arg(argDef.parameterName, argDef.description, if(m.name)
		}*/
	}
}

@TupleConstructor
class Child {
	XSParticle particle
	XSElementDecl elem
}

