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
import liquibase.change.ConstraintsConfig
import liquibase.changelog.DatabaseChangeLog
import liquibase.parser.groovy.exception.ParseErrorWithFileNLine

@CompileStatic
class ConstraintDelegate extends Delegatee<Tag> {
    enum Tag {constraints}
    protected ConstraintsConfig constraint

    ConstraintDelegate(DatabaseChangeLog dbChangeLog, String changeId, String parent) {
        super( dbChangeLog, changeId, parent)
        constraint = new ConstraintsConfig()
    }

/**
 *
 * @param nullable
 * @param notNullConstraintName
 * @param primaryKey
 * @param primaryKeyName
 * @param primaryKeyTablespace
 * @param references
 * @param referencedTableCatalogName
 * @param referencedTableSchemaName
 * @param referencedTableName
 * @param referencedColumnNames
 * @param unique
 * @param uniqueConstraintName
 * @param checkConstraint
 * @param deleteCascade
 * @param foreignKeyName
 * @param initiallyDeferred
 * @param deferrable
 * @param validateNullable
 * @param validateUnique
 * @param validatePrimaryKey
 * @param validateForeignKey
 */
    void constraints(Boolean nullable, Boolean primaryKey = null, Boolean unique = null,
                     String references = null, String foreignKeyName = null,
                     String primaryKeyName = null, String uniqueConstraintName = null,
                     String referencedTableName= null, String referencedColumnNames = null,
                     String referencedTableCatalogName = null, String referencedTableSchemaName = null,
                     String notNullConstraintName = null, String primaryKeyTablespace = null,
                     String checkConstraint = null, Boolean deleteCascade = null,
                     Boolean initiallyDeferred = null, Boolean deferrable = null,
                     Boolean validateNullable = null, Boolean validateUnique = null,
                     Boolean validatePrimaryKey = null, Boolean validateForeignKey = null) {
        constraints argsAsMap(Tag.constraints, nullable, primaryKey, unique,
                    references, foreignKeyName,
                    primaryKeyName, uniqueConstraintName,
                    referencedTableName= null, referencedColumnNames,
                    referencedTableCatalogName, referencedTableSchemaName,
                    notNullConstraintName, primaryKeyTablespace,
                    checkConstraint, deleteCascade,
                    initiallyDeferred, deferrable, validateNullable, validateUnique,
                    validatePrimaryKey, validateForeignKey )
    }

    void constraints(Map ǃ,
                     Boolean nullable, Boolean primaryKey = null, Boolean unique = null,
                     String references = null, String foreignKeyName = null,
                     String primaryKeyName = null, String uniqueConstraintName = null,
                     String referencedTableName= null, String referencedColumnNames = null,
                     String referencedTableCatalogName = null, String referencedTableSchemaName = null,
                     String notNullConstraintName = null, String primaryKeyTablespace = null,
                     String checkConstraint = null, Boolean deleteCascade = null,
                     Boolean initiallyDeferred = null, Boolean deferrable = null,
                     Boolean validateNullable = null, Boolean validateUnique = null,
                     Boolean validatePrimaryKey = null, Boolean validateForeignKey = null) {
        constraints argsAsMap(Tag.constraints, ǃ, nullable, primaryKey, unique,
           references, foreignKeyName,
           primaryKeyName, uniqueConstraintName,
           referencedTableName= null, referencedColumnNames,
           referencedTableCatalogName, referencedTableSchemaName,
           notNullConstraintName, primaryKeyTablespace,
           checkConstraint, deleteCascade,
           initiallyDeferred, deferrable, validateNullable, validateUnique,
           validatePrimaryKey, validateForeignKey )
    }

    def constraints(Map params) {
        if(!params) {
            error new ParseErrorWithFileNLine("`constraint` element requires at least one argument to be set. " +
                    "Valid arguments:" + constraint.serializableFields.toListString()
            )
        }
        setProps(constraint, params)
    }

    def methodMissing(String name, params) {
        if ( constraint.hasProperty(name) ) {
            setProp(constraint, name, (params as Object[])[0])
        } else {
            super.methodMissing(name, params)
        }
    }
}
