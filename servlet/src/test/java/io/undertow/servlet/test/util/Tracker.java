/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.undertow.servlet.test.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for tracking invocation order.
 *
 * @author Jozef Hartinger
 *
 */
public class Tracker {

    private static final List<String> actions = Collections.synchronizedList(new ArrayList<String>());

    public static void addAction(String action) {
        actions.add(action);
    }

    public static List<String> getActions() {
        return actions;
    }

    public static void reset() {
        actions.clear();
    }
}
