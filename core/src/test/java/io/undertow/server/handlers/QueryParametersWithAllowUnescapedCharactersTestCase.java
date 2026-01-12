/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

package io.undertow.server.handlers;

import io.undertow.testutils.DefaultServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.xnio.OptionMap;

import static io.undertow.UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL;

/**
 * Tests that query parameters are handled correctly with {@link io.undertow.UndertowOptions#ALLOW_UNESCAPED_CHARACTERS_IN_URL
 * allow unescaped characters option} enabled.
 *
 * @author Stuart Douglas
 */
public class QueryParametersWithAllowUnescapedCharactersTestCase extends AbstractQueryParametersTest {

    @DefaultServer.BeforeServerStarts
    public static void setServerOptions() {
        DefaultServer.setServerOptions(OptionMap.create(ALLOW_UNESCAPED_CHARACTERS_IN_URL, true));
    }

    @DefaultServer.AfterServerStops
    public static void clearServerOptions() {
        DefaultServer.setServerOptions(OptionMap.EMPTY);
    }

    @BeforeClass // this is run after the server starts
    public static void disableProxyUnescapedCharactersInURL() {
        // disable it in proxy or else decoded URL is sent to the Undertow server
        DefaultServer.setProxyOptions(OptionMap.create(ALLOW_UNESCAPED_CHARACTERS_IN_URL, false));
    }

    @AfterClass
    public static void clearProxyOptions() {
        // disable it in proxy or else decoded URL is sent to the Undertow server
        DefaultServer.setProxyOptions(OptionMap.EMPTY);
    }

    @BeforeClass
    public static void setQueryStringsArray() {
        // format is: {queryString, expected result}
        queryStrings = new String[][] { new String[] { "/path?unicode=Iñtërnâtiônàližætiøn",
                //"unicode=Iñtërnâtiônàližætiøn{unicode=>Iñtërnâtiônàližætiøn}" },
                "unicode=I%C3%B1t%C3%ABrn%C3%A2ti%C3%B4n%C3%A0li%C5%BE%C3%A6ti%C3%B8n{unicode=>Iñtërnâtiônàližætiøn}" },
                //new String[] { "/path?a=b&value=bb%20bb", "a=b&value=bb bb{a=>b,value=>bb bb}" },
                new String[] { "/path?a=b&value=bb%20bb", "a=b&value=bb%20bb{a=>b,value=>bb bb}" },
                new String[] { "/path?a=b&value=bb&value=cc", "a=b&value=bb&value=cc{a=>b,value=>[bb,cc]}" },
                new String[] { "/path?&a=b&value=bb&&value=cc", "&a=b&value=bb&&value=cc{a=>b,value=>[bb,cc]}" },
                // Specifying some query parameters with empty by intentional for the test purpose. These should be ignored.
                new String[] { "/path?a=b&value=bb&value=cc&s%20&t%20",
                        //"a=b&value=bb&value=cc&s &t {a=>b,s =>,t =>,value=>[bb,cc]}" },
                        "a=b&value=bb&value=cc&s%20&t%20{a=>b,s =>,t =>,value=>[bb,cc]}" },
                new String[] { "/path?a=b&value=bb&value=cc&s%20&t%20&",
                        //"a=b&value=bb&value=cc&s &t &{a=>b,s =>,t =>,value=>[bb,cc]}" },
                        "a=b&value=bb&value=cc&s%20&t%20&{a=>b,s =>,t =>,value=>[bb,cc]}" },
                new String[] { "/path?a=b&value=bb&value=cc&s%20&t%20&u",
                        //"a=b&value=bb&value=cc&s &t &u{a=>b,s =>,t =>,u=>,value=>[bb,cc]}" } };
                        "a=b&value=bb&value=cc&s%20&t%20&u{a=>b,s =>,t =>,u=>,value=>[bb,cc]}" } };
    }
}
