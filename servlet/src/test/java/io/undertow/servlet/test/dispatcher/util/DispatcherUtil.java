/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
package io.undertow.servlet.test.dispatcher.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Moulali Shikalwadi
 */
public class DispatcherUtil {

    /**
     * Utility method to check if it contains a word
     *
     * @param fullString and partString
     *
     */
    public static boolean containsWord(String fullString, String partString){
        String pattern = "\\b"+partString+"\\b";
        Pattern p= Pattern.compile(pattern);
        Matcher m=p.matcher(fullString);
        return m.find();
    }
}
