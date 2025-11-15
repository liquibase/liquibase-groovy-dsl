# Groovy Liquibase
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.liquibase/liquibase-groovy-dsl/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.liquibase/liquibase-groovy-dsl)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/org.liquibase/liquibase-groovy-dsl/badge.svg)](http://www.javadoc.io/doc/org.liquibase/liquibase-groovy-dsl)

A pluggable parser for [Liquibase](http://liquibase.org) that allows the creation of changelogs in
a Groovy DSL, rather than hurtful XML. If this DSL isn't reason enough to adopt Liquibase, then
there is no hope for you.  This project was started once upon a time by Tim Berglund, and is
currently maintained by Steve Saliman.

## Version 4.0.1 has been released! See the [News](#news) section for more details.

**Important note for Groovy 4 and build tools:**
This DSL is built with a transitive dependency on Groovy 3.0.15.  This ensures that Gradle and Maven
users don't need to include Groovy in the classpath for the DSL to work, but because Groovy moved 
into the Apache foundation for version 4, and changed the artifact group, users who want to use
Groovy 4 need to exclude the transitive dependency.  Here is an example of how users of the
Liquibase Gradle plugin can do it:
```groovy
liquibaseRuntime('org.liquibase:liquibase-groovy-dsl:4.0.0') {
    exclude group: "org.codehaus.groovy", module: "groovy"
    exclude group: "org.codehaus.groovy", module: "groovy-sql"
  }
liquibaseRuntime "org.apache.groovy:groovy:4.0.5"
liquibaseRuntime "org.apache.groovy:groovy-sql:4.0.5"
```

## News
### Oct 1, 2025
Release 4.1 adds **auto-complete and documentation** right @ script edit in IDEs with groovy
support like IntelliJ IDEA and Eclipse. 
Auto complete works if the `BaseScript` type is defined in the first lines like this:
```groovy
@groovy.transform.BaseScript(GroovyScript)
import liquibase.GroovyScript
```
The file also needs to be is in the `src/main/...`, `src/test/...` or similar folder where the IDE
may treat it as part of the project's module, the liquibase-groovy-dsl jar is configured for.

![edit helpers](doc/code-helpers.gif )

**If the IDE does not show error during script editing, then no syntax error will occur later 
when the script excuted**, which greatly improves productivity. (Opposed to previous versions, where 
scripts were checked runtime only)

![](doc/code-edit-anim.gif )

- known properties can be set with positional parameters. e.g. 
```groovy
   changeSet 'id1', 'authorname' {
   }
```
**NOTE:** Although the IDE can show the parameter names automatically, using more than the first 
few positional parameters is not suggested.
Positional parameters can be mixed with named ones like:
```groovy
  changeSet 'id2', author:'authorname' {
  } 
```
**NOTE:** 
1. By the nature of the groovy method call logic, the parameters defined with its name,  
  are passed as a map to the method as first parameter. To help editor validation, there are always
  at least 2 method declarations for each element: [Groovy docs](https://groovy-lang.org/objectorientation.html#_mixing_named_and_positional_parameters)
   - One with the parameters and their type
   - Another with the same list, but a `Map ǃ` parameter in front, which you should ignore
   - If there are mandatory attributes, then for backward compatibility the single `Map` parameter 
     version of the methods are also kept that is used when all parameters defined with their name 
     like before
```groovy
tableExists( String tableName, String schemaName=null, String catalogName=null )
tableExists( Map ǃ, String tableName, String schemaName=null, String catalogName=null )
tableExists( Map params )
```
2. Optional attributes have a default value of `null`. There is no optional parameter in java, so 
  groovy has to generate a separate method declaration for each optional parameter. That resulting ~
  2 x <number of optional parameters> + 1 declarations, that can cover all possible cases _helping
  the validation right while you are typing_.

3. When mixing positional and named parameter definitions, extra care needs to be taken <br>
  _not to try to define parameters positionally **after** the position of the first parameter's 
  position defined with its name_!<br>
  The parameter meant to be defined after this position will be used as value of the parameter 
  defined by its name. It results _that parameter is defined twice_ and an `ArgumentSetTwice` error 
  will occur during execution. (otherwise there is no way to know which shall be used). It might
  sound complicated, but an example can help to simplify it. Let's see one using the change with
  definition:<br>`
  delete( String tableName, String schemaName=null, String catalogName=null)`<br>
  With the use: `delete( 'table', schemaName:'schema', 'catalog')`<br>
  The value "schema" and "catalog" are both treated as the value for parameter `schemaName`. 
   1. It's value "schema" set with its name gets passed in the first special `Map ǃ` parameter 
   2. and its position will be filled with the value "catalog".

   All other positional parameters are also "shifted left", so even if the double value definition 
   wouldn't trigger an error, all parameters would get the value of the next parameter on its right.
   
   (To be exact for this situation to happen all parameters has to have the same type. the situation
   is even more complicated if there are different type parameters)

4. The old named parameter definition is still supported and also aided with the valid attribute names 

- Liquibase `Enum` values can also be used not only as strings, as every script has imported statically:
```groovy
  import static liquibase.database.ObjectQuotingStrategy.*
  import static liquibase.changelog.ChangeSet.ValidationFailOption.*
  import static liquibase.database.ColumnParentTypeEnum.*
  import static liquibase.precondition.core.PreconditionContainer.OnSqlOutputOption.*
  import static liquibase.precondition.core.PreconditionContainer.FailOption.*
```
  also 
  `import liquibase.precondition.core.PreconditionContainer.ErrorOption as OnError` imported
  so this works fine:
```groovy
databaseChangeLog(objectQuotingStrategy: LEGACY) {
  preConditions HALT, OnError.CONTINUE, onSqlOutput: FAIL, { ...
```
**Note:** _The IDE validation and code completion require putting these at the top of the script
if you use any of the enum values._

- Error messages greatly improved: the script name + line and possible elements or parameters added<br>
  e.g. in case a mistyped "createTable/**column**" in file `changelogs/includerelative/pathinclude1.changelog.groovy`
  included from `changelogs/common.test.groovy`:<br>
>  `changeSet includerelative/pathinclude1::1::nvoxland: Unrecognized child element: 'colum' for 'createTable'! Valid elements are [column] @changelogs/includerelative/pathinclude1.changelog.groovy:7 @changelogs/common.test.groovy:616`

instead of:
> `ChangeSet '1': 'createTable' is not a valid child element of createTable changes`

- **Converter from XML**: To convert changelog.xml _and all XMLs it includes transitively_ run
> `java -jar liquibase-groovy-dsl-4.1.0.jar -cp liquibase-core-4.28.0.jar;<liquibase dependencies> <change log xml>`
 _Known limitations: `rollback` and `createProcedure` elements are not converted correctly_

**Breaking changes:**
- The change [createProcedure](https://docs.liquibase.com/reference-guide/change-types/createprocedure)
  has 2 disjunct set of properties: 
  1. `path` + related properties if the procedure text shall be read from a separate file. 
  2. If those are not defined, the SQL is expected to be in the child `procedureText` closure. 
     Now calling `createProcedure 'string'` will treat 'string' as the `path` instead of the SQL 
     body as earlier
- Multiple nested `comment`s are not concatenated (latest wins) in `sql` element like in other file 
  formats
- `ArgumentSetTwice` exception is thrown, when both `context` and `contextFilter` is set for 
  `include` and `includeAll`

### March 1, 2025
Release 4.0.1 adds support for Liquibase 4.31, including new attributes added to Liquibase since
version 4.26.

### March 12, 2024
Release 4.0.0 adds support for Liquibase 4.26, and removed official support for versions prior to
that.  Liquibase has had a lot of internal API changes between 6.16.1 and 4.26, and the plugin will
most likely not work with older versions.

**This release has breaking changes**

- The new version removes support for absolute paths.  Liquibase hasn't supported them in a long
  time, so the Groovy DSL no longer does either.  If, for some reason, you are using absolute
  paths in an `include` or `includeAll`, they will need to be fixed, and you will most likely need
  to fix the entries in the `databasechangelog` table as well. 

- The Groovy DSL used to "fix" the path of files that were included relative to the changelog.  All
  paths are now going to be relative to the working directory, which is how the other Liquibase
  parsers work.  This means that if you are using `relativeToChangeLog` in any of your `include` or
  `includeAll` methods, you'll have to fix the paths in the `databasechangelog` table to be relative
  to the working directory instead of the changelog.

- The Groovy DSL used to have a default filter for `includeAll`.  None of the other parsers did this
  so we no longer do.  If you are doing an `includeAll` on a directory that has more than just the
  groovy files you want to include, this will be a breaking change.  Adding 
  `endsWithFilter: ".groovy"` to the `includeAll` will fix this issue.

- The groovy DSL removed support for the deprecated `onUpdateSQL` precondition attribute.  Use
  `onUpdateSql` instead.

In addition, the Groovy DSL doesn't support the Liquibase PRO modifyChangeSets change.

### March 12, 2023
Release 3.0.3 adds support for Liquibase up to version 4.16.1, and it adds support for Groovy 4
(#53), with thanks to Bjørn Vester (@bjornvester)

### June 12, 2021
Release 3.0.2 Fixes a bug with change log parameters (#50)

### April 16, 2021
Version 3.0.1 of the Liquibase Groovy DSL is now available with support for  Liquibase 4.2.2.

### September 5, 2020
Version 3.0.0 of the Liquibase Groovy DSL is now available with support for Liquibase 4.0.0.

As you might expect for a major release, this means some breaking changes.   There are two breaking
changes with this release.

Version 3.0.0 of the DSL no longer supports the 3.x releases of Liquibase.  If you need to use an
older version of Liquibase, you'll need an older version of the DSL.

Liquibase 4.0.0 no longer supports using absolute filenames, so the DSL doesn't either.  This change
only affects changelogs that were using the `include` and `includeAll` elements with absolute paths.
 
### June 6, 2020
Release 2.1.2 is a minor release that fixes an issue with `include` and `includeAll` changes nested
inside change logs that used the previously added `logicalFilePath` support.

### January 25, 2020
Added support for an undocumented ChangeSet attribute.  The XML accepts an attribute named
`logicalFilePath`.  The actual ChangeSet property in the source code is named `filePath`.  The
Groovy DSL now supports both.  The default is still to inherit the filePath from the
DatabaseChangeLog.  This resolves Issue #45. The bugs in Liquibase 3.7+ still remain as of
Liquibase 3.8.5, so use those versions with care.

## Usage
Simply include this project's jar file in your class path, along with a version of Liquibase, a
version of Groovy, and your database driver, and Liquibase can parse elegant Groovy changelogs
instead of ugly XML ones.

If you are running Liquibase directly from the command line using the binary distribution of
Liquibase, you would need to copy the liquibase-groovy-dsl, groovy-x.y.z and database driver jar
files into the `internal/lib` directory of the Liquibase distribution.  If you are running Liquibase
using a Gradle plugin, Maven plugin, or Spring Boot, follow the documentation of the tool to add
these artifacts to the classpath.  

The DSL syntax is intended to mirror the
[Liquibase XML](https://docs.liquibase.com/concepts/changelogs/home.html)
syntax directly, such that mapping elements and attributes from the Liquibase documentation to
Groovy builder syntax will result in a valid changelog. Hence, this DSL is not documented separately
from the Liquibase XML format.  We will, however let you know about the minor differences or
enhancements to the XML format, and help out with a couple of the holes in Liquibase's documentation
of the XML format.

Note that wile the Groovy DSL fully supports using absolute paths for changelogs, we strongly
recommend using relative paths instead.  When Liquibase sees an absolute path for a changelog, all
changes included by that changelog will also have absolute path names, even if the `include` or
`includeAll` element used the `relativeToChangeLog` attribute.  This will cause problems in 
multi-developer environments because the difference in the users' directories will cause Liquibase
to think that the changes are new, and it will try to run them again.

##### Deprecated and Unsupported Items
- The Liquibase `includeAll` element has `minDepth` and `maxDepth` attributes, but they are broken
  in Liquibase itself (https://github.com/liquibase/liquibase/issues/5654).  The Groovy DSL will
  pass them to Liquibase as-is, so once this is fixed in Liquibase, they will work in the Groovy DSL
  as well.

- The Groovy DSL doesn't support the `modifyChangeSets` change.

- Liquibase has an optional `comment` element for `validCheckSum` elements, which has no use in 
  groovy file format where the user can use groovy's standard comments, so it has been left out of 
  the Groovy DSL.

- The documentation mentions a deprecated `referencesUniqueColumn` attribute of the
  `addForeignKeyConstraint` change, but what it doesn't tell you is that it is ignored.  Since
  Liquibase deprecated it, the Groovy DSL does as well, and it will print a warning message.

- Prior to 2.0.0, the DSL used the `resourceFilter` attribute of the `includeAll` element to filter
  the changelogs included in a directory.  This has been changed to `filter` to remain consistent
  with Liquibase itself.

##### Additions to the XML format:
- Sometimes, you may want to include all SQL files in a given directory.  For example, you may have
  the source for all your stored procedures in a directory, and you want to reload each file 
  whenever that file changes.  The Groovy DSL adds an `includeAllSql` element to `databaseChangeLog`
  to do this.  It combines the functionality if `includeAll` and `sqlFile` in one convenient
  element.  `includeAllSql` creates a changeSet for each file it finds in a given directory.  Each
  changeSet will contain a single `sqlFile` change that will rerun whenever the file itself changes.
  The id of each generated changeSet will be derived from the name of the SQL file the changeSet
  runs.  The `includeAllSql` element takes a map of attributes that include the valid parameters
  from `includeAll`, `changeSet`, and `sqlFile`, with a few exceptions:
  - Since changeSet ids are generated, `id` attribute is not supported by `includeAllSql`
  - The `author` attribute is supported, but optional.  Different SQL files could have different
    authors, so the default is "various (generated by includeAllSql)".
  - The intent of `includeAllSql` is to "monitor" the files in the included directory for changes,
    so the default value for `runOnChange` is true.
  - The `filePath`, `logicalFilePath`, `runInTransaction`, and `runOrder` attributes are not
    supported by `includeAllSql` because it creates multiple changeSets, and it doesn't make much
    sense to run a SQL file outside a transaction.
  - The `idPrefix`, `idSuffix`, and `idKeepsExtension` attributes are specific to `includeAllSql`,
    and control how the changeSet ids are generated.  By default, the id is simply the name of the
    SQL file, minus the directory and extension.  `idPrefix` and `idSuffix` allow you to prepend or
    append text to that name, and `idKeepsExtension` lets you include the file's extension in the
    id.
  
- In general, boolean attributes can be specified as either strings or booleans. For example,
  `changeSet(runAlways: 'true')` can also be written as `changeSet(runAlways: true)`. The same is 
  true for numeric and enum parameters. 

- The Groovy DSL supports a simplified means of passing arguments to the `executeCommand change`.
  Instead of:

```groovy
execute {
  arg(value: 'somevalue')
}
```
You can use this simpler form:
```groovy
execute {
  arg 'somevalue'
}
```

- The `sql` change does not require a closure for the actual SQL.  You can just pass the string like
  this: `sql 'select some_stuff from some_table'`.  If you want to use the `comments` element of a
  `sql` change, you need to use the closure form, and the comment must be in the closure BEFORE the
  SQL, like this:

```groovy
sql {
  comment('we should not have added this...')
  'delete from my_table'
}
```

- The  `stop` change can take a message as an argument as well as an attribute.  In other words,
  `stop 'message'` works as well as the more XMLish `stop(message: 'message')`

- A `customPrecondition`  can take parameters.  the XMLish way to pass them is with
  `param(name: 'myParam', value: 'myValue')` statements in the customPrecondition's closure.  In the
  Groovy DSL, you can also have `myParam('myValue')`

- The `validChecksum` element of a change set is not well documented.  Basically you can use this
  when changeSet's current checksum will not match what is stored in the database. This might happen
  if you, for example want to reformat a changeSet to add white space.  This doesn't change the
  functionality of the changeset, but it will cause Liquibase to generate new checksums for it.  The
  `validateChecksum` element tells Liquibase to consider the checksums in the `validChecksum`
  element to be valid, even if it doesn't match what is in the database.

- Liquibase has an `includeAll` element in the databaseChangeLog that includes all the files in the
  given directory.  The Groovy DSL implementation makes sure they are included in alphabetical
  order by path, like Liquibase itself does.  This is really handy for keeping changes in a
  different file for each release.  As long as the file names are named with the release numbers in
  mind, Liquibase will apply changes in the correct order.

- `column` attributes are limited to their meaningful set e.g. `index` / `column` does not offer 
  `defaultValueNumeric` as it is ignored during execution.

- Remember, the Groovy DSL is basically just Groovy closures, so you can use groovy code to do
  things you could never do in XML, such as this:

```groovy
sql { """
  insert into some_table(data_column, date_inserted)
  values('some_data', '${new Date().toString()}')
"""
}
```

##### Items that were left out of the XML documentation
- The `column` element has some undocumented attributes.  `valueSequenceNext`,
  `valueSequenceCurrent`, and `defaultValueSequenceNext`, which appear to link values for a column
  to database sequences.

- Liquibase added the `context` attribute to the `include`, `includeAll`, and `changeLog` elements.
  They work the same as the context attribute of a change set.

- The Liquibase XML accepts a `logicalFilePath` attribute for the `changeSet` element.  The actual
  property in the ChangeSet class is named `filePath`.  The Groovy DSL accepts both.  The default is
  to inherit the file path from the DatabaseChangeLog that contains the ChangeSet. 

## License
This code is released under the Apache Public License 2.0, just like Liquibase itself.

## TODOs

 * Support for the customChange. Using groovy code, liquibase changes and database SQL in a changeSet.
 * Support for extensions. modifyColumn is probably a good place to start.
