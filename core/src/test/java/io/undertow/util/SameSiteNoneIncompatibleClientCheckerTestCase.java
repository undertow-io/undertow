/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package io.undertow.util;

import io.undertow.testutils.category.UnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(UnitTest.class)
public class SameSiteNoneIncompatibleClientCheckerTestCase {

    /**
     * List of incompatible User-Agents that contain bug in same-site cookie behavior.
     *
     * @see SameSiteNoneIncompatibleClientChecker
     */
    String[] incompatibleWebKitUserAgents = {
            // Safari on Mac OS X 10.14
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/11.2 Safari/605.1.15",
            // Safari on iOS 12
            "Mozilla/5.0 (iPhone; CPU iPhone OS 12_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.0 Mobile/15E148 Safari/604.1",
            // Chrome on iOS 12
            "Mozilla/5.0 (iPhone; CPU iPhone OS 12_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/69.0.3497.91 Mobile/15E148 Safari/605.1"
    };

    /**
     * List of compatible User-Agents that not containing bug in same-site cookie behavior.
     * There is also empty string and 'null' to check incorrect input User-Agent value behavior.
     *
     * @see SameSiteNoneIncompatibleClientChecker
     */
    String[] compatibleWebKitUserAgents = {
            // Safari on Mac OS X 10.15
            "Mozilla/6.0 (Macintosh; U; Intel Mac OS X 10_15_3) AppleWebKit/663.16 (KHTML, like Gecko) Version/10.0 Safari/663.16",
            // Safari on iOS 13
            "Mozilla/5.0 (iPhone; CPU iPhone OS 13_1_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.1 Mobile/15E148 Safari/604.1",
            // Chrome on iOS 13
            "Mozilla/5.0 (iPhone; CPU iPhone OS 13_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/77.0.3865.69 Mobile/15E148 Safari/605.1",
            "",
            null
    };

    /**
     * List of incompatible User-Agents that drop same-site cookies entirely.
     *
     * @see SameSiteNoneIncompatibleClientChecker
     */
    String[] incompatibleWebKitUserAgents2 = {
            // Chrome 51 on Windows
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36",
            // Chrome 62 on Windows
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.62 Safari/537.36",
            // UC Browser 11.3.8 on Android
            "Mozilla/5.0 (Linux; U; Android 7.0; en-US; ...) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 UCBrowser/11.3.8.976 U3/0.8.0 Mobile Safari/534.30",
            // UC Browser 12.13.0 on Android
            "Mozilla/5.0 (Linux; U; Android 9; en-US; ...) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.2987.108 UCBrowser/12.13.0.1207 Mobile Safari/537.36",
    };

    /**
     * List of compatible User-Agents that don't drop same-site cookies entirely.
     * There is also empty string and 'null' to check incorrect input User-Agent value behavior.
     *
     * @see SameSiteNoneIncompatibleClientChecker
     */
    String[] compatibleWebKitUserAgents2 = {
            // Chrome 72 on Windows
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36",
            // Chrome 78 on Linux
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36",
            // UC Browser 12.13.4 on Android
            "Mozilla/5.0 (Linux; U; Android 10; en-US; ...) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.2987.108 UCBrowser/12.13.4.1214 Mobile Safari/537.36",
            "",
            null
    };

    @Test
    public void testShouldSendSameSiteNone() {

        boolean result;

        for (String userAgent : incompatibleWebKitUserAgents) {
            result = SameSiteNoneIncompatibleClientChecker.shouldSendSameSiteNone(userAgent);
            Assert.assertFalse("Tested user-agent: '" + userAgent + "'", result);
        }

        for (String userAgent : compatibleWebKitUserAgents) {
            result = SameSiteNoneIncompatibleClientChecker.shouldSendSameSiteNone(userAgent);
            Assert.assertTrue("Tested user-agent: '" + userAgent + "'", result);
        }

        for (String userAgent : incompatibleWebKitUserAgents2) {
            result = SameSiteNoneIncompatibleClientChecker.shouldSendSameSiteNone(userAgent);
            Assert.assertFalse("Tested user-agent: '" + userAgent + "'", result);
        }

        for (String userAgent : compatibleWebKitUserAgents2) {
            result = SameSiteNoneIncompatibleClientChecker.shouldSendSameSiteNone(userAgent);
            Assert.assertTrue("Tested user-agent: '" + userAgent + "'", result);
        }
    }

    @Test
    public void testIsSameSiteNoneIncompatible() {

        boolean result;

        for (String userAgent : incompatibleWebKitUserAgents) {
            result = SameSiteNoneIncompatibleClientChecker.isSameSiteNoneIncompatible(userAgent);
            Assert.assertTrue("Tested user-agent: '" + userAgent + "'", result);
        }

        for (String userAgent : compatibleWebKitUserAgents) {
            result = SameSiteNoneIncompatibleClientChecker.isSameSiteNoneIncompatible(userAgent);
            Assert.assertFalse("Tested user-agent: '" + userAgent + "'", result);
        }

        for (String userAgent : incompatibleWebKitUserAgents2) {
            result = SameSiteNoneIncompatibleClientChecker.isSameSiteNoneIncompatible(userAgent);
            Assert.assertTrue("Tested user-agent: '" + userAgent + "'", result);
        }

        for (String userAgent : compatibleWebKitUserAgents2) {
            result = SameSiteNoneIncompatibleClientChecker.isSameSiteNoneIncompatible(userAgent);
            Assert.assertFalse("Tested user-agent: '" + userAgent + "'", result);
        }
    }
}
