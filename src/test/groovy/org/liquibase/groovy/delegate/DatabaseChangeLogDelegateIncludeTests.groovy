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

package org.liquibase.groovy.delegate

import groovy.transform.CompileStatic
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.LiquibaseException
import liquibase.parser.groovy.exception.ArgumentSetTwice
import liquibase.parser.groovy.exception.InvalidArguments
import liquibase.precondition.Precondition
import liquibase.precondition.core.DBMSPrecondition
import liquibase.precondition.core.RunningAsPrecondition
import liquibase.resource.DirectoryResourceAccessor
import org.junit.After
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.*
import static org.liquibase.groovy.helper.util.*
import static liquibase.parser.ext.GroovyLiquibaseChangeLogParser.dbChangeLogTagName

/**
 * One of several test classes for the {@link DatabaseChangeLogDelegate}.  The number of tests for
 * {@link DatabaseChangeLogDelegate} were getting unwieldy, so they were split up.  this class deals
 * with all the "include" element of a database changelog.
 *
 * @author Steven C. Saliman
 */
@CompileStatic
class DatabaseChangeLogDelegateIncludeTests extends DatabaseChangeLogTests {
    // Let's define some paths and directories.  These should all be relative.
    static final String INCLUDED_CHANGELOG_PATH = TMP_CHANGELOG_PATH + "/include"
    static final File TMP_CHANGELOG_DIR = new File(TMP_CHANGELOG_PATH)
    static final File INCLUDED_CHANGELOG_DIR = new File(INCLUDED_CHANGELOG_PATH)

    @Before
    void registerParser() {
        // when Liquibase runs, it gives a DirectoryResourceAccessor based on the absolute path of
        // the current working directory.  We'll do the same for this test.  We'll make a file for
        // ".", then get that file's absolute path, which produces something like
        // "/some/path/to/dir/.", just like what Liquibase does.
        def f = new File(".")
        resourceAccessor = new DirectoryResourceAccessor(new File(f.absolutePath))

        // make sure we start with clean temporary directories before each test
        TMP_CHANGELOG_DIR.deleteDir()
        INCLUDED_CHANGELOG_DIR.mkdirs()
    }

    /**
     * Attempt to clean up included files and directories.  We do this every time to make sure we
     * start clean each time.  The includeAll test depends on it.
     */
    @After
    void cleanUp() {
        TMP_CHANGELOG_DIR.deleteDir()
    }

    /**
     * Test including a file when we have an unsupported attribute.
     */
    @Test(expected = InvalidArguments)
    void includeInvalidAttribute() {
        buildChangeLog {
            include(changeFile: 'invalid')
        }
    }

