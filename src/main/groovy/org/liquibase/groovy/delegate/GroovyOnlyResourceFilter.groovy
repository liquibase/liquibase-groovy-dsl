/*
 * Copyright 2011-2023 Tim Berglund and Steven C. Saliman
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

import liquibase.changelog.IncludeAllFilter

/**
 * Created with IntelliJ IDEA.
 * User: steve
 * Date: 6/23/18
 * Time: 6:53 PM
 * To change this template use File | Settings | File Templates.
 *
 * @author Steven C. Saliman
 */
class GroovyOnlyResourceFilter implements IncludeAllFilter {
    def userFilter

    @Override
    boolean include(String changeLogPath) {
        // If this isn't a groovy file, exclude it.
        if ( !changeLogPath.endsWith(".groovy") ) {
            return false
        }
        // if it is a groovy file and the user hasn't defined a filter, return true
        if ( userFilter == null ) {
            return true
        }
        // if it is a groovy file, and the user has a filter, defer to the filter.
        return userFilter.include(changeLogPath)
    }
}
