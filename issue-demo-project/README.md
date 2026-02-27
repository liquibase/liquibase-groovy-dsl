# issue-demo-project

Self-contained demo that runs **Liquibase** with the **Groovy DSL** against an in-memory **H2** database. 
It is not part of the parent project build, but includes parent into itself build.


## What it does

- **Changelog:** `src/main/resources/db/changelog.groovy` defines one change set that creates table `demo_item` with columns `id` (BIGINT PK) and `name` (VARCHAR 255).
- **Test:** `LiquibaseGroovyDslTest` runs Liquibase update against an in-memory H2 database, then asserts the table and columns exist.


## Steps to repeat NPE issue
The following command does all steps for you:
1. First it builds `liquibase-groovy-dsl` library with the minium versions of liquibase and groovy it supports 
   (we pass versions as arguments `-PliquibaseVersion=4.+ -PgroovyVersion=3.+`, because this is what we have in the 
   [liquibase-groovy-dsl build.gradle](https://github.com/liquibase/liquibase-groovy-dsl/blob/master/gradle.properties#L17))
2. Second it runs the test of `issue-demo-project` that uses `liquibase-groovy-dsl` to apply groovy changesets 
   (a simple demonstration of `liquibase-groovy-dsl` use). Despite the fact that the library is built using one set of 
   liquibase and groovy versions, the consuming project might tend to use the latest dependencies (we pass another 
   versions as arguments `-PtestProjectLiquibaseVersion=4.+ -PtestProjectGroovyVersion=3.+`)

Here is the command:
```bash
./gradlew clean test -PgroovyVersion=3.+ -PtestProjectGroovyVersion=5.0.4
```

Note: 
 - Omits `-PliquibaseVersion=4.+` and `-PtestProjectLiquibaseVersion=4.+` or 5.+ since it does not make difference for 
   the results.
 - Fails/succeds same way for `-PgroovyVersion=4.+` vs `-PgroovyVersion=3.+`.
 - Fails/succeds same way for jdk 11, 17, 21, 25

### NPE Issue stacktrace

```log liquibase-groovy-dsl/issue-demo-project/build/reports/tests/test/classes/demo.LiquibaseGroovyDslTest.html
liquibase.exception.CommandExecutionException: java.lang.NullPointerException: Cannot invoke "Object.hashCode()" because "key" is null
	at app//liquibase.command.CommandScope.lambda$execute$6(CommandScope.java:300)
	at app//liquibase.Scope.child(Scope.java:210)
	at app//liquibase.Scope.child(Scope.java:186)
	at app//liquibase.command.CommandScope.execute(CommandScope.java:241)
	at app//liquibase.Liquibase.lambda$update$0(Liquibase.java:216)
	at app//liquibase.Scope.lambda$child$0(Scope.java:201)
	at app//liquibase.Scope.child(Scope.java:210)
	at app//liquibase.Scope.child(Scope.java:200)
	at app//liquibase.Scope.child(Scope.java:179)
	at app//liquibase.Liquibase.runInScope(Liquibase.java:1333)
	at app//liquibase.Liquibase.update(Liquibase.java:205)
	at app//liquibase.Liquibase.update(Liquibase.java:188)
	at app//demo.LiquibaseGroovyDslTest.liquibaseGroovyDslCreatesTableInH2(LiquibaseGroovyDslTest.java:36)
	at java.base@25.0.2/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
	at java.base@25.0.2/java.lang.reflect.Method.invoke(Method.java:565)
	at app//org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)
	at app//org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
	at app//org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:56)
	at app//org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
	at app//org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
	at app//org.junit.runners.BlockJUnit4ClassRunner$1.evaluate(BlockJUnit4ClassRunner.java:100)
	at app//org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:366)
	at app//org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:103)
	at app//org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:63)
	at app//org.junit.runners.ParentRunner$4.run(ParentRunner.java:331)
	at app//org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:79)
	at app//org.junit.runners.ParentRunner.runChildren(ParentRunner.java:329)
	at app//org.junit.runners.ParentRunner.access$100(ParentRunner.java:66)
	at app//org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:293)
	at app//org.junit.runners.ParentRunner$3.evaluate(ParentRunner.java:306)
	at app//org.junit.runners.ParentRunner.run(ParentRunner.java:413)
	at org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecutor.runTestClass(JUnitTestClassExecutor.java:112)
	at org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecutor.execute(JUnitTestClassExecutor.java:58)
	at org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecutor.execute(JUnitTestClassExecutor.java:40)
	at org.gradle.api.internal.tasks.testing.junit.AbstractJUnitTestClassProcessor.processTestClass(AbstractJUnitTestClassProcessor.java:60)
	at org.gradle.api.internal.tasks.testing.SuiteTestClassProcessor.processTestClass(SuiteTestClassProcessor.java:52)
	at java.base@25.0.2/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
	at java.base@25.0.2/java.lang.reflect.Method.invoke(Method.java:565)
	at org.gradle.internal.dispatch.ReflectionDispatch.dispatch(ReflectionDispatch.java:36)
	at org.gradle.internal.dispatch.ReflectionDispatch.dispatch(ReflectionDispatch.java:24)
	at org.gradle.internal.dispatch.ContextClassLoaderDispatch.dispatch(ContextClassLoaderDispatch.java:33)
	at org.gradle.internal.dispatch.ProxyDispatchAdapter$DispatchingInvocationHandler.invoke(ProxyDispatchAdapter.java:94)
	at jdk.proxy1/jdk.proxy1.$Proxy2.processTestClass(Unknown Source)
	at org.gradle.api.internal.tasks.testing.worker.TestWorker$2.run(TestWorker.java:176)
	at org.gradle.api.internal.tasks.testing.worker.TestWorker.executeAndMaintainThreadName(TestWorker.java:129)
	at org.gradle.api.internal.tasks.testing.worker.TestWorker.execute(TestWorker.java:100)
	at org.gradle.api.internal.tasks.testing.worker.TestWorker.execute(TestWorker.java:60)
	at org.gradle.process.internal.worker.child.ActionExecutionWorker.execute(ActionExecutionWorker.java:56)
	at org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker.call(SystemApplicationClassLoaderWorker.java:113)
	at org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker.call(SystemApplicationClassLoaderWorker.java:65)
	at app//worker.org.gradle.process.internal.worker.GradleWorkerMain.run(GradleWorkerMain.java:69)
	at app//worker.org.gradle.process.internal.worker.GradleWorkerMain.main(GradleWorkerMain.java:74)
Caused by: java.lang.NullPointerException: Cannot invoke "Object.hashCode()" because "key" is null
	at java.base/java.util.concurrent.ConcurrentHashMap.get(ConcurrentHashMap.java:948)
	at groovy.lang.MetaClassImpl.getMetaProperty(MetaClassImpl.java:2841)
	at groovy.lang.MetaClassImpl.setProperty(MetaClassImpl.java:2712)
	at org.codehaus.groovy.runtime.ScriptBytecodeAdapter.setProperty(ScriptBytecodeAdapter.java:509)
	at org.liquibase.groovy.delegate.DatabaseChangeLogDelegate.changeSet(DatabaseChangeLogDelegate.groovy:133)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
	at java.base/java.lang.reflect.Method.invoke(Method.java:565)
	at org.codehaus.groovy.reflection.CachedMethod.invoke(CachedMethod.java:338)
	at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:274)
	at org.codehaus.groovy.runtime.metaclass.ClosureMetaClass.invokeMethod(ClosureMetaClass.java:318)
	at org.codehaus.groovy.vmplugin.v8.IndyInterface.fromCache(IndyInterface.java:344)
	at Script1$_run_closure1.doCall(Script1.groovy:2)
	at Script1$_run_closure1.doCall(Script1.groovy)
	at org.codehaus.groovy.vmplugin.v8.IndyInterface.fromCache(IndyInterface.java:344)
	at liquibase.parser.ext.GroovyLiquibaseChangeLogParser.processDatabaseChangeLogRootElement(GroovyLiquibaseChangeLogParser.groovy:129)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
	at java.base/java.lang.reflect.Method.invoke(Method.java:565)
	at org.codehaus.groovy.reflection.CachedMethod.invoke(CachedMethod.java:338)
	at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:274)
	at org.codehaus.groovy.runtime.metaclass.ClosureMetaClass.invokeMethod(ClosureMetaClass.java:318)
	at org.codehaus.groovy.vmplugin.v8.IndyInterface.fromCache(IndyInterface.java:344)
	at liquibase.parser.ext.GroovyLiquibaseChangeLogParser$_getChangeLogMethodMissing_closure3.doCall(GroovyLiquibaseChangeLogParser.groovy:87)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
	at java.base/java.lang.reflect.Method.invoke(Method.java:565)
	at org.codehaus.groovy.reflection.CachedMethod.invoke(CachedMethod.java:338)
	at org.codehaus.groovy.runtime.metaclass.ClosureMetaMethod.invoke(ClosureMetaMethod.java:90)
	at groovy.lang.MetaClassImpl.invokeMissingMethod(MetaClassImpl.java:937)
	at groovy.lang.MetaClassImpl.invokePropertyOrMissing(MetaClassImpl.java:1325)
	at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:1245)
	at groovy.lang.ExpandoMetaClass.invokeMethod(ExpandoMetaClass.java:1139)
	at org.codehaus.groovy.vmplugin.v8.IndyInterface.fromCache(IndyInterface.java:344)
	at Script1.run(Script1.groovy:1)
	at org.codehaus.groovy.vmplugin.v8.IndyInterface.fromCache(IndyInterface.java:344)
	at liquibase.parser.ext.GroovyLiquibaseChangeLogParser.parse(GroovyLiquibaseChangeLogParser.groovy:58)
	at liquibase.command.core.helpers.DatabaseChangelogCommandStep.lambda$getDatabaseChangeLog$0(DatabaseChangelogCommandStep.java:125)
	at liquibase.Scope.child(Scope.java:210)
	at liquibase.Scope.child(Scope.java:186)
	at liquibase.command.core.helpers.DatabaseChangelogCommandStep.getDatabaseChangeLog(DatabaseChangelogCommandStep.java:124)
	at liquibase.command.core.helpers.DatabaseChangelogCommandStep.run(DatabaseChangelogCommandStep.java:83)
	at liquibase.command.CommandScope.lambda$execute$6(CommandScope.java:253)
	... 51 more
```

### Issue was not present in groovy 5.0.3
Interesting that the issue is sensitive to a performance optimization from v5.0.4 and was not appear in v5.0.3:

```bash
./gradlew clean test -PgroovyVersion=3.+ -PtestProjectGroovyVersion=5.0.3
```

### Issue disappears if library and consumer use same major groovy version
What even more interesting is that if the same liquibase-groovy-dsl was built using the same major groovy version as 
test project, then there is no issue:

```bash
./gradlew clean test -PgroovyVersion=5.0.0 -PtestProjectGroovyVersion=5.0.4
```


## What dependency versions actually to use?
### Liquibase-core support
- https://docs.liquibase.com/oss/get-started-4-33/system-requirements
    - v4.33 - Java 8 is the minimum required version,
    - although we recommend using Long-term Support (LTS) versions 11, 17, and 21.
    - Our team regularly tests on these versions and Liquibase ships with 21.

- https://docs.liquibase.com/community/get-started-5-0/system-requirements
    - v5.x - Java 17 is the minimum required version.
    - Our team regularly tests on Java 17 and 21, and Liquibase ships with Java 21.

### Groovy version support
- https://groovy-lang.org/releasenotes/groovy-5.0.html#Groovy5.0-requirements
    - Groovy 5 requires JDK17+ to build and JDK11 is the minimum version of the JRE that we support.
    - Groovy 5 has been tested on JDK versions 11 through 25.

- https://groovy-lang.org/releasenotes/groovy-4.0.html#Groovy4.0-requirements
    - Groovy 4 requires JDK16+ to build and JDK8 is the minimum version of the JRE that we support.
    - Groovy 4 has been tested on JDK versions 8 through 17.

- https://groovy-lang.org/releasenotes/groovy-3.0.html#Groovy3.0releasenotes-JdkRequirements
    - Groovy 3 requires JDK9+ to build and JDK8 is the minimum version of the JRE that we support.

### Summary
<pre>
 liquibase v4 | Java 8  - 21 (21 default) | groovy v3,v4,v5
 liquibase v5 | Java 17 - 21 (21 default) | groovy v3,v4,v5
 groovy 3 - Jvm8+
 groovy 4 - Jvm8..17
 groovy 5 - Jvm11..25
</pre>

So that, based on documentation the liquibase v5 is Java 17, that requires groovy 4 or 5 (NOT 3)

Note: ''- Fails/succeds same way for jdk 11, 17, 21, 25''