    /**
     * Try including a file that references an invalid changelog property in the the name.  In this
     * case, the fileName property is not set, so it can't be expanded and the parser will look for
     * a file named '${fileName}.groovy', which of course doesn't exist.
     */
    @Test(expected = LiquibaseException)
    void includeWithInvalidProperty() {
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: '\${fileName}.groovy')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        parseDatabaseChangeLog(rootChangeLogFile, resourceAccessor)
    }

    @Lazy File includedChangeLogFile = createFileFrom(INCLUDED_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: 'included-change-set') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")

    /**
     * Try including a file that has a database changelog property in the name. This proves that we
     * can expand tokens in filenames.  We don't validate any filenames here, just that we can get
     * the right change sets.  We also have a contextFilter and context to prove we can handle those
     * too with the right precedence
     */
    @Test
    void includeWithValidPropertyAndContextFilter() {

        // Let's strip off the extension so the include's file includes a property but is not just
        // a property.

        String baseName = includedChangeLogFile.path
                .replaceAll("\\\\", "/")
                .dropRight(7)

        File rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  property(name: 'fileName', value: '${baseName}')
  include(file: '\${fileName}.groovy', context: 'myContext')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def rootChangeLog =  parseDatabaseChangeLog(rootChangeLogFile, resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()
        assertEquals 'included-change-set', changeSets[0].id
        assertEquals 'ROOT_CHANGE_SET', changeSets[1].id

        // Take a look at the contexts.  The change that came in with the include should have one,
        // the change in the root changelog should not.
        assertEquals 'myContext', changeSets[0].changeLog.includeContextFilter.toString()
        assertNull changeSets[1].changeLog.includeContextFilter

        verifyIncludedPreconditions rootChangeLog
    }

    /**
     * Try including a file with an filename that is relative to the working directory.  This test
     * will set a context but not a contextFilter to prove we can still handle the old context
     * parameter
     */
    @Test
    void includeRelativeToWorkDir() {

        //includedChangeLogFile = includedChangeLogFile.path // should be relative.
        String includedChangeLogFileName = includedChangeLogFile.path.replaceAll("\\\\", "/")

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: '${includedChangeLogFileName}', context: 'myContext', errorIfMissing: false)
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        validateChangeLog parseDatabaseChangeLog(rootChangeLogFile, resourceAccessor)
    }

    @Test
    void includeErrorIfMissing() {
        buildChangeLog {
            include 'a', true, 'myContext', 'myLabel', false
        }
    }

    /** empty file throws strange error message, which should not happen */
    @Test void includeIgnore() {
        buildChangeLog {
            include 'a', true, 'myContext', 'myLabel', false, null, true
        }
    }

    /** Try including a file relative to the changelog file */
    @Test
    void includeRelativeToChangeLog() {
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include 'include/${includedChangeLogFile.name}', 1, 'myContext', 'myLabel'
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        DatabaseChangeLog changeLog = parseDatabaseChangeLog(rootChangeLogFile, resourceAccessor)
        validateChangeLog changeLog
        assertEquals 'mylabel', changeLog.changeSets[0].changeLog.includeLabels.toString()
    }

    /**
     * Try including a file with an filename that is relative to the working directory, but this
     * time, use a logicalFilePath.  This test will set a context but not a contextFilter to prove
     * we can still handle the old context parameter
     */
    @Test
    void includeRelativeToWorkDirWithLogicalFilePath() {
       String includedChangeLogFileName = includedChangeLogFile.path.replaceAll("\\\\", "/")
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: '${includedChangeLogFileName}', context: 'myContext', errorIfMissing: false, logicalFilePath: 'logical/path')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def rootChangeLog = parseDatabaseChangeLog(rootChangeLogFile, resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()
        assertEquals 'included-change-set', changeSets[0].id
        assertEquals 'ROOT_CHANGE_SET', changeSets[1].id

        // Check that the paths of the included change set is the logical path we used. The 2nd
        // change set did not come from the "include", but it will be relative.
        assertEquals 'logical/path', changeSets[0].filePath
        assertEquals 'logical/path', changeSets[0].logicalFilePath
        assertTrue changeSets[1].filePath.startsWith(TMP_CHANGELOG_PATH)
        assertNull changeSets[1].logicalFilePath

        // Take a look at the contexts.  The change that came in with the include should have one,
        // the change in the root changelog should not.
        assertEquals 'myContext', changeSets[0].changeLog.includeContextFilter.toString()
        assertNull changeSets[1].changeLog.includeContextFilter

        verifyIncludedPreconditions rootChangeLog
    }

    /**
     * Try including a file relative to the changelolg file when the root changelog is also
     * relative.
     */
    @Test
    void includeRelativeToRelativeChangeLog() {
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
            databaseChangeLog {
              preConditions {
                dbms(type: 'mysql')
              }
              include(file: 'include/${includedChangeLogFile.name}', relativeToChangelogFile: 'y', errorIfMissing: false, contextFilter: 'myContext')
              changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
                addColumn(tableName: 'monkey') {
                  column(name: 'emotion', type: 'varchar(50)')
                }
              }
            }
            """)
        validateChangeLog parseDatabaseChangeLog(rootChangeLogFile, resourceAccessor)
    }

    void validateChangeLog(DatabaseChangeLog rootChangeLog) {

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()
        assertEquals 'included-change-set', changeSets[0].id
        assertEquals 'ROOT_CHANGE_SET', changeSets[1].id

        // Take a look at the contexts.  The change that came in with the include should have one,
        // the change in the root changelog should not.
        assertEquals 'myContext', changeSets[0].changeLog.includeContextFilter.toString()
        assertNull changeSets[1].changeLog.includeContextFilter

        // Check that the paths of the included change set is relative. The 2nd change set did not
        // come from the "include", so it will be relative as well..
        assertTrue changeSets[0].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
        assertNull changeSets[0].logicalFilePath
        assertTrue changeSets[1].filePath.startsWith(TMP_CHANGELOG_PATH)
        assertNull changeSets[1].logicalFilePath

        verifyIncludedPreconditions rootChangeLog

    }

    /**
     * Try including a file relative to the changelolg file when the root changelog is also
     * relative, but the included file is not in the same directory (or subdirectory) as the root
     * changelog.  The main thing here is to make sure paths like "../someDir" work.
     */
    @Test
    void includeRelativeToRelativeChangeLogParent() {
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: '../tmp/include/${includedChangeLogFile.name}', relativeToChangelogFile: true, errorIfMissing: false, contextFilter: 'myContext')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        validateChangeLog parseDatabaseChangeLog(rootChangeLogFile, resourceAccessor)
    }


    /**
     * Check the preconditions on behalf of the various "includeAll" tests. Most of the "includeAll"
     * tests use the same included changeSets, so this helper lets us keep our tests a little
     * cleaner.  We should get 3 preconditions buried in our changelog structure:<br>
     * A DBMSPrecondition from the root ChangeLog.<br>
     * A RunningAsPrecondition from the first included changelog.<br>
     * Aan empty PreconditionContainer from the second included changelog.
     * <p>
     * We won't worry about the 3rd one, but we'll make sure we get the first two.
     * @param preconditions the preconditions from the root changelog
     */
    private def verifyIncludedPreconditions(DatabaseChangeLog rootChangeLog) {
        List<Precondition> preconditions = extractPreconditions rootChangeLog.preconditions
        assertNotNull preconditions
        assertEquals 2, preconditions.size()
        assertTrue preconditions[0] instanceof DBMSPrecondition
        assertEquals 'mysql', preconditions[0].properties.type
        assertTrue preconditions[1] instanceof RunningAsPrecondition
        assertEquals 'ssaliman', preconditions[1].properties.username
    }

   @Test
   void includeAttributeSetBothNewAndOlName() {
      try {
         buildChangeLog {
            include(context: 'invalid', contextFilter: 'asd')
         }
         fail("Expected exception not thrown");
      } catch (ArgumentSetTwice ex) {
         assertEquals(new ArgumentSetTwice('include','contextFilter',  dbChangeLogTagName ,'context').message, ex.getMessage())
      }
   }
}

