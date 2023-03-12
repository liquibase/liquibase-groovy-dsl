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

import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

/**
 * Tests for the static {@link DelegateUtil} class. This mostly validates our assumptions about how
 * Groovy truthiness works.
 *
 * @author Steven C. Saliman
 */
class DelegateUtilTest {
    @Test
    void parseTruthNoValueDefaultFalse() {
        assertFalse DelegateUtil.parseTruth(null, false)
    }

    @Test
    void parseTruthNoValueDefaultTrue() {
        assertTrue DelegateUtil.parseTruth(null, true)
    }

    @Test
    void parseTruthBooleanTrue() {
        assertTrue DelegateUtil.parseTruth(true, null)
    }

    @Test
    void parseTruthIntegerTrue() {
        assertTrue DelegateUtil.parseTruth(1, null)
    }

    @Test
    void parseTruthSingleQuoteStringOne() {
        assertTrue DelegateUtil.parseTruth('1', null)
    }

    @Test
    void parseTruthSingleQuoteStringYes() {
        assertTrue DelegateUtil.parseTruth('y', null)
    }

    @Test
    void parseTruthSingleQuoteStringTrue() {
        assertTrue DelegateUtil.parseTruth('true', null)
    }

    @Test
    void parseTruthDoubleQuoteStringOne() {
        assertTrue DelegateUtil.parseTruth("1", null)
    }

    @Test
    void parseTruthDoubleQuoteStringYes() {
        assertTrue DelegateUtil.parseTruth("y", null)
    }

    @Test
    void parseTruthSDoubleQuoteStringTrue() {
        assertTrue DelegateUtil.parseTruth("true", null)
    }

    @Test
    void parseTruthGStringOne() {
        assertTrue DelegateUtil.parseTruth("""1""", null)
    }

    @Test
    void parseTruthGStringYes() {
        assertTrue DelegateUtil.parseTruth("""y""", null)
    }

    @Test
    void parseTruthGStringTrue() {
        assertTrue DelegateUtil.parseTruth("""true""", null)
    }

    @Test
    void parseTruthBooleanFalse() {
        assertFalse DelegateUtil.parseTruth(false, null)
    }

    @Test
    void parseTruthIntegerFalse() {
        assertFalse DelegateUtil.parseTruth(0, null)
    }

    @Test
    void parseTruthSingleQuoteStringZero() {
        assertFalse DelegateUtil.parseTruth('0', null)
    }

    @Test
    void parseTruthSingleQuoteStringNo() {
        assertFalse DelegateUtil.parseTruth('n', null)
    }

    @Test
    void parseTruthSingleQuoteStringFalse() {
        assertFalse DelegateUtil.parseTruth('false', null)
    }

    @Test
    void parseTruthDoubleQuoteStringZero() {
        assertFalse DelegateUtil.parseTruth("0", null)
    }

    @Test
    void parseTruthDoubleQuoteStringNo() {
        assertFalse DelegateUtil.parseTruth("n", null)
    }

    @Test
    void parseTruthSDoubleQuoteStringFalse() {
        assertFalse DelegateUtil.parseTruth("false", null)
    }

    @Test
    void parseTruthGStringZero() {
        assertFalse DelegateUtil.parseTruth("""0""", null)
    }

    @Test
    void parseTruthGStringNo() {
        assertFalse DelegateUtil.parseTruth("""n""", null)
    }

    @Test
    void parseTruthGStringFalse() {
        assertFalse DelegateUtil.parseTruth("""false""", null)
    }

    @Test
    void parseTruthEmptyString() {
        assertFalse DelegateUtil.parseTruth("", null)
    }

    @Test
    void parseTruthGarbageString() {
        assertFalse DelegateUtil.parseTruth("asdf", null)
    }
}
