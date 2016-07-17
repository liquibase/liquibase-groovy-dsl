/*
 * Copyright 2011-2015 Tim Berglund and Steven C. Saliman
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package liquibase.parser.ext

import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.parser.ChangeLogParser
import liquibase.resource.ResourceAccessor
import org.codehaus.groovy.control.CompilerConfiguration
import org.liquibase.groovy.delegate.ScriptDelegate

/**
 * This is the main parser class for the Liquibase Groovy DSL.  It is the
 * integration point to Liquibase itself.  It must be in the
 * liquibase.parser.ext package to be found by Liquibase at runtime.
 *
 * @author Tim Berglund
 * @author Steven C. Saliman
 * @authro Conor Restall
 */
class GroovyLiquibaseChangeLogParser implements ChangeLogParser {

  DatabaseChangeLog parse(String physicalChangeLogLocation,
                          ChangeLogParameters changeLogParameters,
                          ResourceAccessor resourceAccessor) {

    physicalChangeLogLocation = physicalChangeLogLocation.replaceAll('\\\\', '/')

    Set<InputStream> inputStreams = resourceAccessor.getResourcesAsStream(physicalChangeLogLocation)
    if (!inputStreams || inputStreams.size() < 1) {
      throw new ChangeLogParseException(physicalChangeLogLocation + " does not exist")
    }
    def inputStream = inputStreams.first()

    try {
      def changeLog = new DatabaseChangeLog(physicalChangeLogLocation)
      changeLog.setChangeLogParameters(changeLogParameters)

      CompilerConfiguration cc = new CompilerConfiguration()
      cc.setScriptBaseClass(DelegatingScript.class.name)

      def binding = new Binding()
      def shell = new GroovyShell(binding, cc)

      def scriptDelegate = new ScriptDelegate(changeLog, resourceAccessor)

      // Parse the script, give it the local changeLog instance, give it access
      // to root-level method delegates, and call.
      def script = (DelegatingScript) shell.parse(inputStream.text)
      script.setDelegate(scriptDelegate)
      script.run()

      // The changeLog will have been populated by the script
      return changeLog
    }
    finally {
      try {
        inputStream.close()
      }
      catch (Exception e) {
        // Can't do much more than hope for the best here
      }
    }
  }


  boolean supports(String changeLogFile, ResourceAccessor resourceAccessor) {
    changeLogFile.endsWith('.groovy')
  }


  int getPriority() {
    PRIORITY_DEFAULT
  }

}
