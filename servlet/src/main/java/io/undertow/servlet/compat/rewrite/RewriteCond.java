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

package io.undertow.servlet.compat.rewrite;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Remy Maucherat
 */
public class RewriteCond {

    public abstract static class Condition {
        public abstract boolean evaluate(String value, Resolver resolver);
    }

    public static class PatternCondition extends Condition {
        public Pattern pattern;
        public Matcher matcher = null;

        public boolean evaluate(String value, Resolver resolver) {
            Matcher m = pattern.matcher(value);
            if (m.matches()) {
                matcher = m;
                return true;
            } else {
                return false;
            }
        }
    }

    public static class LexicalCondition extends Condition {
        /**
         * -1: <
         * 0: =
         * 1: >
         */
        public int type = 0;
        public String condition;

        public boolean evaluate(String value, Resolver resolver) {
            int result = value.compareTo(condition);
            switch (type) {
                case -1:
                    return (result < 0);
                case 0:
                    return (result == 0);
                case 1:
                    return (result > 0);
                default:
                    return false;
            }

        }
    }

    public static class ResourceCondition extends Condition {
        /**
         * 0: -d (is directory ?)
         * 1: -f (is regular file ?)
         * 2: -s (is regular file with size ?)
         */
        public int type = 0;

        public boolean evaluate(String value, Resolver resolver) {
            switch (type) {
                case 0:
                    return true;
                case 1:
                    return true;
                case 2:
                    return true;
                default:
                    return false;
            }

        }
    }

    protected String testString = null;
    protected String condPattern = null;

    public String getCondPattern() {
        return condPattern;
    }

    public void setCondPattern(String condPattern) {
        this.condPattern = condPattern;
    }

    public String getTestString() {
        return testString;
    }

    public void setTestString(String testString) {
        this.testString = testString;
    }

    public void parse(Map<String, RewriteMap> maps) {
        test = new Substitution();
        test.setSub(testString);
        test.parse(maps);
        if (condPattern.startsWith("!")) {
            positive = false;
            condPattern = condPattern.substring(1);
        }
        // The counted condition is never anywhere assigned, there is a question whether it shouldn't look more like evaluate method.
        // commenting it out as this code is taken from Tomcats, where it lives for quite long time without change.
//        if (condPattern.startsWith("<")) {
//            LexicalCondition condition = new LexicalCondition();
//            condition.type = -1;
//            condition.condition = condPattern.substring(1);
//        } else if (condPattern.startsWith(">")) {
//            LexicalCondition condition = new LexicalCondition();
//            condition.type = 1;
//            condition.condition = condPattern.substring(1);
//        } else if (condPattern.startsWith("=")) {
//            LexicalCondition condition = new LexicalCondition();
//            condition.type = 0;
//            condition.condition = condPattern.substring(1);
//        } else if (condPattern.equals("-d")) {
//            ResourceCondition ncondition = new ResourceCondition();
//            ncondition.type = 0;
//        } else if (condPattern.equals("-f")) {
//            ResourceCondition ncondition = new ResourceCondition();
//            ncondition.type = 1;
//        } else if (condPattern.equals("-s")) {
//            ResourceCondition ncondition = new ResourceCondition();
//            ncondition.type = 2;
//        } else {
//            PatternCondition condition = new PatternCondition();
//            int flags = 0;
//            if (isNocase()) {
//                flags |= Pattern.CASE_INSENSITIVE;
//            }
//            condition.pattern = Pattern.compile(condPattern, flags);
//        }
    }

    public Matcher getMatcher() {
        Object condition = this.condition.get();
        if (condition instanceof PatternCondition) {
            return ((PatternCondition) condition).matcher;
        }
        return null;
    }

    /**
     * String representation.
     */
    public String toString() {
        // FIXME: Add flags if possible
        return "RewriteCond " + testString + " " + condPattern;
    }


    protected boolean positive = true;

    protected Substitution test = null;

    protected ThreadLocal<Condition> condition = new ThreadLocal<Condition>();

    /**
     * This makes the test case-insensitive, i.e., there is no difference between
     * 'A-Z' and 'a-z' both in the expanded TestString and the CondPattern. This
     * flag is effective only for comparisons between TestString and CondPattern.
     * It has no effect on filesystem and subrequest checks.
     */
    public boolean nocase = false;

    /**
     * Use this to combine rule conditions with a local OR instead of the implicit AND.
     */
    public boolean ornext = false;

    /**
     * Evaluate the condition based on the context
     *
     * @param rule corresponding matched rule
     * @param cond last matched condition
     * @return
     */
    public boolean evaluate(Matcher rule, Matcher cond, Resolver resolver) {
        String value = test.evaluate(rule, cond, resolver);
        if (nocase) {
            value = value.toLowerCase(Locale.ENGLISH);
        }
        Condition condition = this.condition.get();
        if (condition == null) {
            if (condPattern.startsWith("<")) {
                LexicalCondition ncondition = new LexicalCondition();
                ncondition.type = -1;
                ncondition.condition = condPattern.substring(1);
                condition = ncondition;
            } else if (condPattern.startsWith(">")) {
                LexicalCondition ncondition = new LexicalCondition();
                ncondition.type = 1;
                ncondition.condition = condPattern.substring(1);
                condition = ncondition;
            } else if (condPattern.startsWith("=")) {
                LexicalCondition ncondition = new LexicalCondition();
                ncondition.type = 0;
                ncondition.condition = condPattern.substring(1);
                condition = ncondition;
            } else if (condPattern.equals("-d")) {
                ResourceCondition ncondition = new ResourceCondition();
                ncondition.type = 0;
                condition = ncondition;
            } else if (condPattern.equals("-f")) {
                ResourceCondition ncondition = new ResourceCondition();
                ncondition.type = 1;
                condition = ncondition;
            } else if (condPattern.equals("-s")) {
                ResourceCondition ncondition = new ResourceCondition();
                ncondition.type = 2;
                condition = ncondition;
            } else {
                PatternCondition ncondition = new PatternCondition();
                int flags = 0;
                if (isNocase()) {
                    flags |= Pattern.CASE_INSENSITIVE;
                }
                ncondition.pattern = Pattern.compile(condPattern, flags);
                condition = ncondition;
            }
            this.condition.set(condition);
        }
        if (positive) {
            return condition.evaluate(value, resolver);
        } else {
            return !condition.evaluate(value, resolver);
        }
    }

    public boolean isNocase() {
        return nocase;
    }

    public void setNocase(boolean nocase) {
        this.nocase = nocase;
    }

    public boolean isOrnext() {
        return ornext;
    }

    public void setOrnext(boolean ornext) {
        this.ornext = ornext;
    }

    public boolean isPositive() {
        return positive;
    }

    public void setPositive(boolean positive) {
        this.positive = positive;
    }

}
