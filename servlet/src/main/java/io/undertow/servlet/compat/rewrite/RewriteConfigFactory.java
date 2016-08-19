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

import io.undertow.servlet.UndertowServletLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class RewriteConfigFactory {

    public static RewriteConfig build(InputStream inputStream) {

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        try {
            return parse(reader);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
            }
        }
    }


    private static RewriteConfig parse(BufferedReader reader) {
        ArrayList<RewriteRule> rules = new ArrayList<RewriteRule>();
        ArrayList<RewriteCond> conditions = new ArrayList<RewriteCond>();
        Map<String, RewriteMap> maps = new HashMap<>();
        while (true) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                Object result = parse(line);
                if (result instanceof RewriteRule) {
                    RewriteRule rule = (RewriteRule) result;
                    if (UndertowServletLogger.ROOT_LOGGER.isDebugEnabled()) {
                        UndertowServletLogger.ROOT_LOGGER.debug("Add rule with pattern " + rule.getPatternString()
                                + " and substitution " + rule.getSubstitutionString());
                    }
                    for (int i = (conditions.size() - 1); i > 0; i--) {
                        if (conditions.get(i - 1).isOrnext()) {
                            conditions.get(i).setOrnext(true);
                        }
                    }
                    for (int i = 0; i < conditions.size(); i++) {
                        if (UndertowServletLogger.ROOT_LOGGER.isDebugEnabled()) {
                            RewriteCond cond = conditions.get(i);
                            UndertowServletLogger.ROOT_LOGGER.debug("Add condition " + cond.getCondPattern()
                                    + " test " + cond.getTestString() + " to rule with pattern "
                                    + rule.getPatternString() + " and substitution "
                                    + rule.getSubstitutionString() + (cond.isOrnext() ? " [OR]" : "")
                                    + (cond.isNocase() ? " [NC]" : ""));
                        }
                        rule.addCondition(conditions.get(i));
                    }
                    conditions.clear();
                    rules.add(rule);
                } else if (result instanceof RewriteCond) {
                    conditions.add((RewriteCond) result);
                } else if (result instanceof Object[]) {
                    String mapName = (String) ((Object[]) result)[0];
                    RewriteMap map = (RewriteMap) ((Object[]) result)[1];
                    maps.put(mapName, map);
                    //if (map instanceof Lifecycle) {
                    //    ((Lifecycle) map).start();
                    //}
                }
            } catch (IOException e) {
                UndertowServletLogger.ROOT_LOGGER.errorReadingRewriteConfiguration(e);
            }
        }
        RewriteRule[] rulesArray = rules.toArray(new RewriteRule[0]);

        // Finish parsing the rules
        for (int i = 0; i < rulesArray.length; i++) {
            rulesArray[i].parse(maps);
        }
        return new RewriteConfig(rulesArray, maps);
    }

    /**
     * This factory method will parse a line formed like:
     *
     * Example:
     *  RewriteCond %{REMOTE_HOST}  ^host1.*  [OR]
     *
     * @param line
     * @return
     */
    private static Object parse(String line) {
        StringTokenizer tokenizer = new StringTokenizer(line);
        if (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (token.equals("RewriteCond")) {
                // RewriteCond TestString CondPattern [Flags]
                RewriteCond condition = new RewriteCond();
                if (tokenizer.countTokens() < 2) {
                    throw UndertowServletLogger.ROOT_LOGGER.invalidRewriteConfiguration(line);
                }
                condition.setTestString(tokenizer.nextToken());
                condition.setCondPattern(tokenizer.nextToken());
                if (tokenizer.hasMoreTokens()) {
                    String flags = tokenizer.nextToken();
                    if (flags.startsWith("[") && flags.endsWith("]")) {
                        flags = flags.substring(1, flags.length() - 1);
                    }
                    StringTokenizer flagsTokenizer = new StringTokenizer(flags, ",");
                    while (flagsTokenizer.hasMoreElements()) {
                        parseCondFlag(line, condition, flagsTokenizer.nextToken());
                    }
                }
                return condition;
            } else if (token.equals("RewriteRule")) {
                // RewriteRule Pattern Substitution [Flags]
                RewriteRule rule = new RewriteRule();
                if (tokenizer.countTokens() < 2) {
                    throw UndertowServletLogger.ROOT_LOGGER.invalidRewriteConfiguration(line);
                }
                rule.setPatternString(tokenizer.nextToken());
                rule.setSubstitutionString(tokenizer.nextToken());
                if (tokenizer.hasMoreTokens()) {
                    String flags = tokenizer.nextToken();
                    if (flags.startsWith("[") && flags.endsWith("]")) {
                        flags = flags.substring(1, flags.length() - 1);
                    }
                    StringTokenizer flagsTokenizer = new StringTokenizer(flags, ",");
                    while (flagsTokenizer.hasMoreElements()) {
                        parseRuleFlag(line, rule, flagsTokenizer.nextToken());
                    }
                }
                return rule;
            } else if (token.equals("RewriteMap")) {
                // RewriteMap name rewriteMapClassName whateverOptionalParameterInWhateverFormat
                if (tokenizer.countTokens() < 2) {
                    throw UndertowServletLogger.ROOT_LOGGER.invalidRewriteConfiguration(line);
                }
                String name = tokenizer.nextToken();
                String rewriteMapClassName = tokenizer.nextToken();
                RewriteMap map = null;
                try {
                    map = (RewriteMap) (Class.forName(rewriteMapClassName).newInstance());
                } catch (Exception e) {
                    throw UndertowServletLogger.ROOT_LOGGER.invalidRewriteMap(rewriteMapClassName);
                }
                if (tokenizer.hasMoreTokens()) {
                    map.setParameters(tokenizer.nextToken());
                }
                Object[] result = new Object[2];
                result[0] = name;
                result[1] = map;
                return result;
            } else if (token.startsWith("#")) {
                // it's a comment, ignore it
            } else {
                throw UndertowServletLogger.ROOT_LOGGER.invalidRewriteConfiguration(line);
            }
        }
        return null;
    }


    /**
     * Parser for RewriteCond flags.
     *
     * @param condition
     * @param flag
     */
    protected static void parseCondFlag(String line, RewriteCond condition, String flag) {
        if (flag.equals("NC") || flag.equals("nocase")) {
            condition.setNocase(true);
        } else if (flag.equals("OR") || flag.equals("ornext")) {
            condition.setOrnext(true);
        } else {
            throw UndertowServletLogger.ROOT_LOGGER.invalidRewriteFlags(line, flag);
        }
    }


    /**
     * Parser for ReweriteRule flags.
     *
     * @param rule
     * @param flag
     */
    protected static void parseRuleFlag(String line, RewriteRule rule, String flag) {
        if (flag.equals("chain") || flag.equals("C")) {
            rule.setChain(true);
        } else if (flag.startsWith("cookie=") || flag.startsWith("CO=")) {
            rule.setCookie(true);
            if (flag.startsWith("cookie")) {
                flag = flag.substring("cookie=".length());
            } else if (flag.startsWith("CO=")) {
                flag = flag.substring("CO=".length());
            }
            StringTokenizer tokenizer = new StringTokenizer(flag, ":");
            if (tokenizer.countTokens() < 2) {
                throw UndertowServletLogger.ROOT_LOGGER.invalidRewriteFlags(line);
            }
            rule.setCookieName(tokenizer.nextToken());
            rule.setCookieValue(tokenizer.nextToken());
            if (tokenizer.hasMoreTokens()) {
                rule.setCookieDomain(tokenizer.nextToken());
            }
            if (tokenizer.hasMoreTokens()) {
                try {
                    rule.setCookieLifetime(Integer.parseInt(tokenizer.nextToken()));
                } catch (NumberFormatException e) {
                    throw UndertowServletLogger.ROOT_LOGGER.invalidRewriteFlags(line);
                }
            }
            if (tokenizer.hasMoreTokens()) {
                rule.setCookiePath(tokenizer.nextToken());
            }
            if (tokenizer.hasMoreTokens()) {
                rule.setCookieSecure(Boolean.parseBoolean(tokenizer.nextToken()));
            }
            if (tokenizer.hasMoreTokens()) {
                rule.setCookieHttpOnly(Boolean.parseBoolean(tokenizer.nextToken()));
            }
        } else if (flag.startsWith("env=") || flag.startsWith("E=")) {
            rule.setEnv(true);
            if (flag.startsWith("env=")) {
                flag = flag.substring("env=".length());
            } else if (flag.startsWith("E=")) {
                flag = flag.substring("E=".length());
            }
            int pos = flag.indexOf(':');
            if (pos == -1 || (pos + 1) == flag.length()) {
                throw UndertowServletLogger.ROOT_LOGGER.invalidRewriteFlags(line);
            }
            rule.addEnvName(flag.substring(0, pos));
            rule.addEnvValue(flag.substring(pos + 1));
        } else if (flag.startsWith("forbidden") || flag.startsWith("F")) {
            rule.setForbidden(true);
        } else if (flag.startsWith("gone") || flag.startsWith("G")) {
            rule.setGone(true);
        } else if (flag.startsWith("host") || flag.startsWith("H")) {
            rule.setHost(true);
        } else if (flag.startsWith("last") || flag.startsWith("L")) {
            rule.setLast(true);
        } else if (flag.startsWith("next") || flag.startsWith("N")) {
            rule.setNext(true);
        } else if (flag.startsWith("nocase") || flag.startsWith("NC")) {
            rule.setNocase(true);
        } else if (flag.startsWith("noescape") || flag.startsWith("NE")) {
            rule.setNoescape(true);
        /* Proxy not supported, would require strong proxy capabilities
        } else if (flag.startsWith("proxy") || flag.startsWith("P")) {
            rule.setProxy(true);*/
        } else if (flag.startsWith("qsappend") || flag.startsWith("QSA")) {
            rule.setQsappend(true);
        } else if (flag.startsWith("redirect") || flag.startsWith("R")) {
            if (flag.startsWith("redirect=")) {
                flag = flag.substring("redirect=".length());
                rule.setRedirect(true);
                rule.setRedirectCode(Integer.parseInt(flag));
            } else if (flag.startsWith("R=")) {
                flag = flag.substring("R=".length());
                rule.setRedirect(true);
                rule.setRedirectCode(Integer.parseInt(flag));
            } else {
                rule.setRedirect(true);
                rule.setRedirectCode(HttpServletResponse.SC_FOUND);
            }
        } else if (flag.startsWith("skip") || flag.startsWith("S")) {
            if (flag.startsWith("skip=")) {
                flag = flag.substring("skip=".length());
            } else if (flag.startsWith("S=")) {
                flag = flag.substring("S=".length());
            }
            rule.setSkip(Integer.parseInt(flag));
        } else if (flag.startsWith("type") || flag.startsWith("T")) {
            if (flag.startsWith("type=")) {
                flag = flag.substring("type=".length());
            } else if (flag.startsWith("T=")) {
                flag = flag.substring("T=".length());
            }
            rule.setType(true);
            rule.setTypeValue(flag);
        } else {
            throw UndertowServletLogger.ROOT_LOGGER.invalidRewriteFlags(line, flag);
        }
    }

}
