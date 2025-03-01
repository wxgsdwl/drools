/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.drools.ruleunits.impl;

import org.drools.ruleunits.api.RuleUnitInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InterpretedRuleUnitTest {

    @Test
    public void testHelloWorldInterpreted() {
        HelloWorld unit = new HelloWorld();
        unit.getStrings().add("Hello World");

        RuleUnitInstance<HelloWorld> unitInstance = InterpretedRuleUnit.instance(unit, false);
        assertEquals(1, unitInstance.fire());
        assertTrue(unit.getResults().contains("it worked!"));
    }

    @Test
    public void testHelloWorldCompiled() {
        HelloWorld unit = new HelloWorld();
        unit.getStrings().add("Hello World");

        RuleUnitInstance<HelloWorld> unitInstance = InterpretedRuleUnit.instance(unit, true);
        assertEquals(1, unitInstance.fire());
        assertTrue(unit.getResults().contains("it worked!"));
    }

    @Test
    public void testHelloWorldGenerated() {
        HelloWorld unit = new HelloWorld();
        unit.getStrings().add("Hello World");

        RuleUnitInstance<HelloWorld> unitInstance = InMemoryRuleUnitInstanceFactory.generateAndInstance(unit);
        assertEquals(1, unitInstance.fire());
        assertTrue(unit.getResults().contains("it worked!"));
    }
}
