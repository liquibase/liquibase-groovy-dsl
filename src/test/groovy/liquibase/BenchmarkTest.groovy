package liquibase

import groovy.transform.CompileStatic
import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.DatabaseChangeLog
import liquibase.parser.ChangeLogParser
import liquibase.parser.ChangeLogParserFactory
import liquibase.parser.ext.GroovyLiquibaseChangeLogParser
import liquibase.resource.DirectoryResourceAccessor
import liquibase.resource.ResourceAccessor
import liquibase.serializer.core.string.StringChangeLogSerializer
import org.liquibase.groovy.delegate.DelegateUtil
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.annotations.Scope
import spock.lang.Shared

import java.nio.file.Path
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@CompileStatic
class BenchmarkTest {
	static final String commonTestChangelog = 'changelogs/common.tests'
	static final String commonTestChangelogNoInc = commonTestChangelog + '.noinc'
	static final File commonTestChangelogRoot = new File('src/test/resources')
	static @Lazy ResourceAccessor ra = new FolderResourceAccessor(commonTestChangelogRoot)
	static ResourceAccessor raProj = new DirectoryResourceAccessor(Path.of('.'))

	//@Benchmark
	 static DatabaseChangeLog parseGroovy() {
		parseToChangeLog(commonTestChangelogNoInc+'.groovy', raProj)
	}

	@Benchmark
	static DatabaseChangeLog parseXML() {
		parseToChangeLog(commonTestChangelogNoInc+'.xml', ra)
	}

	//@Benchmark
	static DatabaseChangeLog compiledGroovy() {
		Class<GroovyScript> cls = Class.forName('changelogs.common_test_noinc')
		GroovyScript script = cls.getDeclaredConstructor().newInstance()
		DatabaseChangeLog groovy = GroovyLiquibaseChangeLogParser.runScript(script, ra)
		groovy
	}

	static String serializeStr(DatabaseChangeLog log, String ignore = '') {
		StringChangeLogSerializer serializer = new StringChangeLogSerializer()
		//ChangeLogSerializer serializer = new GroovyChangeLogSerializer()
		//ChangeLogSerializer serializer = new XMLChangeLogSerializer()
		boolean pretty = false
		try(PrintWriter p = new PrintWriter(log.physicalFilePath+'.txt' )) {
			String r = serializer.serialize(log.preconditions, pretty)
			r += log.changeSets.inject (new DelegateUtil.CollectionStringBuilder()) { c, ch ->
				c << serializer.serialize(ch, pretty).replace(ignore,'')
			}
			p.write(r)
			p.flush()
			return r
		}
	}

	static DatabaseChangeLog parseToChangeLog (String fileName, ResourceAccessor ra,
															 ChangeLogParameters changeLogParameters = new ChangeLogParameters()) {
		ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser(fileName, ra)
		parser.parse(fileName, changeLogParameters, ra)
	}

	static void main(String[] args) {

		//DatabaseChangeLog groovy = parseToChangeLog(commonTestsChangelog+'.groovy', ra)
		DatabaseChangeLog groovy = compiledGroovy()

		//DatabaseChangeLog xml = parseXML()
		serializeStr(groovy)
	}
}

