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

import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.exception.LiquibaseException
import liquibase.parser.ChangeLogParserFactory
import liquibase.parser.ext.GroovyLiquibaseChangeLogParser
import liquibase.precondition.Precondition
import liquibase.precondition.core.DBMSPrecondition
import liquibase.precondition.core.PreconditionContainer
import liquibase.precondition.core.RunningAsPrecondition
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.DirectoryResourceAccessor
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

/**
 * One of several test classes for the {@link DatabaseChangeLogDelegate}.  The number of tests for
 * {@link DatabaseChangeLogDelegate} were getting unwieldy, so they were split up.  this class deals
 * with all the "includeAll" element of a database changelog.
 * <p>
 * For the tests in this class, it is helpful to remember that minDepth and maxDepth are 1-based,
 * starting with the include dir itself.  So minDepth of 1 starts including files in the specified
 * directory and below, maxDepth of 1 includes files in the specified directory but not below, and
 * so on.
 *
 * @author Steven C. Saliman
 */
class DatabaseChangeLogDelegateIncludeAllTests {
    // Let's define some paths and directories.  These should all be relative.
    static final ROOT_CHANGELOG_PATH = "src/test/changelog"
    static final TMP_CHANGELOG_PATH = ROOT_CHANGELOG_PATH + "/tmp"
    static final INCLUDED_CHANGELOG_PATH = TMP_CHANGELOG_PATH + "/include"
    // The "3-" is important to put it between 2 filenames in the parent.
    static final INCLUDED_CHANGELOG_SUB_PATH = TMP_CHANGELOG_PATH + "/include/3-subdirectory"
    static final TMP_CHANGELOG_DIR = new File(TMP_CHANGELOG_PATH)
    static final INCLUDED_CHANGELOG_DIR = new File(INCLUDED_CHANGELOG_PATH)
    static final INCLUDED_CHANGELOG_SUB_DIR = new File(INCLUDED_CHANGELOG_SUB_PATH)
    static final ROOT_CHANGE_SET = 'root-change-set'
    // constants for 4 included changesets.  one of them is a sql file, to test filters.  One is
    // alphabetically last, but in in a subdirectory whose name is between two files in the parent,
    // and therefore will be included 3rd.  These constants need to match the ids in
    // src/test/resources/include to properly test the classpath includes.
    static final FIRST_INCLUDED_CHANGE_SET = 'first-included-change-set'
    static final SECOND_INCLUDED_CHANGE_SET = 'second-included-change-set'
    static final THIRD_INCLUDED_CHANGE_SET = 'third-included-change-set'  // the one in the subdir
    static final FOURTH_INCLUDED_CHANGE_SET = 'fourth-included-change-set' // the sql file
    // This one is not a real file, but it looks like a legit file.  It is used by tests that
    // build changelogs on the fly.
    static final MOCK_CHANGELOG = "${ROOT_CHANGELOG_PATH}/mock-changelog.groovy"

    def resourceAccessor
    ChangeLogParserFactory parserFactory


    @Before
    void registerParser() {
        // when Liquibase runs, it gives a DirectoryResourceAccessor based on the absolute path of
        // the current working directory.  We'll do the same for this test.  We'll make a file for
        // ".", then get that file's absolute path, which produces something like
        // "/some/path/to/dir/.", just like what Liquibase does.
        def f = new File(".")
        resourceAccessor = new DirectoryResourceAccessor(new File(f.absolutePath))
        parserFactory = ChangeLogParserFactory.instance
        ChangeLogParserFactory.getInstance().register(new GroovyLiquibaseChangeLogParser())
        // make sure we start with clean temporary directories before each test
        TMP_CHANGELOG_DIR.deleteDir()
        INCLUDED_CHANGELOG_DIR.mkdirs()
        INCLUDED_CHANGELOG_SUB_DIR.mkdirs()
    }

    /**
     * Test including a path when we have an unsupported attribute.
     */
    @Test(expected = ChangeLogParseException)
    void includeAllInvalidAttribute() {
        buildChangeLog {
            includeAll(changePath: 'invalid')
        }
    }

