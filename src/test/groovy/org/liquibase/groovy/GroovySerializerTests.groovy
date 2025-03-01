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
package org.liquibase.groovy

import liquibase.resource.DirectoryResourceAccessor
import liquibase.serializer.ChangeLogSerializerFactory
import liquibase.serializer.ext.GroovyChangeLogSerializer

import org.junit.Test
import org.junit.Before
import static org.junit.Assert.*


/**
 * An implementation
 *
 * @author Tim Berglund
 */
class GroovySerializerTests {
    def resourceAccessor
    def serializerFactory


    @Before
    void registerSerializer() {
        resourceAccessor = new DirectoryResourceAccessor(new File('.'))
        serializerFactory = ChangeLogSerializerFactory.instance
        ChangeLogSerializerFactory.getInstance().register(new GroovyChangeLogSerializer())
    }


    @Test
    void onlyGroovyFilesAreSupported() {
        def serializer = new GroovyChangeLogSerializer()
        assertArrayEquals(['groovy'] as String[], serializer.validFileExtensions)
    }


}
