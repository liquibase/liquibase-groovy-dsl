Changes for 4.1
================
- Attributes can be passed as positional method parameter for each element, that adds **auto complete** 
  @ script edit in IDEs with groovy support like IntelliJ IDEA, Eclipse and Netbeans.

- This solution **adds inplace documentation** also in IDEs 

- From now **if the IDE does not show error during script edit, there is a big chance the script is 
valid and no parse error occurs @ runtime**

- Error messages are improved containing the script name+line and possible names / parameters
e.g: "changeSet includerelative/pathinclude1::1::nvoxland: Unrecognized child element: 'colum' for 
'createTable'! Valid elements are [column] @changelogs/pathincluded/pathinclude1.changelog.groovy:10"

- Liquibase `Enum`s can also be used as either strings or just their enum value as every script has
  imported statically
  `liquibase.database.ObjectQuotingStrategy.*`
  ,`liquibase.changelog.ChangeSet.ValidationFailOption.*`
  ,`liquibase.database.ColumnParentTypeEnum.*`
  ,`liquibase.precondition.core.PreconditionContainer.FailOption.*`

- `column` element attributes are limited to their meaningful set e.g. `index` / `column` does not offer
  attributes like `defaultValueNumeric` as it is ignored during execution.

- `property` element handling logs warning("'name' and 'value' parameters are ignored if 'file' is set")

- `output` element is supported with `message` in closure parameter too 

_Known limitations:_
1. elements with 2 disjunct parameter sets return only one possible parameter list in the error message
2. `whereParams` not using the new error handling

Fixes:
- `databaseChangeLog/includeAllSql` related test were not running on Windows
- `include@errorIfMissing` should be true by default

- **Converter from XML**: To convert XML changelog _and all XMLs it includes transitively_ run
> `java -jar liquibase-groovy-dsl-4.1.0.jar -cp liquibase-core-4.xx.x.jar;<liquibase dependencies> <change log xml>`
_Known limitations: `rollback` and `createProcedure` elements are not converted correctly_

