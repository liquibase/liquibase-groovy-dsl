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

import liquibase.change.core.SQLFileChange
import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.DatabaseChangeLog
import liquibase.database.ObjectQuotingStrategy
import liquibase.exception.ChangeLogParseException
import liquibase.exception.LiquibaseException
import liquibase.parser.ChangeLogParserFactory
import liquibase.parser.ext.GroovyLiquibaseChangeLogParser
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.DirectoryResourceAccessor
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

/**
 * One of several test classes for the {@link DatabaseChangeLogDelegate}.  The number of tests for
 * {@link DatabaseChangeLogDelegate} were getting unwieldy, so they were split up.  this class deals
 * with all the "includeAllSql" element of a database changelog.
 * <p>
 * Most of these tests are concerned with making sure we find the right files with the different
 * permutations of ResourceAccessor options like paths relative to changelogs vs. the working dir,
 * filters, comparators, depth, etc.  These tests make sure includeAllSql create the right change
 * sets in the right order.  There is one test at the end of this class that sets all the attributes
 * that aren't related to finding a file, such as id prefixes, encoding, etc. to make sure they
 * find their way into the generated changeSet and sqlFile change.
 * <p>
 * For the tests in this class, it is helpful to remember that minDepth and maxDepth are 1-based,
 * starting with the include dir itself.  So minDepth of 1 starts including files in the specified
 * directory and below, maxDepth of 1 includes files in the specified directory but not below, and
 * so on.
 * <p>
 * Also note that other "include" type tests use the path of the included change to make sure the
 * right files get included, but includeAllSql works a little differently,  It doesn't include a
 * file that makes a change set, it makes the change set directly, which means the change set's file
 * will be the same for all included changes.  We'll verify that it all worked by looking at the
 * change id instead.
 *
 * @author Steven C. Saliman
 */
