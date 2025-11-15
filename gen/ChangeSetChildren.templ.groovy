package org.liquibase.groovy.delegate
${timestamp}
import groovy.transform.CompileStatic
import groovy.transform.SelfType
import liquibase.database.ColumnParentTypeEnum
import liquibase.database.FkCascadeActionOptions
import static org.liquibase.groovy.delegate.ChangeSetDelegate.*
import static groovy.lang.Closure.DELEGATE_ONLY
<% def skip = ['customChange','sql','rollback','output']
   List<String> skipMapArgVersion = ['createProcedure'] //'createView' %>
@CompileStatic
@SelfType(ChangeSetDelegate)
trait ChangeSetChildren {
<% methods.findAll{ it.args && !skip.contains(it.name) }.each { m -> %>
	${m.functionDefinitions( m.name, true)}
<% if(m.args.size() > 2) {
		if(m.childOptional()) { %>
	${m.functionDefinitions(m.name)}
<%  	} // Add Map or Map [+ Closure] versions if hasChild
	if(!skipMapArgVersion.contains(m.name) && (m.hasRequired() || m.hasChild )) {
%>
	${m.javadoc(m.args, m.args.size() > 3 )}<%
	if (m.hasChild){ %>
	void $m.name(Map params,${m.child.typeNNameNDefault(false).dropRight(1)}) {<%
		if(m.child.stringClosure()) { %>
		addChange chkMap(Tag.$m.name, params)${m.child.nameNName(true, '').dropRight(1)}
	}
		<%	} else { %>
		addChangeWithChild Tag.$m.name, params${m.child.nameNName(true, '').dropRight(1)}
	}
<%		}
	} else { %>
	void $m.name(Map params) {
		addChange Tag.$m.name, params
	}
<%    }// if(m.hasChild)
    } // map versions
	} //m.args.size() > 2
  } // each %>
}