    /**
     * Try including all files in a directory.  For this test, we want a path that contains an
     * invalid token.  The easiest way to do that is to simply use a token that doesn't have a
     * matching property.
     */
    @Test(expected = ChangeLogParseException)
    void includeAllWithInvalidProperty() {
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '\${includedChangeLogDir}')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
    }

    /**
     * Try including all files in a directory.  For this test, we want 4 files to make sure we
     * include them all in the right order, with subdirectory change between base dir changes.
     * This test makes sure that tokens don't affect paths.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllWithValidToken() {
        def includedChangeLogDir = createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  property(name: 'includeDir', value: '${includedChangeLogDir}')
  includeAll(path: '\${includeDir}')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 5, changeSets.size()
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
        assertEquals THIRD_INCLUDED_CHANGE_SET, changeSets[2].id
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[3].id
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        verifyIncludedPreconditions rootChangeLog
    }

    /**
     * Try including all files in a directory.  For this test, we want 4 files to make sure we
     * include them all in the right order, with subdirectory change between base dir changes.
     * This test does things with relative paths so we can verify that the DSL preserves the
     * relative paths instead of converting them to absolute paths.
     * <p>
     * This test will also make sure we can handle contextFilters properly, by setting both a
     * contextFilter and a context to make sure we get the contextFilter.  It also makes sure our
     * default endsWithFilter is properly set to include only groovy files.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllRelativeToWorkDir() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}', context: 'override', contextFilter: 'myContext')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 5, changeSets.size()
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
        assertEquals THIRD_INCLUDED_CHANGE_SET, changeSets[2].id
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[3].id
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        // Take a look at the contexts of the changes.  The first 4, came from the included file,
        // and should have contexts.  The 4th one came from the root changelog and should not.
        assertEquals 'myContext', changeSets[0].changeLog.includeContextFilter.toString()
        assertEquals 'myContext', changeSets[1].changeLog.includeContextFilter.toString()
        assertEquals 'myContext', changeSets[2].changeLog.includeContextFilter.toString()
        assertEquals 'myContext', changeSets[3].changeLog.includeContextFilter.toString()
        assertNull changeSets[4].changeLog.includeContextFilter

        verifyIncludedPreconditions rootChangeLog
    }

    /**
     * Try including all files in a directory relative to a changelog that uses a relative path.
     * This test is looking at the relativeToChangeLogFile parameter.  This test also sets a context
     * without a contextFilter to make sure we handle the old parameter
     */
    @Test
    void includeAllRelativeToRelativeChangeLog() {
        createIncludedChangeLogFiles()
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: 'include', relativeToChangelogFile: true, context: 'myContext')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 5, changeSets.size()
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
        assertEquals THIRD_INCLUDED_CHANGE_SET, changeSets[2].id
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[3].id
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        // Take a look at the contexts of the changes.  The first 4, came from the included file,
        // and should have contexts.  The 4th one came from the root changelog and should not.
        assertEquals 'myContext', changeSets[0].changeLog.includeContextFilter.toString()
        assertEquals 'myContext', changeSets[1].changeLog.includeContextFilter.toString()
        assertEquals 'myContext', changeSets[2].changeLog.includeContextFilter.toString()
        assertEquals 'myContext', changeSets[3].changeLog.includeContextFilter.toString()
        assertNull changeSets[4].changeLog.includeContextFilter

        verifyIncludedPreconditions rootChangeLog
    }

    /**
     * Try including all files in a directory relative to a changelog that uses a relative path.
     * This test is looking at the relativeToChangeLogFile parameter.  For this test, the included
     * changelogs are not in the same directory as (or a subdirectory of) the root changelog.  The
     * main thing here is to make sure paths like "../somedir" work.
     */
    @Test
    void includeAllRelativeToRelativeChangeLogParent() {
        createIncludedChangeLogFiles()
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '../tmp/include', relativeToChangelogFile: true)
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 5, changeSets.size()
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
        assertEquals THIRD_INCLUDED_CHANGE_SET, changeSets[2].id
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[3].id
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        // Check that the paths of the 3 included change sets are relative.  The 4th change set did
        // not come from the "includeAll", but it will be relative.
        assertTrue changeSets[0].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/1-first")
        assertTrue changeSets[1].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/2-second")
        assertTrue changeSets[2].filePath.startsWith("${INCLUDED_CHANGELOG_SUB_PATH}/third")
        assertTrue changeSets[3].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/4-fourth")
        assertTrue changeSets[4].filePath.startsWith(TMP_CHANGELOG_PATH)

        verifyIncludedPreconditions rootChangeLog
    }

    /**
     * Try including all files in a directory, but with a resource filter.  For this test, we'll
     * repeat wanting 4 files, but with a filter that excludes 3 of them. Test may fail because of
     * unclean directories. Fix the other tests first.
     */
    @Test
    void includeAllValidWithFilter() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             filter: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()  // from the first file, and the changelog itself.
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        assertTrue changeSets[0].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
        assertTrue changeSets[1].filePath.startsWith(TMP_CHANGELOG_PATH)

        verifyIncludedPreconditions rootChangeLog
    }

    /**
     * Try including all files in a directory with an invalid resource filter.  For this test, we'll
     * use a class that doesn't exist.
     */
    @Test(expected = ChangeLogParseException)
    void includeAllValidWithInvalidFilter() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             filter: 'org.liquibase.groovy.helper.NoSuchClass')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
    }

    /**
     * Try including all files in a directory with an invalid resource filter.  For this test, we'll
     * use a class that exists, but isn't an {@code IncludeAllFilter}.
     */
    @Test(expected = ChangeLogParseException)
    void includeAllValidWithInappropriateFilter() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             filter: 'org.liquibase.groovy.helper.ReversingComparator')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
    }

    /**
     * Try including all files in a directory, but with an "endsWithFilter.  For this test, we'll
     * include a directory with 3 files, but with a filter that only matches one of them.  Test may
     * fail because of unclean directories. Fix the other tests first.
     */
    @Test
    void includeAllEndsWithFilter() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             endsWithFilter: 'sql')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()  // from the first file, and the changelog itself.
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals ROOT_CHANGE_SET, changeSets[1].id
    }

    /**
     * Try including all files in a directory when we provide a custom comparator.  For this test,
     * we'll repeat includeAllRelativeToWorkDir, but with a comparator that sorts the included
     * changes in reverse alphabetical order.  We still expect 4 files, but this time, they should
     * come in to the changes in reverse order.
     * <p>
     * This test will also make sure we can handle contexts properly.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllWithComparator() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             resourceComparator: 'org.liquibase.groovy.helper.ReversingComparator')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 5, changeSets.size()
        // Remember, these should come in BACKWARDS because of the comparator.
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals THIRD_INCLUDED_CHANGE_SET, changeSets[1].id
        assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[2].id
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[3].id
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        verifyIncludedPreconditions(rootChangeLog)
    }

    /**
     * Try including all files in a directory when we provide an invalid custom comparator.  For
     * this test, we'll use a class that doesn't exist.
     */
    @Test(expected = ChangeLogParseException)
    void includeAllWithInvalidComparator() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             resourceComparator: 'org.liquibase.groovy.helper.NoSuchClass')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
    }

    /**
     * Try including all files in a directory when we provide an invalid custom comparator.  For
     * this test, we'll use a class that does exist, but is not a {@code Comparator}
     */
    @Test(expected = ChangeLogParseException)
    void includeAllWithInappropriateComparator() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             resourceComparator: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
    }

    /**
     * Try including all when the path doesn't exist is invalid.  Expect an error.
     */
    @Test(expected = LiquibaseException)
    void includeAllInvalidPath() {
        buildChangeLog {
            includeAll(path: 'invalid')
        }
    }

    /**
     * Try including all when the path doesn't exist is invalid, but we've set the
     * errorIfMissingOrEmpty property to false.  For this test, we'll use a string to represent
     * falseness.
     */
    @Test
    void includeAllInvalidPathIgnoreError() {
        def changeLog = buildChangeLog {
            includeAll(path: 'invalid', errorIfMissingOrEmpty: false)
        }
        assertNotNull changeLog
        def changeSets = changeLog.changeSets
        assertNotNull changeSets
        assertEquals 0, changeSets.size()
    }

    /**
     * Try including all when the path is valid, but there are no usable files in the directory.
     * We'll test this by using the filter to eliminate the one change set we'll create to make sure
     * we do the test after the filter.
     */
    @Test(expected = LiquibaseException)
    void includeAllEmptyPath() {
        // This file should be excluded by the resource filter.
        createFileFrom(INCLUDED_CHANGELOG_DIR, 'second', '.groovy', """
databaseChangeLog {
  changeSet(author: 'ssaliman', id: '${SECOND_INCLUDED_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(30)')
    }
  }
}
""")

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             filter: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
    }

    /**
     * Try including all when the path is valid, but there are no usable files in the directory.
     * This time, we'll set the errorIfMissingOrEmpty property to false.  For this test, we'll use
     * a boolean to represent falseness.  We should ignore the error about the empty directory, and
     * get the root change set from the parent file.
     */
    @Test
    void includeAllEmptyPathIgnoreError() {
        // This file should be excluded by the resource filter.
        createFileFrom(INCLUDED_CHANGELOG_DIR, 'second', '.groovy', """
databaseChangeLog {
  changeSet(author: 'ssaliman', id: '${SECOND_INCLUDED_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(30)')
    }
  }
}
""")
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}', errorIfMissingOrEmpty: false,
             filter: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 1, changeSets.size()  // from the changelog itself.
        assertEquals ROOT_CHANGE_SET, changeSets[0].id

        def preconditions = extractPreconditions rootChangeLog.preconditionContainer?.nestedPreconditions
        assertNotNull preconditions
        assertEquals 1, preconditions.size()
        assertTrue preconditions[0] instanceof DBMSPrecondition
        assertEquals 'mysql', preconditions[0].type
    }

    //-------------------------------------------------------------------------------------------
    // Tests of the includeAll method when the changelog file is accessed via the classpath.

    /**
     * Try including all files in a classpath directory.  We'll want to make sure we include them
     * both, and in the right order. with the xml change excluded and the subdirectory change last.
     * <p>
     * These change logs can't be created on the fly, they must exist in a directory that is on the
     * classpath, and we need to replace the resource accessor with one that can load a file from
     * the classpath.
     */
    @Test
    void includeAllClasspath() {
        def rootChangeLogFile = "changelog.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 5, changeSets.size()
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
        assertEquals THIRD_INCLUDED_CHANGE_SET, changeSets[2].id
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[3].id
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        verifyIncludedPreconditions rootChangeLog
    }

    /**
     * Try including all files in a classpath directory, but with a resource filter. For this test,
     * we'll have 3 files in the directory, but the resource filter will excludes two of them.
     * <p>
     * The change logs can't be created on the fly, it must exist in a directory that is on the
     * classpath, and we need to replace the resource accessor with one that can load a file from
     * the classpath.
     */
    @Test
    void includeAllClasspathWithFilter() {
        def rootChangeLogFile = "filtered-changelog.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()  // from the first file, and the changelog itself.
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Verify our file paths.  The included changes should have relative paths.  The change from
        // the root file will have just the name of the file because it was in the root of the
        // classpath.  Remember, we're using classpaths here, so our relative directories will be
        // relative to the classpath root.
        assertTrue changeSets[0].filePath.startsWith('include/')
        assertTrue changeSets[1].filePath.equals('filtered-changelog.groovy')

        verifyIncludedPreconditions rootChangeLog
    }

    /**
     * Try including all files in a classpath directory, but with an ends-with filter. For this
     * test, we'll have 3 files in the directory, but the filter will only match one of them.
     * <p>
     * The change logs can't be created on the fly, it must exist in a directory that is on the
     * classpath, and we need to replace the resource accessor with one that can load a file from
     * the classpath.
     */
    @Test
    void includeAllClasspathEndsWithWithFilter() {
        def rootChangeLogFile = "ends-with-changelog.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()  // from the first file, and the changelog itself.
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Verify our file paths.  The included changes should have relative paths.  The change from
        // the root file will have just the name of the file because it was in the root of the
        // classpath.  Remember, we're using classpaths here, so our relative directories will be
        // relative to the classpath root.
        assertTrue changeSets[0].filePath.startsWith('include/')
        assertTrue changeSets[1].filePath.equals('ends-with-changelog.groovy')
    }

    /**
     * Try including all from a classpath loaded change log when the include path doesn't exist is
     * invalid.  Expect an error.
     * <p>
     * The change logs can't be created on the fly, it must exist in a directory that is on the
     * classpath, and we need to replace the resource accessor with one that can load a file from
     * the classpath.
     */
    @Test(expected = LiquibaseException)
    void includeAllInvalidClassPath() {
        def rootChangeLogFile = "invalid-changelog.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)
    }

    /**
     * Try including all from a classpath loaded change log when the include path is invalid, but
     * we've set the errorIfMissingOrEmpty property to false.
     * <p>
     * The change logs can't be created on the fly, it must exist in a directory that is on the
     * classpath, and we need to replace the resource accessor with one that can load a file from
     * the classpath.
     */
    @Test
    void includeAllInvalidClassPathIgnoreError() {
        def rootChangeLogFile = "ignore-changelog.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 1, changeSets.size()  // from the first file, and the changelog itself.
        assertEquals ROOT_CHANGE_SET, changeSets[0].id

        assertTrue changeSets[0].filePath.equals('ignore-changelog.groovy')

        def preconditions = extractPreconditions rootChangeLog.preconditionContainer?.nestedPreconditions
        assertNotNull preconditions
        assertEquals 1, preconditions.size()
        assertTrue preconditions[0] instanceof DBMSPrecondition
        assertEquals 'mysql', preconditions[0].type
    }

    /**
     * Try including all files in a directory, but with a specified minDepth of 2.  For this test,
     * we expect to exclude the files in the include directory, and only return the one in the
     * subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllRelToWorkDirMinDepth() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAll(path: '${INCLUDED_CHANGELOG_DIR}', minDepth: 2)
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()
        assertEquals THIRD_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Check that the paths of the included change sets are relative.  The 2nd change set did
        // not come from the "includeAll", but it will be relative.
        assertTrue changeSets[0].filePath.startsWith("${INCLUDED_CHANGELOG_SUB_PATH}/third")
        assertTrue changeSets[1].filePath.startsWith(TMP_CHANGELOG_PATH)
    }

    /**
     * Try including all files in a directory, but with a maxDepth of 1 (remember maxDepth is
     * inclusive).  For this test, we expect to exclude the file in the subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllRelToWorkDirMaxDepth() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAll(path: '${INCLUDED_CHANGELOG_DIR}', maxDepth: 1)
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 4, changeSets.size()
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[2].id
        assertEquals ROOT_CHANGE_SET, changeSets[3].id

        // Check that the paths of the 3 included change sets are relative.  The 4th change set did
        // not come from the "includeAll", but it will be relative.
        assertTrue changeSets[0].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/1-first")
        assertTrue changeSets[1].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/2-second")
        assertTrue changeSets[2].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/4-fourth")
        assertTrue changeSets[3].filePath.startsWith(TMP_CHANGELOG_PATH)
    }

    /**
     * Try including all files in a directory, but with a specified minDepth of 2.  For this test,
     * we expect to exclude the files in the include directory, and only return the one in the
     * subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllRelToChangeLogMinDepth() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAll(path: 'include', relativeToChangelogFile: true, minDepth: 2)
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()
        assertEquals THIRD_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Check that the paths of the included change sets are relative.  The 2nd change set did
        // not come from the "includeAll", but it will be relative.
        assertTrue changeSets[0].filePath.startsWith("${INCLUDED_CHANGELOG_SUB_PATH}/third")
        assertTrue changeSets[1].filePath.startsWith(TMP_CHANGELOG_PATH)
    }

    /**
     * Try including all files in a directory, but with a maxDepth of 1 (remember, maxDepth is
     * inclusive).  For this test, we expect to exclude the file in the subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllRelToChangeLogMaxDepth() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAll(path: 'include', relativeToChangelogFile: true, maxDepth: 1)
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 4, changeSets.size()
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[2].id
        assertEquals ROOT_CHANGE_SET, changeSets[3].id

        // Check that the paths of the 3 included change sets are relative.  The 4th change set did
        // not come from the "includeAll", but it will be relative.
        assertTrue changeSets[0].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/1-first")
        assertTrue changeSets[1].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/2-second")
        assertTrue changeSets[2].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/4-fourth")
        assertTrue changeSets[3].filePath.startsWith(TMP_CHANGELOG_PATH)
    }

    /**
     * Try including all files in a directory, but with a specified minDepth of 2.  For this test,
     * we expect to exclude the files in the include directory, and only return the one in the
     * subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllRelToChangeLogParentMinDepth() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAll(path: '../tmp/include', relativeToChangelogFile: true, minDepth: 2)
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()
        assertEquals THIRD_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Check that the paths of the included change sets are relative.  The 2nd change set did
        // not come from the "includeAll", but it will be relative.
        assertTrue changeSets[0].filePath.startsWith("${INCLUDED_CHANGELOG_SUB_PATH}/third")
        assertTrue changeSets[1].filePath.startsWith(TMP_CHANGELOG_PATH)
    }

    /**
     * Try including all files in a directory, but with a maxDepth of 1 (remember maxDepth is
     * inclusive).  For this test, we expect  to exclude the file in the subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllRelToChangeLogParentMaxDepth() {
        createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAll(path: '../tmp/include', relativeToChangelogFile: true, maxDepth: 1)
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 4, changeSets.size()
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[2].id
        assertEquals ROOT_CHANGE_SET, changeSets[3].id

        // Check that the paths of the 3 included change sets are relative.  The 4th change set did
        // not come from the "includeAll", but it will be relative.
        assertTrue changeSets[0].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/1-first")
        assertTrue changeSets[1].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/2-second")
        assertTrue changeSets[2].filePath.startsWith("${INCLUDED_CHANGELOG_PATH}/4-fourth")
        assertTrue changeSets[3].filePath.startsWith(TMP_CHANGELOG_PATH)
    }

    /**
     * Try including all files in a directory, but with a specified minDepth of 2.  For this test,
     * we expect to exclude the files in the include directory, and only return the one in the
     * subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllClasspathMinDepth() {
        def rootChangeLogFile = "changelog-min.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()
        assertEquals THIRD_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Check that the paths of the included change sets are relative.  The 2nd change set did
        // not come from the "includeAll", but it will be relative.  Note that classpath resources
        // are relative to the classpath root, and won't begin with a "/"
        def relativePath = INCLUDED_CHANGELOG_SUB_PATH.substring(TMP_CHANGELOG_PATH.length()+1)
        assertTrue changeSets[0].filePath.startsWith("${relativePath}/third")
        assertEquals "changelog-min.groovy", changeSets[1].filePath
    }

    /**
     * Try including all files in a directory, but with a maxDepth of 2.  For this test, we expect
     * to exclude the file in the subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllClasspathMaxDepth() {
        def rootChangeLogFile = "changelog-max.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 4, changeSets.size()
        assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
        assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
        assertEquals FOURTH_INCLUDED_CHANGE_SET, changeSets[2].id
        assertEquals ROOT_CHANGE_SET, changeSets[3].id

        // Check that the paths of the 3 included change sets are relative.  The 4th change set did
        // not come from the "includeAll", but it will be relative.  Note that classpath resources
        // are relative to the classpath root, and won't begin with a "/"
        def relativePath = INCLUDED_CHANGELOG_PATH.substring(TMP_CHANGELOG_PATH.length()+1)
        assertTrue changeSets[0].filePath.startsWith("${relativePath}/1-first")
        assertTrue changeSets[1].filePath.startsWith("${relativePath}/2-second")
        assertTrue changeSets[2].filePath.startsWith("${relativePath}/4-fourth")
        assertEquals "changelog-max.groovy", changeSets[3].filePath
    }

    /**
     * Try including all files in a directory. but with a logicalFilePath of 'logicalFilePathValue.groovy'.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllWithLogicalPath() {

        def includedChangeLogDir = createIncludedChangeLogFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAll(path: '${includedChangeLogDir}', logicalFilePath: 'logicalFilePathValue.groovy')
}
""")

        def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        changeSets.each { changeSet ->
            assertEquals 'logicalFilePathValue.groovy', changeSet.filePath
            assertEquals 'logicalFilePathValue.groovy', changeSet.logicalFilePath
        }
    }

    /**
     * Helper method that builds a changeSet from the given closure.  Tests will use this to test
     * parsing the various closures that make up the Groovy DSL.
     * @param closure the closure containing changes to parse.
     * @return the changeSet, with parsed changes from the closure added.
     */
    private def buildChangeLog(Closure closure) {
        def changelog = new DatabaseChangeLog(MOCK_CHANGELOG)
        changelog.changeLogParameters = new ChangeLogParameters()
        closure.delegate = new DatabaseChangeLogDelegate(changelog)
        closure.delegate.resourceAccessor = resourceAccessor
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        return changelog
    }

    /**
     * Helper method to create changelogs in a directory for testing the includeAll methods.  It
     * creates 4 files:
     * <ul>
     * <li>A groovy file that should be considered first, and included first.</li>
     * <li>A groovy file that should be considered second and included second.</li>
     * <li>A groovy file that is last in the alphabet, but in a subdirectory that sorts between the
     * first and fourth files.  This will prove that the default sorting is on the entire path, and
     * this file will be included third.</li>
     * <li>a sql file that should be considered fourth. and included fourth</li>
     * </ul>
     *
     * @return the full path of the directory where the files were placed.
     */
    private String createIncludedChangeLogFiles() {
        createFileFrom(INCLUDED_CHANGELOG_DIR, '1-first', '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: '${FIRST_INCLUDED_CHANGE_SET}') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")

        createFileFrom(INCLUDED_CHANGELOG_DIR, '2-second', '.groovy', """
databaseChangeLog {
  changeSet(author: 'ssaliman', id: '${SECOND_INCLUDED_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(30)')
    }
  }
}
""")

        createFileFrom(INCLUDED_CHANGELOG_DIR, '4-fourth', '.sql', """
--liquibase formatted sql

--changeset ssaliman:${FOURTH_INCLUDED_CHANGE_SET}
alter table monkey add gender varchar(1);
""")

        // alphabetically last, but in a "3-" subdir, placing it third.
        createFileFrom(INCLUDED_CHANGELOG_SUB_DIR, 'third', '.groovy', """
databaseChangeLog {
  changeSet(author: 'ssaliman', id: '${THIRD_INCLUDED_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'location', type: 'varchar(30)')
    }
  }
}
""")


        return INCLUDED_CHANGELOG_DIR.path.replaceAll("\\\\", "/")
    }

    private File createFileFrom(directory, suffix, text) {
        createFileFrom(directory, 'liquibase-', suffix, text)
    }

    private File createFileFrom(directory, prefix, suffix, text) {
        def file = File.createTempFile(prefix, suffix, directory)
        file << text
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
    private def verifyIncludedPreconditions(rootChangeLog) {
        def preconditions = extractPreconditions rootChangeLog.preconditionContainer?.nestedPreconditions
        assertNotNull preconditions
        assertEquals 2, preconditions.size()
        assertTrue preconditions[0] instanceof DBMSPrecondition
        assertEquals 'mysql', preconditions[0].type
        assertTrue preconditions[1] instanceof RunningAsPrecondition
        assertEquals 'ssaliman', preconditions[1].username
    }

    /**
     * Helper method to extract the actual preconditions from a list of potential preconditions.
     * <p>
     * Liquibase often nests the actual preconditions in a precondition container.  This method
     * will walk through a collection of objects, extracting preconditions and recursively checking
     * nested items in a container to get just the preconditions themselves.
     * @param preconditions the collection of preconditions to search
     * @return a list of actual preconditions.
     */
    private def extractPreconditions(preconditions) {
        def actualPreconditions = []
        preconditions?.each { pc ->
            if ( pc instanceof PreconditionContainer ) {
                actualPreconditions.addAll extractPreconditions(pc.nestedPreconditions)
            } else if ( pc instanceof Precondition) {
                actualPreconditions.add pc
            }
        }
        return actualPreconditions
    }
}