class DatabaseChangeLogDelegateIncludeAllSqlTests {
    // Let's define some paths and directories.  These should all be relative.
    static final ROOT_CHANGELOG_PATH = "src/test/changelog"
    static final TMP_CHANGELOG_PATH = ROOT_CHANGELOG_PATH + "/tmp"
    static final INCLUDED_SQL_PATH = TMP_CHANGELOG_PATH + "/sql"
    // The "3-" is important to put it between 2 filenames in the parent.
    static final INCLUDED_SQL_SUB_PATH = TMP_CHANGELOG_PATH + "/sql/3-subdirectory"
    static final TMP_CHANGELOG_DIR = new File(TMP_CHANGELOG_PATH)
    static final INCLUDED_SQL_DIR = new File(INCLUDED_SQL_PATH)
    static final INCLUDED_SQL_SUB_DIR = new File(INCLUDED_SQL_SUB_PATH)
    static final ROOT_CHANGE_SET = 'root-change-set'
    // constants for 4 included changesets.  one of them is a sql file, to test filters.  One is
    // alphabetically last, but in in a subdirectory whose name is between two files in the parent,
    // and therefore will be included 3rd.  These constants need to match the ids in
    // src/test/resources/include to properly test the classpath includes.  These constants are
    // prefixes to the change set ids, since the full id is based on the temporary filename, which
    // has numbers in it.
    static final FIRST_INCLUDED_CHANGE_PREFIX = '1-first'
    static final SECOND_INCLUDED_CHANGE_PREFIX = '2-second'
    static final THIRD_INCLUDED_CHANGE_PREFIX = 'third'  // the one in the subdir
    static final FOURTH_INCLUDED_CHANGE_PREFIX = '4-fourth' // the filtered file
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
        INCLUDED_SQL_DIR.mkdirs()
        INCLUDED_SQL_SUB_DIR.mkdirs()
    }

    /**
     * Test including a path when we have an unsupported attribute.
     */
    @Test(expected = ChangeLogParseException)
    void includeAllSqlInvalidAttribute() {
        buildChangeLog {
            includeAllSql(changePath: 'invalid')
        }
    }

    /**
     * Try including all SQL files in a directory.  For this test, we want a path that contains an
     * invalid token.  The easiest way to do that is to simply use a token that doesn't have a
     * matching property.
     */
    @Test(expected = ChangeLogParseException)
    void includeAllSqlWithInvalidProperty() {
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '\${includedChangeLogDir}')
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
     * Try including all SQL in a directory.  For this test, we want 4 files to make sure we
     * include them all in the right order, with subdirectory change between base dir changes.
     * This test makes sure that tokens don't affect paths.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllSqlWithValidToken() {
        def includedChangeLogDir = createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  property(name: 'includeDir', value: '${includedChangeLogDir}')
  includeAllSql(path: '\${includeDir}')
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
        assertTrue changeSets[0].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[1].id.startsWith(SECOND_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[2].id.startsWith(THIRD_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[3].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/1-first")
        assertTrue changeSets[1].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/2-second")
        assertTrue changeSets[2].changes[0].path.startsWith("${INCLUDED_SQL_SUB_PATH}/third")
        assertTrue changeSets[3].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/4-fourth")
    }

    /**
     * Try including all SQL in a directory.  For this test, we want 4 files to make sure we
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
    void includeAllSqlRelativeToWorkDir() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_PATH}', context: 'override', contextFilter: 'myContext')
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
        assertTrue changeSets[0].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[1].id.startsWith(SECOND_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[2].id.startsWith(THIRD_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[3].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/1-first")
        assertTrue changeSets[1].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/2-second")
        assertTrue changeSets[2].changes[0].path.startsWith("${INCLUDED_SQL_SUB_PATH}/third")
        assertTrue changeSets[3].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/4-fourth")

        // Take a look at the contexts of the changes.  The first 4, came from the included file,
        // and should have contexts.  The 4th one came from the root changelog and should not.
        assertEquals 'myContext', changeSets[0].contextFilter.toString()
        assertEquals 'myContext', changeSets[1].contextFilter.toString()
        assertEquals 'myContext', changeSets[2].contextFilter.toString()
        assertEquals 'myContext', changeSets[3].contextFilter.toString()
        assertNull changeSets[4].changeLog.includeContextFilter
    }

    /**
     * Try including all SQL in a directory relative to a changelog that uses a relative path.
     * This test is looking at the relativeToChangeLogFile parameter.  This test also sets a context
     * without a contextFilter to make sure we handle the old parameter
     */
    @Test
    void includeAllSqlRelativeToRelativeChangeLog() {
        createIncludedSqlFiles()
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: 'sql', relativeToChangelogFile: true, context: 'myContext')
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
        assertTrue changeSets[0].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[1].id.startsWith(SECOND_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[2].id.startsWith(THIRD_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[3].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/1-first")
        assertTrue changeSets[1].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/2-second")
        assertTrue changeSets[2].changes[0].path.startsWith("${INCLUDED_SQL_SUB_PATH}/third")
        assertTrue changeSets[3].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/4-fourth")

        // Take a look at the contexts of the changes.  The first 4, came from the included file,
        // and should have contexts.  The 4th one came from the root changelog and should not.
        assertEquals 'myContext', changeSets[0].contextFilter.toString()
        assertEquals 'myContext', changeSets[1].contextFilter.toString()
        assertEquals 'myContext', changeSets[2].contextFilter.toString()
        assertEquals 'myContext', changeSets[3].contextFilter.toString()
        assertNull changeSets[4].changeLog.includeContextFilter
    }

    /**
     * Try including all SQL in a directory relative to a changelog that uses a relative path.
     * This test is looking at the relativeToChangeLogFile parameter.  For this test, the included
     * sql is not in the same directory as (or a subdirectory of) the root changelog.  The
     * main thing here is to make sure paths like "../somedir" work.
     */
    @Test
    void includeAllSqlRelativeToRelativeChangeLogParent() {
        createIncludedSqlFiles()
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '../tmp/sql', relativeToChangelogFile: true)
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
        assertTrue changeSets[0].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[1].id.startsWith(SECOND_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[2].id.startsWith(THIRD_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[3].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/1-first")
        assertTrue changeSets[1].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/2-second")
        assertTrue changeSets[2].changes[0].path.startsWith("${INCLUDED_SQL_SUB_PATH}/third")
        assertTrue changeSets[3].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/4-fourth")
    }

    /**
     * Try including all SQL in a directory, but with a resource filter.  For this test, we'll
     * repeat wanting 4 files, but with a filter that excludes 3 of them. Test may fail because of
     * unclean directories. Fix the other tests first.
     */
    @Test
    void includeAllSqlValidWithFilter() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_PATH}',
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
        assertTrue changeSets[0].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/1-first")
    }

    /**
     * Try including all SQL in a directory with an invalid resource filter.  For this test, we'll
     * use a class that doesn't exist.
     */
    @Test(expected = ChangeLogParseException)
    void includeAllSqlValidWithInvalidFilter() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_PATH}',
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
     * Try including all SQL in a directory with an invalid resource filter.  For this test, we'll
     * use a class that exists, but isn't an {@code IncludeAllFilter}.
     */
    @Test(expected = ChangeLogParseException)
    void includeAllSqlValidWithInappropriateFilter() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_PATH}',
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
     * Try including all SQL in a directory, but with an "endsWithFilter.  For this test, we'll
     * include a directory with 3 files, but with a filter that only matches one of them.  Test may
     * fail because of unclean directories. Fix the other tests first.
     */
    @Test
    void includeAllSqlEndsWithFilter() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_PATH}',
             endsWithFilter: 'qry')
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
        assertTrue changeSets[0].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/4-fourth")
    }

    /**
     * Try including all SQL in a directory when we provide a custom comparator.  For this test,
     * we'll repeat includeAllSqlRelativeToWorkDir, but with a comparator that sorts the included
     * changes in reverse alphabetical order.  We still expect 4 files, but this time, they should
     * come in to the changes in reverse order.
     * <p>
     * This test will also make sure we can handle contexts properly.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllSqlWithComparator() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_PATH}',
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
        assertTrue changeSets[0].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[1].id.startsWith(THIRD_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[2].id.startsWith(SECOND_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[3].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/4-fourth")
        assertTrue changeSets[1].changes[0].path.startsWith("${INCLUDED_SQL_SUB_PATH}/third")
        assertTrue changeSets[2].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/2-second")
        assertTrue changeSets[3].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/1-first")
    }

    /**
     * Try including all SQL in a directory when we provide an invalid custom comparator.  For
     * this test, we'll use a class that doesn't exist.
     */
    @Test(expected = ChangeLogParseException)
    void includeAllSqlWithInvalidComparator() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_PATH}',
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
     * Try including all SQL in a directory when we provide an invalid custom comparator.  For
     * this test, we'll use a class that does exist, but is not a {@code Comparator}
     */
    @Test(expected = ChangeLogParseException)
    void includeAllSqlWithInappropriateComparator() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_PATH}',
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
     * Try including all SQL when the path doesn't exist is invalid.  Expect an error.
     */
    @Test(expected = LiquibaseException)
    void includeAllSqlInvalidPath() {
        buildChangeLog {
            includeAllSql(path: 'invalid')
        }
    }

    /**
     * Try including all SQL when the path doesn't exist is invalid, but we've set the
     * errorIfMissingOrEmpty property to false.  For this test, we'll use a string to represent
     * falseness.
     */
    @Test
    void includeAllSqlInvalidPathIgnoreError() {
        def changeLog = buildChangeLog {
            includeAllSql(path: 'invalid', errorIfMissingOrEmpty: false)
        }
        assertNotNull changeLog
        def changeSets = changeLog.changeSets
        assertNotNull changeSets
        assertEquals 0, changeSets.size()
    }

    /**
     * Try including all SQL when the path is valid, but there are no usable files in the directory.
     * We'll test this by using the filter to eliminate the one change set we'll create to make sure
     * we do the test after the filter.
     */
    @Test(expected = LiquibaseException)
    void includeAllSqlEmptyPath() {
        // This file should be excluded by the resource filter.
        createFileFrom(INCLUDED_SQL_DIR, '2-second', '.sql', """
insert into monkey(name, emotion) values ('sandy', 'sad');
""")

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_PATH}',
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
     * Try including all SQL when the path is valid, but there are no usable files in the directory.
     * This time, we'll set the errorIfMissingOrEmpty property to false.  For this test, we'll use
     * a boolean to represent falseness.  We should ignore the error about the empty directory, and
     * get the root change set from the parent file.
     */
    @Test
    void includeAllSqlEmptyPathIgnoreError() {
        // This file should be excluded by the resource filter.
        createFileFrom(INCLUDED_SQL_DIR, '2-second', '.sql', """
insert into monkey(name, emotion) values ('sandy', 'sad');
""")
        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_PATH}', errorIfMissingOrEmpty: false,
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
    }

    //-------------------------------------------------------------------------------------------
    // Tests of the includeAllSql method when the changelog file is accessed via the classpath.

    /**
     * Try including all SQL files in a classpath directory.  We'll want to make sure we include
     * them both, and in the right order. with the xml change excluded and the subdirectory change
     * last.
     * <p>
     * These SQL files can't be created on the fly, they must exist in a directory that is on the
     * classpath, and we need to replace the resource accessor with one that can load a file from
     * the classpath.
     */
    @Test
    void includeAllSqlClasspath() {
        def rootChangeLogFile = "changelog-sql.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 5, changeSets.size()
        assertTrue changeSets[0].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[1].id.startsWith(SECOND_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[2].id.startsWith(THIRD_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[3].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[4].id

        // Make sure each included change is for the expected file
        // Note that classpath resources are relative to the classpath root, and won't begin with
        // a "/"
        def relativePath = INCLUDED_SQL_PATH.substring(TMP_CHANGELOG_PATH.length()+1)
        assertTrue changeSets[0].changes[0].path.startsWith("${relativePath}/1-first")
        assertTrue changeSets[1].changes[0].path.startsWith("${relativePath}/2-second")
        assertTrue changeSets[2].changes[0].path.startsWith("${relativePath}/3-subdirectory/third")
        assertTrue changeSets[3].changes[0].path.startsWith("${relativePath}/4-fourth")
    }

    /**
     * Try including all SQL in a classpath directory, but with a resource filter. For this test,
     * we'll have 3 files in the directory, but the resource filter will exclude two of them.
     * <p>
     * The SQL files can't be created on the fly, they must exist in a directory that is on the
     * classpath, and we need to replace the resource accessor with one that can load a file from
     * the classpath.
     */
    @Test
    void includeAllSqlClasspathWithFilter() {
        def rootChangeLogFile = "filtered-changelog-sql.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()  // from the first file, and the changelog itself.
        assertTrue changeSets[0].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Make sure each included change is for the expected file
        // Note that classpath resources are relative to the classpath root, and won't begin with
        // a "/"
        def relativePath = INCLUDED_SQL_PATH.substring(TMP_CHANGELOG_PATH.length()+1)
        assertTrue changeSets[0].changes[0].path.startsWith("${relativePath}/1-first")
    }

    /**
     * Try including all SQL files in a classpath directory, but with an ends-with filter. For this
     * test, we'll have 3 files in the directory, but the filter will only match one of them.
     * <p>
     * The SQL files can't be created on the fly, they must exist in a directory that is on the
     * classpath, and we need to replace the resource accessor with one that can load a file from
     * the classpath.
     */
    @Test
    void includeAllSqlClasspathEndsWithWithFilter() {
        def rootChangeLogFile = "ends-with-changelog-sql.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()  // from the first file, and the changelog itself.
        assertTrue changeSets[0].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Make sure each included change is for the expected file
        // Note that classpath resources are relative to the classpath root, and won't begin with
        // a "/"
        def relativePath = INCLUDED_SQL_PATH.substring(TMP_CHANGELOG_PATH.length()+1)
        assertTrue changeSets[0].changes[0].path.startsWith("${relativePath}/4-fourth")
    }

    /**
     * Try including all SQL from a classpath loaded change log when the include path doesn't exist
     * is invalid.  Expect an error.
     * <p>
     * The SQL files can't be created on the fly, they must exist in a directory that is on the
     * classpath, and we need to replace the resource accessor with one that can load a file from
     * the classpath.
     */
    @Test(expected = LiquibaseException)
    void includeAllSqlInvalidClassPath() {
        def rootChangeLogFile = "invalid-changelog-sql.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)
    }

    /**
     * Try including all SQL from a classpath loaded change log when the include path is invalid,
     * but we've set the errorIfMissingOrEmpty property to false.
     * <p>
     * The change logs can't be created on the fly, it must exist in a directory that is on the
     * classpath, and we need to replace the resource accessor with one that can load a file from
     * the classpath.
     */
    @Test
    void includeAllSqlInvalidClassPathIgnoreError() {
        def rootChangeLogFile = "ignore-changelog-sql.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 1, changeSets.size()  // from the first file, and the changelog itself.
        assertEquals ROOT_CHANGE_SET, changeSets[0].id

        assertTrue changeSets[0].filePath.equals('ignore-changelog-sql.groovy')
    }

    /**
     * Try including all SQL files in a directory, but with a specified minDepth of 2.  For this
     * test, we expect to exclude the files in the include directory, and only return the one in the
     * subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllSqlRelToWorkDirMinDepth() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_DIR}', minDepth: 2)
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
        assertTrue changeSets[0].id.startsWith(THIRD_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_SUB_PATH}/third")
    }

    /**
     * Try including all SQL files in a directory, but with a maxDepth of 1.  For this test, we
     * expect to exclude the file in the subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllSqlRelToWorkDirMaxDepth() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_DIR}', maxDepth: 1)
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
        assertTrue changeSets[0].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[1].id.startsWith(SECOND_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[2].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[3].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/1-first")
        assertTrue changeSets[1].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/2-second")
        assertTrue changeSets[2].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/4-fourth")
    }

    /**
     * Try including all SQL files in a directory, but with a specified minDepth of 2.  For this
     * test, we expect to exclude the files in the sql directory, and only return the one in the
     * subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllSqlRelToChangeLogMinDepth() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: 'sql', relativeToChangelogFile: true, minDepth: 2)
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
        assertTrue changeSets[0].id.startsWith(THIRD_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_SUB_PATH}/third")
    }

    /**
     * Try including all SQL files in a directory, but with a maxDepth of 1.  For this test, we
     * expect to exclude the file in the subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllSqlRelToChangeLogMaxDepth() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: 'sql', relativeToChangelogFile: true, maxDepth: 1)
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
        assertTrue changeSets[0].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[1].id.startsWith(SECOND_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[2].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[3].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/1-first")
        assertTrue changeSets[1].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/2-second")
        assertTrue changeSets[2].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/4-fourth")
    }

    /**
     * Try including all SQL files in a directory, but with a specified minDepth of 2.  For this
     * test, we expect to exclude the files in the include directory, and only return the one in the
     * subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllSqlRelToChangeLogParentMinDepth() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '../tmp/sql', relativeToChangelogFile: true, minDepth: 2)
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
        assertTrue changeSets[0].id.startsWith(THIRD_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_SUB_PATH}/third")
    }

    /**
     * Try including all SQL files in a directory, but with a maxDepth of 1.  For this test, we
     * expect to exclude the file in the subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllSqlRelToChangeLogParentMaxDepth() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '../tmp/sql', relativeToChangelogFile: true, maxDepth: 1)
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
        assertTrue changeSets[0].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[1].id.startsWith(SECOND_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[2].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[3].id

        // Make sure each included change is for the expected file
        assertTrue changeSets[0].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/1-first")
        assertTrue changeSets[1].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/2-second")
        assertTrue changeSets[2].changes[0].path.startsWith("${INCLUDED_SQL_PATH}/4-fourth")
    }

    /**
     * Try including all SQL files in a directory, but with a specified minDepth of 2.  For this
     * test, we expect to exclude the files in the include directory, and only return the one in the
     * subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllSqlClasspathMinDepth() {
        def rootChangeLogFile = "changelog-min-sql.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 2, changeSets.size()
        assertTrue changeSets[0].id.startsWith(THIRD_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[1].id

        // Make sure each included change is for the expected file
        // Note that classpath resources are relative to the classpath root, and won't begin with
        // a "/"
        def relativePath = INCLUDED_SQL_SUB_PATH.substring(TMP_CHANGELOG_PATH.length()+1)
        assertTrue changeSets[0].changes[0].path.startsWith("${relativePath}/third")
    }

    /**
     * Try including all SQL files in a directory, but with a maxDepth of 1.  For this test, we
     * expect to exclude the file in the subdirectory.
     * <p>
     * Note: when other tests throw exceptions, this test may also fail because of unclean
     * directories.  Fix the other tests first.
     */
    @Test
    void includeAllSqlClasspathMaxDepth() {
        def rootChangeLogFile = "changelog-max-sql.groovy"
        resourceAccessor = new ClassLoaderResourceAccessor()

        def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
        def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

        assertNotNull rootChangeLog
        def changeSets = rootChangeLog.changeSets
        assertNotNull changeSets
        assertEquals 4, changeSets.size()
        assertTrue changeSets[0].id.startsWith(FIRST_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[1].id.startsWith(SECOND_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSets[2].id.startsWith(FOURTH_INCLUDED_CHANGE_PREFIX)
        assertEquals ROOT_CHANGE_SET, changeSets[3].id

        // Make sure each included change is for the expected file
        // Note that classpath resources are relative to the classpath root, and won't begin with
        // a "/"
        def relativePath = INCLUDED_SQL_PATH.substring(TMP_CHANGELOG_PATH.length()+1)
        assertTrue changeSets[0].changes[0].path.startsWith("${relativePath}/1-first")
        assertTrue changeSets[1].changes[0].path.startsWith("${relativePath}/2-second")
        assertTrue changeSets[2].changes[0].path.startsWith("${relativePath}/4-fourth")
    }

    //============================================================================================

    /**
     * Try including all SQL in a directory, but with all the changeSet and sqlFile related
     * parameters set.  This test will make sure that we populate the changeSet and sqlFile change
     * the right way based on the given options.  To keep things simple, we'll use a resource filter
     * that results in only one changeSet being created.
     * <p>
     * This test doesn't test the context filter, we took care of that in previous tests.
     * <p>
     * Test may fail because of
     * unclean directories. Fix the other tests first.
     */
    @Test
    void includeAllSqlValidWithAll() {
        createIncludedSqlFiles()

        def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  includeAllSql(path: '${INCLUDED_SQL_PATH}',
             filter: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter',
             author: 'Steve Saliman',
             dbms: 'postgresql',
             runAlways: true,
             runOnChange: false,
             labels: 'test_label',
             failOnError: true, 
             onValidationFail: 'MARK_RAN',
             objectQuotingStrategy: 'QUOTE_ONLY_RESERVED_WORDS',
             created: 'test_created',
             ignore: true,
             logicalFilePath: 'logical/path',
             runWith: 'my_executor',
             runWithSpoolFile: 'my.log',
             idKeepsExtension: true,
             idPrefix: 'my_prefix-',
             idSuffix: '-my_suffix',
             encoding: 'UTF-8',
             endDelimiter: '@',
             splitStatements: false,
             stripComments: true)
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

        def changeSet = changeSets[0]
        // The id is based on the included filename, but the included filename has random numbers
        // in it, so we need to make sure the id starts with the given prefix and the start of the
        // file name, and ends with the suffix.  Remember, in this test, we set idKeepExtension to
        // true, overriding the default.
        assertTrue changeSet.id.startsWith("my_prefix-" + FIRST_INCLUDED_CHANGE_PREFIX)
        assertTrue changeSet.id.endsWith(".sql-my_suffix")
        assertEquals 'Steve Saliman', changeSet.author
        assertTrue changeSet.alwaysRun // the property doesn't match xml or docs.
        assertFalse changeSet.runOnChange
        assertEquals 'test_label', changeSet.labels.toString()
        assertEquals 'postgresql', changeSet.dbmsSet.toArray()[0]
        assertTrue changeSet.failOnError
        assertEquals "MARK_RAN", changeSet.onValidationFail.toString()
        assertEquals ObjectQuotingStrategy.QUOTE_ONLY_RESERVED_WORDS, changeSet.objectQuotingStrategy
        assertEquals 'test_created', changeSet.created
        assertTrue changeSet.ignore
        assertEquals 'logical/path', changeSet.logicalFilePath
        assertEquals 'my_executor', changeSet.runWith
        assertEquals 'my.log', changeSet.runWithSpoolFile

        // get sqlFile change
        assertEquals 1, changeSet.changes.size()
        def sqlFileChange = changeSet.changes[0]
        assertTrue sqlFileChange instanceof SQLFileChange
        // The file is randomly generated, so we can't go with an equals, but we know we're good
        // if the file starts the way we expect.
        assertTrue sqlFileChange.path.startsWith(INCLUDED_SQL_PATH + "/" + FIRST_INCLUDED_CHANGE_PREFIX)
        assertFalse sqlFileChange.relativeToChangelogFile
        assertEquals 'UTF-8', sqlFileChange.encoding
        assertTrue sqlFileChange.isStripComments()
        assertFalse sqlFileChange.isSplitStatements()
        assertEquals '@', sqlFileChange.endDelimiter
        assertEquals 'postgresql', sqlFileChange.dbms
        assertNotNull sqlFileChange.resourceAccessor
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
     * Helper method to create changelogs in a directory for testing the includeAllSql methods.  It
     * creates 4 files:
     * <ul>
     * <li>A groovy file that should be considered first, and included first.</li>
     * <li>A groovy file that should be considered second and included second.</li>
     * <li>A groovy file that is last in the alphabet, but in a subdirectory that sorts between the
     * first and fourth files.  This will prove that the default sorting is on the entire path, and
     * this file will be included third.</li>
     * <li>a sql file that should be considered fourth. and included fourth</li>
     * </ul>
     */
    private String createIncludedSqlFiles() {
        createFileFrom(INCLUDED_SQL_DIR, '1-first', '.sql', """
insert into monkey(name, emotion) values ('frank', 'fastidious');
""")

        createFileFrom(INCLUDED_SQL_DIR, '2-second', '.sql', """
insert into monkey(name, emotion) values ('sandy', 'sad');
""")

        createFileFrom(INCLUDED_SQL_DIR, '4-fourth', '.qry', """
insert into monkey(name, emotion) values ('felicia', 'fearless');
""")

        // alphabetically last, but in a "3-" subdir, placing it third.
        createFileFrom(INCLUDED_SQL_SUB_DIR, 'third', '.sql', """
insert into monkey(name, emotion) values('thor', 'thrashed');
""")


        return INCLUDED_SQL_DIR.path.replaceAll("\\\\", "/")
    }

    private File createFileFrom(directory, suffix, text) {
        createFileFrom(directory, 'liquibase-', suffix, text)
    }

    private File createFileFrom(directory, prefix, suffix, text) {
        def file = File.createTempFile(prefix, suffix, directory)
        file << text
    }

}

