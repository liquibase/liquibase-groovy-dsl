package org.liquibase.groovy.delegate

import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.resource.ResourceAccessor

class ScriptDelegate {

  final DatabaseChangeLog changeLog
  final resourceAccessor

  ScriptDelegate(DatabaseChangeLog changeLog, ResourceAccessor resourceAccessor) {
    this.changeLog = changeLog
    this.resourceAccessor = resourceAccessor
  }

  public void databaseChangeLog(@DelegatesTo(DatabaseChangeLogDelegate) Closure closure) {
    DatabaseChangeLogDelegate delegate = new DatabaseChangeLogDelegate(changeLog)
    delegate.resourceAccessor = this.resourceAccessor
    def script = closure.rehydrate(delegate, this, this)
    script.resolveStrategy = Closure.DELEGATE_FIRST
    script()
  }

  public void databaseChangeLog(Map params, @DelegatesTo(DatabaseChangeLogDelegate) Closure closure) {
    DatabaseChangeLogDelegate delegate = new DatabaseChangeLogDelegate(params, changeLog)
    delegate.resourceAccessor = this.resourceAccessor
    def script = closure.rehydrate(delegate, this, this)
    script.resolveStrategy = Closure.OWNER_FIRST
    script()
  }

  // Method exists for consistency
  public void databaseChangeLog() {
    throw new ChangeLogParseException("databaseChangeLog element cannot be empty")
  }

  public void methodMissing(String methodName, args){
    throw new ChangeLogParseException("Unrecognized root element ${methodName}")
  }

}
