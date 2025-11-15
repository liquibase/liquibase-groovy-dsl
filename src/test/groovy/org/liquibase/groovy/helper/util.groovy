package org.liquibase.groovy.helper

import groovy.transform.TupleConstructor
import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.parser.ChangeLogParserFactory
import liquibase.parser.ext.GroovyLiquibaseChangeLogParser
import liquibase.parser.groovy.exception.*
import liquibase.precondition.Precondition
import liquibase.precondition.core.PreconditionContainer
import org.apache.groovy.util.BeanUtils
import org.liquibase.groovy.delegate.ChangeSetDelegate
import org.liquibase.groovy.delegate.DatabaseChangeLogDelegate
import org.liquibase.groovy.delegate.Delegatee
import liquibase.resource.ResourceAccessor

import java.nio.charset.StandardCharsets
import java.lang.reflect.Field
import java.sql.Timestamp
import java.text.SimpleDateFormat

import static groovy.lang.Closure.DELEGATE_ONLY
import static org.junit.Assert.assertEquals
import static liquibase.parser.ext.GroovyLiquibaseChangeLogParser.*
import static org.junit.Assert.assertNotNull
import static org.liquibase.groovy.delegate.DelegateUtil.callOn

@groovy.transform.CompileStatic
class util {
    static final String ROOT_CHANGELOG_PATH = "src/test/changelog"

    // This one is not a real file, but it looks like a legit file.  It is used by tests that
    // build changelogs on the fly.
    static final String MOCK_CHANGELOG = "${ROOT_CHANGELOG_PATH}/mock-changelog.groovy"

    /** parserFactory with GroovyLiquibaseChangeLogParser registered */
    static @Lazy ChangeLogParserFactory parserFactory = registerParser()
    static ChangeLogParserFactory registerParser() {
        ChangeLogParserFactory.instance.register(new GroovyLiquibaseChangeLogParser())
        ChangeLogParserFactory.instance
    }

    /** Get registered parser */
    static DatabaseChangeLog parseDatabaseChangeLog(File changeLogFile, ResourceAccessor resourceAccessor) {
        parserFactory.getParser(changeLogFile.path, resourceAccessor)
            .parse(changeLogFile.path, new ChangeLogParameters(), resourceAccessor)
    }

