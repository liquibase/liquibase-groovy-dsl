# Groovy Liquibase
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.liquibase/liquibase-groovy-dsl/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.liquibase/liquibase-groovy-dsl)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/org.liquibase/liquibase-groovy-dsl/badge.svg)](http://www.javadoc.io/doc/org.liquibase/liquibase-groovy-dsl)

A pluggable parser for [Liquibase](http://liquibase.org) that allows the
creation of changelogs in a Groovy DSL, rather than hurtful XML. If this DSL
isn't reason enough to adopt Liquibase, then there is no hope for you.  This
project was started once upon a time by Tim Berglund, and is currently
maintained by Steve Saliman.

## News
### April 16, 2021
Version 3.0.1 of the Liquibase Groovy DSL is now available with support for 
Liquibase 4.2.2.

### September 5, 2020
Version 3.0.0 of the Liquibase Groovy DSL is now available with support for 
Liquibase 4.0.0.

As you might expect for a major release, this means some breaking changes.  
There are two breaking changes with this release.

Version 3.0.0 of the DSL no longer supports the 3.x releases of Liquibase.  If
you need to use an older version of Liquibase, you'll need an older version of
the DSL.

Liquibase 4.0.0 no longer supports using absolute filenemes, so the DSL doesn't
either.  This change only affects changelogs that were using the `include` and
`includeAll` elements with absolute paths.
 
### June 6, 2020
Release 2.1.2 is a minor release that fixes an issue with `include` 
and `includeAll` changes nested inside change logs that used the previously
added `logicalFilePath` support.

### January 25, 2020
Added support for an undocumented ChangeSet attribute.  The XML accepts an 
attribute named `logicalFilePath`.  The actual ChangeSet property in the source
code is named `filePath`.  The Groovy DSL now supports both.  The default is 
still to inherit the filePath from the DatabaseChangeLog.  This resolves Issue
#45. The bugs in Liquibase 3.7+ still remain as of Liquibase 3.8.5, so use those
versions with care.

### September 21, 2019
Version 2.1.0 of the Liquibase Groovy DSL has been released to support version
3.7+ of Liquibase, since it has a change that breaks older versions of the DSL.
Note that there is a bug in Liquibase, documented in  
[CORE-3463](https://liquibase.jira.com/browse/CORE-3463) that prevents the DSL
from parsing the new validateXYZ attributes of a constraint, but I wanted to 
release a version that otherwise works with the latest Liquibase releases.  
I've submitted a [Pull Request](https://github.com/liquibase/liquibase/pull/917) 
to fix the issue, and as soon as it is incorporated into Liquibase, the Groovy
DSL should once again be fully compatible with Liquibase.

### September 7, 2019
Liquibase version 3.8.0 is out, but they have not yet applied the pull requests
that fix the issues with the new 3.7.0 attributes.  In the meantime, if you 
depend on Liquibase 3.7.x or newer, and you aren't using the new attributes,
you can build and use this version of the Groovy DSL.  I'm hoping for a 3.8.1
release shortly, at which point I'll release the latest version of the DSL.

### August 20, 2019
Liquiabse version 3.7.0 is out, but it has caused a couple of issues with the
Groovy DSL.  If you use one of the following, you won't be able to upgrade 
to Liquibase 3.7.0:
- `include` or `includeAll` in a change log: Liquibase has changed method 
  signatures of these methods.  I have a workaround for it that will be 
  released in the next version of the DSL.
- 4 new attributes have been added to constraints, but the Groovy DSL can't 
  work with them because of how they were implemented.  I've submitted a pull
  request that fixes the issue.
  
As soon as Liquibase 3.7.1 is released, I'll release a version of the Groovy
DSL that supports it.  We apologize for the inconvenience.

### March 3, 2019
Release 2.0.3 is a minor release that fixes some unit tests on Windows and
changes the way text is represented in generated changelogs, with thanks to
Ethan Davidson (@ethanmdavidson)

### October 14, 2018
Release 2.0.2 adds support for changes provided by extensions, with thanks to 
Amanuel Nega (@amexboy).  This release will be pushed in the next few days after
I look into another issue.

### September 3, 2018
Release 2.0.1 is a minor release that removes the CVE-2016-6814 vulnerability 
by updating the Groovy dependency.

### July 14, 2018
We're proud to announce the release of version 2.0.0 of the Groovy DSL, which
now fully supports Liquibase 3.6.1.  This release will decouple the DSL from
any particular version of Liquibase, making it easier for users to take
advantage of new releases of Liquibase as soon as they are released.  Note that
the DSL itself is tested against a specific version of Liquibase to ensure 
complete compatibility with a specific version, so YMMV with regard to newer
releases.

Release 2.0.0 has been tested against all the valid elements and attributes of
Liquibase 3.6.1

**Release 2.0.0 has breaking changes,** so make sure to read all the information
in this section before upgrading.

There are several breaking changes with this version of the DSL:
1. The Groovy DSL no longer includes Liquibase itself as a dependency.  Users
  must make sure the desired version of Liquibase is on the classpath.

2. There was a bug introduced in version 1.2.2 of the DSL regarding filenames
  and the `includeAll` change. Version 1.2.2 was incorrectly converting all
  changeset filenames to absolute paths, a bug that was fixed in version 2.0.0.
  If you are updating from version 1.2.1 or earlier, this change should not 
  effect you, but if you've run changes with version 1.2.2, you will need to fix
  some or all of the paths in the DATABASECHANGELOG table before running the 
  2.0.0 parser.  Failing to do this wil result in Liquibase trying to run the 
  changes again.

3. Liquibase made a change to the checksum logic in version 3.6.0.  According
  to the Liquibase documentation, Liquibase will just fix the checksums of each
  change when you run the first update command, but it won't detect changes to
  any changes that were marked with the `runOnChange`.  If you have any changes
  that use `runOnChange`, you should run an update once with your old version,
  then run it again with the new version to fix the checksums.

4. Liquibase changed the `resourceFilter` attribute of the `includeAll` element
  to just `filter`.  Since the 2.0.0 version of the Groovy DSL was built for
  Liquibase 3.6.x, it will throw an error if it finds the old `resourceFilter`
  attribute, so you will need to convert any effected change sets.  Note that
  `includeAll` is one of the few things handled by the DSL itself, so `filter`
  will still work even if you're using an older version of Liquibase.

5. The `alterSequence` change used to have a `willCycle` attribute.  That 
  attribute is now called `cycle`

## Usage
Simply include this project's jar file in your class path, along with a version
of Liquibase, a version of Groovy, and your database driver, and Liquibase can
parse elegant Groovy changelogs instead of ugly XML ones.

If you are running Liquibase directly from the command line using the binary
distribution of Liquibase, you would need to copy the liquibase-groovy-dsl,
groovy-x.y.z and database driver jar files into the `lib` directory of the 
Liquibase distribution.  If you are running Liquibase using a Gradle plugin, 
Maven plugin, or Spring Boot, follow the documentation of the tool to add these
artifacts to the classpath.  

The DSL syntax is intended to mirror the
[Liquibase XML](http://www.liquibase.org/documentation/databasechangelog.html)
syntax directly, such that mapping elements and attributes from the Liquibase
documentation to Groovy builder syntax will result in a valid changelog. Hence
this DSL is not documented separately from the Liquibase XML format.  We will,
however let you know about the minor differences or enhancements to the XML
format, and help out with a couple of the gaping holes in Liquibase's
documentation of the XML.

Note that wile the Groovy DSL fully supports using absolute paths for 
changelogs, we strongly recommend using relative paths instead.  When Liquibase
sees an absolute path for a changelog, all changes included by that changelog
will also have absolute path names, even if the `include` or `includeAll`
element used the `relativeToChangeLog` attribute.  This will cause problems in
multi-developer environments because the difference in the users' directories
will cause Liquibase to think that the changes are new, and it will try to run
them again.

##### Deprecated and Unsupported Items
* Liquibase has a `whereParam` element for changes like the `update` change.
  It isn't documented in the Liquibase documentation, and I don't see any
  benefits of using it over the simpler `where` element, so it has been left
  out of the Groovy DSL.
* In the Liquibase XML, you can set a `sql` attribute in a `sqlFile` change,
  but that doesn't make a lot sense, so this has been disabled in the Groovy
  DSL.
* The documentation mentions a `referencesUniqueColumn` attribute of the
  `addForeignKeyConstraint` change, but what it doesn't tell you is that it is
  ignored.  In the code, Liquibase has marked this item as being deprecated,
  so we've deprecated it as well, and we let you know about it.
* If you were using the DSL prior to version 1.0.0, a changeSet could have an
  `alwaysRun` property.  This is inconsistent with Liquibase and has been
  replaced in 1.0.0 with `runAlways`
* Prior to 1.0.0, the DSL allowed a `path` attribute in an `include`.  This is
  no longer allowed.  `includeAll` should be used instead.
* Prior to 1.0.0, the DSL allowed `createStoredProcedure` changes.  This has
  been replaced with `createProcedure`.
* Prior to 1.0.0, the DSL allowed a File object to be passed as an attribute to
  `loadData` and `loadUpdateData` changes.  This is no longer supported, the
  path to the file should be used instead.
* Prior to 1.0.0, the DSL allowed constraint attributes to be set as methods
  in a constraint closure.  This is inconsistent with the rest of the DSL and
  has been removed.
* Prior to 2.0.0, the DSL used the `resourceFilter` attribute of the 
  `includeAll` element to filter the changelogs included in an directory.  This
  has been changed to `filter` to remain consistent with Liquibase itself.

##### Additions to the XML format:
* In general, boolean attributes can be specified as either strings or booleans.
  For example, `changeSet(runAlways: 'true')` can also be written as
  `changeSet(runAlways: true)`.
* The Groovy DSL supports a simplified means of passing arguments to the
  `executeCommand change`.  Instead of:

```groovy
execute {
  arg(value: 'somevalue')
}
```
You can use this the simpler form:
```groovy
execute {
  arg 'somevalue'
}
```
* The `sql` change does not require a closure for the actual SQL.  You can
  just pass the string like this: `sql 'select some_stuff from some_table'`
  If you want to use the `comments` element of a `sql` change, you need
  to use the closure form, and the comment must be in the closure BEFORE the
  SQL, like this:

```groovy
sql {
  comment('we should not have added this...')
  'delete from my_table'
}
```
* The  `stop` change can take a message as an argument as well as an
  attribute.  In other words, `stop 'message'` works as well as the more
  XMLish `stop(message: 'message')`
* A `customPrecondition`  can take parameters.  the XMLish way to pass them
  is with `param(name: 'myParam', value: 'myValue')` statements in the
  customPrecondition's closure.  In the Groovy DSL, you can also have
   `myParam('myValue')`
* The `validChecksum` element of a change set is not well documented.
  Basically you can use this when changeSet's current checksum will not match
  what is stored in the database. This might happen if you, for example want to
  reformat a changeSet to add white space.  This doesn't change the
  functionality of the changeset, but it will cause Liquibase to generate new
  checksums for it.  The `validateChecksum` element tells Liquibase to
  consider the checksums in the `validChecksum` element to be valid, even
  if it doesn't match what is in the database.
* The Liquibase documentation tells you how to set a property for a
  databaseChangeLog by using the `property` element.  What it doesn't tell
  you is that you can also set properties by loading a property file.  To do
  this, you can have `property(file: 'my_file.properties')` in the closure
  for the databaseChangeLog.
* Liquibase has an `includeAll` element in the databaseChangeLog that
  includes all the files in the given directory.  The Groovy DSL implementation
  only includes groovy files, and it makes sure they are included in
  alphabetical order.  This is really handy for keeping changes in a different
  file for each release.  As long as the file names are named with the release
  numbers in mind, Liquibase will apply changes in the correct order.
* Remember, the Groovy DSL is basically just Groovy closures, so you can use
  groovy code to do things you could never do in XML, such as this:

```groovy
sql { """
  insert into some_table(data_column, date_inserted)
  values('some_data', '${new Date().toString()}')
"""
}
```

##### Items that were left out of the XML documentation
* The `createIndex` and `dropIndex` changes have an undocumented
  `associatedWith` attribute.  From an old Liquibase forum, it appears to be
   an attempt to solve the problem that occurs because some databases
   automatically create indexes on primary keys and foreign keys, and others
   don't.  The idea is that you would have a change to create the primary key or
   foreign key, and another to create the index for it.  The index change would
   use the `associatedWith` attribute to let Liquibase know that this index
   will already exist for some databases so that Liquibase can skip the change
   if we are in one of those databases.  The Liquibase authors do say it is
   experimental, so use at your own risk...
* The `executeCommand` change has an undocumented `os` attribute.  The
  `os` attribute is a string with  a list of operating systems under which
  the command should execute.  If present, the `os.name` system property will
  be checked against this list, and the command will only run if the operating
  system is in the list.
* The `column` element has some undocumented attributes that are pretty
  significant.  They include:
  - `valueSequenceNext`, `valueSequenceCurrent`, and
    `defaultValueSequenceNext`, which appear to link values for a column
    to database sequences.
  - A column can be set auto-number if it the `autoIncrement` attribute is set
    to true, but did you know that you can also control the starting number and
    the increment interval with the `startWith` and `incrementBy` attributes?
  - Since Liquibase 3.6, you can specify a `defaultValueConstraintName`.
* The `constraints` element also has some hidden gems:
  - Some databases automatically create indexes for primary keys. The
    `primaryKeyTablespace` can be used to control the tablespace.
  - A foreign key can be made by using the `references` attribute like
    this: `references: 'monkey(id)'`, It can also be done like this:
    `referencedTableName: 'monkey', referencedColumnNames: 'id'` for those
    who prefer to separate out the table from the column. Since Liquibase 3.5,
    this second form also has `referencedTableCatalogName` and 
    `referencedTableSchemaName` attributes.
  - There is also a `checkConstraint` attribute, that appears to be useful
    for defining a check constraint, but I could not determine the
    proper syntax for it yet.  For now, it may be best to stick to custom
    `sql` changes to define check constraints.
  - Since Liquibase 3.6, you can specify a name for a Not Null constraint with
    the `notNullConstraintName` attribute.
* The `createSequence` change has n `cacheSize` attribute that sets how many
  numbers of the sequence will be fetched into memory for each query that
  accesses the sequence.
* The documentation for version 3.1.1 of Liquibase mentions the new
  `beforeColumn`, `afterColumn`, and `position` attributes that you can put on
  a `column` statement to control where a new column is placed in an existing
  table.  What the documentation leaves out is that these attributes don't
  work :-)
* Version 3.4.0 of Liquibase introduced two new attributes to the 
  `includeAll` element of a databaseChangeLog, both of which are undocumented.
  The first one is the `errorIfMissingOrEmpty` attribute.  It defaults to 
  `true`, but if it is set to `false`, Liquibase will ignore errors caused by
  invalid or empty directories and move on.  The second one is the 
  `resourceFilter` attribute.  A resourceFilter is the name of a class that
  implements `liquibase.changelog.IncludeAllFilter` interface, which allows 
  developers to implement sophisticated logic to decide what files from a 
  directory should be included (in addition to the .groovy extension filter
  that the Groovy DSL imposes). 
* Liquibase 3.5.0 renamed the `resourceFilter` of `includeAll` to just `filter`.
  It also added `resourceComparator`, which lets you specify the name of a 
  class that implements Comparator that will be used to determine how to sort
  files.  The default is to just sort them by name.
* Liquibase 3.4.0 added the undocumented `forIndexCatalogName`,
  `forIndexSchemaName`, and `forIndexName` attributes to the `addPrimaryKey` 
  and `addUniqueConstraint` changes.  These attributes allow you to specify the
  index that will be used to implement the primary key and unique constraint, 
  respectively.
* Liquibase 3.4.0 added the undocumented `cacheSize` and `willCycle` attributes
  to the `alterSequence`  change. `cacheSize` sets how many numbers of the 
  sequence will be fetched into memory for each query that accesses the 
  sequence.  `willCycle` determines if the sequence should start over when it
  reaches its maximum value.
* Liquibase 3.5.0 changed the `willCycle` attribute of `alterSequence` to be `cycle`.
  It does the same thing, but it remains undocumented.
* Liquibase added the `context` attribute to the `include`, `includeAll`, and
  `changeLog` elements.  They work the same as the context attribute of a 
  change set.
* Liquibase 3.5 added `runOrder` and `created` attributes to the `changeSet` 
  element.  `runOrder` lets you specify that a change set should always be 
  first or last.  I have no idea what `created` does.
* Liquibase 3.6 added the `ignore` attribute to a change set, which seems to be
  a way to ignore a change set.
* Liquibase 3.6 added `validate` and `clustered` to the `addUniqueConstraint`
  change.  `validate` tells the database to validate the constraint when it is
  first created, and `clustered` tells the database to use a clustered index.
* Liquibase 3.6 added `defaultValueConstraintName` to the `addDefaultValue` 
  change to give the constraint being created a name.
* Liquibase 3.5 added `commentLineStartsWith` to the `loadData` change.  By
  default, Liquibase treats lines in the loaded file that start with a `#` to be
  comments.  `commentLineStartsWith` lets you change that.
* Liquibase 3.6 added `usePreparedStatements` to the `loadData` change.  When
  true, it tells Liquibase to use prepared statements in the inserts it 
  generates.
* Liquibase 3.6 added `validate` to the `addForeignKeyConstraint` change to
  tell the database whether or not to validate a new constraint when it is made.
* Liquibase 3.6 added another way to create a view with the `createView` change.
  Previously, the SQL to create the view was in the closure of the `createView`
  element.  Now you can specify a file with the SQL using the new `path`,
  `encoding`, and `relativeToChangelogFile` attributes.  I have no idea what
  happens if you specify both a path to a file and a closure with SQL.
* Liquibase 3.6 added a `timeout` to the `executeCommand` change.  It lets you
  specify a command timeout in seconds, minutes, or hours.  For example, to 
  set a 2 minute timeout, you'd use `2m` as the value of the `timeout` 
  attribute.
* The Liquibase XML accepts a `logicalFilePath` attribute for the `changeSet`
  element.  The actual property in the ChangeSet class is named `filePath`.  The
  Groovy DSL accepts both.  The default is to inherit the file path from the 
  DatabaseChangeLog that contains the ChangeSet. 

## License
This code is released under the Apache Public License 2.0, just like Liquibase 2.0.

## TODOs

 * Support for the customChange. Using groovy code, liquibase changes and database SQL in a changeSet.
 * Support for extensions. modifyColumn is probably a good place to start.