**Breaking change**
- The change [createProcedure](https://docs.liquibase.com/reference-guide/change-types/createprocedure)
  has 2 disjunct set of properties:  
  1. `path` + related properties if the procedure text shall be read from a separate file.
  2. If those are not defined, the SQL is expected to be in the child `procedureText` closure.
     Now calling `createProcedure 'string'` will treat 'string' as the `path` instead of the SQL
     body as earlier

- Multiple nested `comment`s are not concatenated (latest wins) in `sql` element ike in other file formats

- `ArgumentSetTwice` exception is thrown, when both `context` and `contextFilter` is set for `include[All]`

Changes for 4.0.1
=================

- Fixed a bug with the `maxDepth` argument to `includeAll` and `includeAllSql` elements.

- Added support for the `onSqlOutput` precondition property, with thanks to @momosetkn

- Fixed a bug that prevented preconditions from being serialized, with thanks to @momosetkn

- Added support for Liquibase 4.31.1, adding new attributes introduced since 4.26.

Changes for 4.0.0
=================

- Added support for Liquibase 4.26, removed support for previous versions.

- Added support for `whereParams` in `update` and `delete` changes.

- Dropped support for absolute paths for `includeAll`.

- Removed the default filter for `includeAll`.

- Stopped "fixing" the paths when using `relativeToChangeLog` in an `includeAll`.

- removed support for the deprecated `onUpdateSQL` precondition property.

- Removed the check for `constraints` that have a closure.  We never supported it, we just had a 
  nice error message.

- Added an includeAllSql element to create a changeSet with a sqlFile change for each SQL file
  found in a specified directory.

- We now show a more meaningful message for invalid `preconditions` (#61)

Changes for 3.0.3
=================
- Updated Gradle to version 7.6.1 and moved all the build properties to gradle.properties.

- Added support for Liquibase 4.16, updated the default version of Groovy to 3.0.15

- Added support for Groovy 4 (#53), with thanks to Bjørn Vester (@bjornvester)


Changes for 3.0.2
=================
- Fixed #50, which prevented changelog parameters from being passed into the 
  change sets.
   
Changes for 3.0.1
=================
- Added support for Liquibase 4.2's new `allowUpdate` attribute of a column
  inside a `loadupdateData` change.
  
Changes for 3.0.0
=================
- Added support for Liquibase 4.0.0, which breaks support for Liquibase 3.x.
  This introduces one breaking change to the Groovy DSL.  Liquibase no longer
  supports including changelogs via an absolute path, so that support has been
  removed from the Groovy DSL as well.  Changes to the way Liquibase finds 
  extensions means that the new DSL can't be used with older versions of 
  Liquibase. 
  
Changes for 2.1.2
=================
- Verified that everything still works in Liquibase 3.10.0

- Fixed an issue with include and includeAll changes inside databaseChangeLogs
  that have parameter maps (Issue #47)
  
Changes for 2.1.1
=================
- Added support for an undocumented ChangeSet attribute:  The XML uses
 `logicalFilePath`, the actual ChangeSet property is called `filePath`.  The 
 DSL now supports both. (Issue #45)
  
Changes for 2.1.0
=================
- Added support for Liquibase 3.7

Changes for 2.0.3
=================
- Changed the way strings are serialized when we run 'generateChangeLog'.  
  Instead of single quotes, we now use Groovy's triple quotes.  If a view or
  table remarks use multi-line definitions, or contain quotes themselves, the
  generated change logs will now work.  Thanks to Ethan Davidson
  (@ethanmdavidson) for the contribution (Issue #39)
  
- Unit tests now work on windows boxes (Issue #40).
   
Changes for 2.0.2
=================
- Changed the way we load changes.  We now look for all changes in the Liquibase
  registry instead of instantiating them directly.
  
- Added support for changes provided by extensions, with thanks to Amanuel Nega
  (@amexboy) (Issue #33)
  
Changes for 2.0.1
=================
- Updated the version of Groovy to 2.4.12 to remove the CVE-2016-6814
  vulnerability
  
Changes for 2.0.0
=================
- This DSL no longer has a transitive dependency on Liquibase itself.  **This is
  a breaking change!**  It is now up to you make sure Liquibase is on the 
  classpath, but you can now use whatever version of Liquibase you need. This
  resolves Issue 32.
  
Changes for 1.2.2
=================
- Added support for property tokens in `changeSet`, `include`, and `includeAll`
  attributes (Issue #26)
  
- Fixed a problem with file based attributes of the `column` method (Issue #22)
  with thanks to Viachaslau Tratsiak (@restorer)  
  
- Rollback changes that need access to resources, like `sqlFile` can find them
  (Issue #24)
 
- Explicitly set a value for the `output` change's `target` attribute if no
  value was given.  This is a workaround for a Liquibase bug. (Issue #28)
 
- Changed the way included files are found so that classpath resources can be
  used in Spring Boot applications (Issue #13)
   
Changes for 1.2.1
=================
- Fixed some issues with custom changes (Issue #5 and Issue #8) with thanks to 
  Don Valentino

Changes for 1.2.0
=================
- Updated the DSL to support most of Liquibase 3.4.2 (Issues 4 and 6 from the 
  Gradle plugin repository)

Changes for 1.1.1
=================
- Updated the DSL to support Liquibase 3.3.5 (Issue 29 from the old repository)

- Fixed a `createProcedure` bug and added support for `dropProcedure` with 
  thanks to Carlos Hernandez (Issue #3)

Changes for 1.1.0
=================
- Refactored the project to fit into the Liquibase organization.

Changes for 1.0.2
=================
- Recompiled with `sourceCompatibility` and `targetCompatibility` set so that
  the DSL works with older versions of Java (< JDK8)

Changes for 1.0.1
=================
- Updated the DSL to support Liquibase 3.3.2 (Issue #45 from the old repo)

- Updated the Groovy dependency to 2.4.1. 