    /** Parse a changeLog from String {@code content} */
    static DatabaseChangeLog parseDatabaseChangeLog( ResourceAccessor resourceAccessor, String content) {
        parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), resourceAccessor)
    }

    /** Parse a changeLog from String {@code content} */
    static DatabaseChangeLog parseDatabaseChangeLog(String content) {
        parseDatabaseChangeLog null, content
    }

    static File createFileFrom(File directory, String suffix, String text) {
        createFileFrom(directory, 'liquibase-', suffix, text)
    }

    /** Create a temporary file in the {@code directory} with the name {prefix}{suffix} containing the
     * {text}
     * @return the created file
     */
    static File createFileFrom(File directory, String prefix, String suffix, String text) {
        def file = File.createTempFile(prefix, suffix, directory)
        file << text
    }

    static <I,O> IO io( I i, O o) { new IO ( i, o)}
    @TupleConstructor()
    static class IO<I,O> { I i; O o }

    static failedCase(String c, Throwable t){
        throw new AssertionError("Case '$c' thrown: $t.class.simpleName: $t.message", t)
    }

    @Category(Delegatee)
    static class DelegateeCategory<Tag> {

        String invalidElement(String name) {
            this.prefix( new UnrecognizedElement(name, this.knownElements())).message
        }

        /** Convert Tag to String + set prefix*/
        String missingClosure(Tag name) {
            this.prefix(new MissingClosure( name as String)).message
        }

        /** Convert Tag to String */
        String invalidArgs(Tag name, Object... args) {
            this.prefix(new InvalidArguments( name as String, '',null, args)).message.dropRight(1)
        }

        /** Convert Tag to String */
        String attributeSetTwice(Tag tag, String attribName ) {
            this.prefix(new ArgumentSetTwice( tag as String, attribName)).message
        }
    }


    /** Validate if all keys in {@code expected} are set as property in {@code actual} and the values are matching
        If the type do not match their String representation is compared
     */
    static void assertPropsSet(Map expected, actual) {
        assertNotNull actual
        assertMapEquals expected as Map<String, Object>, actual
    }

    /** Validate if all keys in {@code expected} are also in {@code actual} and the values are matching
        If the type do not match compare their String representation
      */
    static void assertMapEquals(Map<String, Object> expected, actual) {
        expected.each { key, exp ->
            def act
            try { 
                act = actual[key]
            } catch (MissingPropertyException e) { // Nasty fallback in groovy 4
                MetaMethod m = actual.metaClass.pickMethod('is'+ BeanUtils.capitalize(key),null)
                if(m){
                    act = m.invoke(actual, GString.EMPTY_OBJECT_ARRAY)
                }
//                def mmethods = actual.metaClass.metaMethods
//                def methods = actual.metaClass.methods
//                def props = actual.properties
//                def mprops = actual.metaPropertyValues
//                def mmprops = actual.metaClass.properties
//                MetaProperty q = actual.metaClass.getMetaProperty(key)
//
//                //def b = actual['is'+ BeanUtils.capitalize(key)]
//                println(e)

            }
            // Use String representation if different types
            if(null != act && exp != null && exp.class != act.class) {
                act = act as String // Convert actual
                if ( exp.class != String ) { // If we have only the string representation
                    exp = exp as String // Convert both to String
                }
            }
            assertEquals key, exp, act
        }
    }

    /** Get the last ChangeLogParameter using reflection
     * @param global Get the last global or local changeLogParameter
     * @return ChangeLogParameters.ChangeLogParameter
     */
    static def lastParam(DatabaseChangeLog changeLog, boolean global = true) {
        // change log parameters are not exposed through the API, so get them using reflection.
        // Also, there are
        def changeLogParameters = changeLog.changeLogParameters
        Field f = changeLogParameters.class.getDeclaredField((global ? "global" : "local")+"Parameters")
        f.setAccessible(true)
        def properties = f.get(changeLogParameters)
        //def properties = m.invoke(changeLogParameters, key, null, null)//f.get(changeLogParameters)
        global ? (properties as List).last() :
                ((properties as Map).get(changeLog.physicalFilePath) as List).last() // The last one is ours.
    }

    static String makeRelativeTo(File f, String root) {
        // Now make it relative - add 1 to the index to eat the "/"
        f.path.replaceAll("\\\\", "/")
                .substring(root.length() +1)
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
    static List<Precondition> extractPreconditions(PreconditionContainer preconditions) {
        List<Precondition> actualPreconditions = []
        preconditions.nestedPreconditions.each { pc ->
            if ( pc instanceof PreconditionContainer ) {
                actualPreconditions.addAll extractPreconditions(pc)
            } else if ( pc instanceof Precondition) {
                actualPreconditions.add pc
            }
        }
        return actualPreconditions
    }

    /** Parent for DatabaseChangeLog tests */
    static class DatabaseChangeLogTests {
        static final String TMP_CHANGELOG_PATH = ROOT_CHANGELOG_PATH + "/tmp"
        ResourceAccessor resourceAccessor
        /**
         * Helper method that builds a changeSet from the given closure.  Tests will use this to test
         * parsing the various closures that make up the Groovy DSL.
         * @param closure the closure containing changes to parse.
         * @return the changeSet, with parsed changes from the closure added.
         */
        DatabaseChangeLog buildChangeLog(clParams = null,
                @DelegatesTo(value = DatabaseChangeLogDelegate, strategy = DELEGATE_ONLY) Closure closure) {
            return buildChangeLog(null, clParams, closure)
        }

        /**
         * Helper method that builds a changeSet from the given closure.  Tests will use this to test
         * parsing the various closures that make up the Groovy DSL.
         * @param closure the closure containing changes to parse.
         * @return the changeSet, with parsed changes from the closure added.
         */
        DatabaseChangeLog buildChangeLog(ChangeLogParameters parameters, clParams = null,
           @DelegatesTo(value = DatabaseChangeLogDelegate, strategy=DELEGATE_ONLY) Closure closure) {
            def changelog = new DatabaseChangeLog(MOCK_CHANGELOG)
            if ( parameters == null ) {
                changelog.changeLogParameters = new ChangeLogParameters()
            } else {
                changelog.changeLogParameters = parameters
            }
            callOn(new DatabaseChangeLogDelegate(changelog, resourceAccessor)
                 ,closure, clParams)
            return changelog
        }
    }

    public static final SimpleDateFormat simpleDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    /**
     * Small helper to parse a string into a Timestamp
     * @param dateTimeString the string to parse
     * @return the parsed string
     */
    public static Timestamp parseSqlTimestamp(String dateTime) {
        new Timestamp(simpleDF.parse(dateTime).time)
    }
/*
    static class ChangeSpec extends Specification {
        String ID = 'changeset-id'
        String AUTHOR = 'tlberglund'
        String FILEPATH = '/filePath'
        String CONTEXT = 'mycontext'
        String DBMS = 'mysql'

        ChangeSet newChangeSet(String id = ID, String author = AUTHOR,
                                  boolean alwaysRun = false, boolean runOnChange = false,
                                  String filePath = FILEPATH, String contextFilter = CONTEXT,
                                  String dbmsList = DBMS) {
            def changeLog = new DatabaseChangeLog(filePath)
            changeLog.changeLogParameters = new ChangeLogParameters()
            new ChangeSet(id, author, alwaysRun, runOnChange, filePath, contextFilter, dbmsList,
                    changeLog)
        }

        *//**
         * Helper method that builds a changeSet from the given closure.  Tests will use this to test
         * parsing the various closures that make up the Groovy DSL.
         * @param closure the closure containing changes to parse.
         * @return the changeSet, with parsed changes from the closure added.
         */
    static ChangeSetDelegate buildBaseChangeSetDelegate() {
        def changelog = new DatabaseChangeLog()
        changelog.changeLogParameters = new ChangeLogParameters()
        ChangeSet changeSet = new ChangeSet(changelog)
        changelog.addChangeSet(changeSet)
        new ChangeSetDelegate(changeSet )
    }
}

