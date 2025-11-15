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
import org.junit.Test
import liquibase.parser.groovy.exception.*

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
import static org.liquibase.groovy.helper.util.*
import static org.liquibase.groovy.delegate.DelegateUtil.*
import static Delegatee.argsToMap

/**
 * Tests for the static {@link DelegateUtil} class. This mostly validates our assumptions about how
 * Groovy truthiness works.
 *
 * @author Steven C. Saliman
 */
@CompileStatic
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

    interface exp {
        String prefix = 'prefix'
        String method = 'method'
        String fnDef = 'fnDef'
        String arg1 = 'arg1'
        String arg2 = 'arg2'
        String cl = 'cl'
        String map = 'map'
    }

    IO<List<String>, Object[]> p(List<String> i, Object[] o) {new IO<>(i,o)}
    IO<IO<List<String>, Object[]>, ParseErrorWithFileNLine> aio(IO<List<String>, Object[]> i, ParseErrorWithFileNLine o) {new IO<>(i,o)}

    static Map<String, Object> om (Map m) {m}

    void verifyException(String clue, Throwable ex, boolean needsClosure, List<String> argNames, Object... args) {
        try {
            argsToMap(exp.prefix, exp.method, needsClosure, exp.fnDef, argNames, args)
            assertTrue clue + " did not throw $ex", false
        } catch (e) {
            assertEquals clue, ex.class, e.class
            (ex as ParseErrorWithFileNLine).with {
                assertEquals clue, it.message, e.message
            }
        }
    }

    @Test
    void argsToMapErrorArgTwice() {
        verifyException 'arg twice', new ArgumentSetTwice(exp.method,exp.arg1,exp.prefix),
                false, [exp.arg1], [arg1: 1], 1
    }

    @Test
    void argsToMapErrorInvalidArg() {
        exp.with {
            // The one and only argument is missing
            verifyException 'invalid arg', new InvalidArguments(method, fnDef, prefix),
                    false, [arg1]
            Object[] args = objArr('arg1', 1, 2)
            verifyException 'invalid arg', new InvalidArguments(method, fnDef, prefix, args),
                    false, [arg1, arg2], args

            args += ({}  as Object)
            verifyException 'invalid arg', new InvalidArguments(method, fnDef, prefix, args),
                    true, [arg1, arg2, cl], args
        }
    }

    @Test
    void argsToMapAll() {
         exp.with {
            // Positional + named parameter call (with different types)
            Map res = om([arg1: '1', arg2: 2])
            assertMapEquals res,
                    argsToMap(prefix, method, false, fnDef, [arg1, arg2], [arg2: 2], '1')
            assertMapEquals res,
                    argsToMap(prefix, method, true, fnDef, [arg1, arg2, cl], [arg2: 2], '1', {})

             // Case: missingMethod called with positional parameters only
            assertMapEquals res,
                    argsToMap(prefix, method, true, fnDef, [arg1, arg2, cl], '1', 2, {})

            assertMapEquals res,
                    argsToMap(prefix, method, false, fnDef, [arg1, arg2, cl], '1', 2)
         }
    }

    @Test
    void testCompareSemvers() {
        ['2.1/2.2': -1
        ,'1.1/1.1': 0
        ,'1.2/1.1': 1
        ,'1.2.1/1.2.2': -1
        ,'1.10.1/1.9.1': 1
        ,'1.2/1.2.1': -1
        ].each {def v = it.key.split('/')
            assertEquals v[0]+ ' ? ' +v[1], it.value, compareSemvers(v[0],v[1])
        }

    }
}
