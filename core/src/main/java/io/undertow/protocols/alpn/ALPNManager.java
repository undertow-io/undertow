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

package io.undertow.protocols.alpn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import javax.net.ssl.SSLEngine;

/**
 * @author Stuart Douglas
 */
public class ALPNManager {

    private final List<ALPNProvider> alpnProviders;

    public static final ALPNManager INSTANCE = new ALPNManager(ALPNManager.class.getClassLoader());

    public ALPNManager(ClassLoader classLoader) {
        ServiceLoader<ALPNProvider> loader = ServiceLoader.load(ALPNProvider.class, classLoader);
        List<ALPNProvider> provider = new ArrayList<>();
        for(ALPNProvider prov : loader) {
            provider.add(prov);
        }
        Collections.sort(provider, new Comparator<ALPNProvider>() {
            @Override
            public int compare(ALPNProvider o1, ALPNProvider o2) {
                return Integer.compare(o2.getPriority(), o1.getPriority()); //highest first
            }
        });
        this.alpnProviders = Collections.unmodifiableList(provider);
    }

    public ALPNProvider getProvider(SSLEngine engine) {
        for(ALPNProvider provider: alpnProviders) {
            if(provider.isEnabled(engine)) {
                return provider;
            }
        }
        return null;
    }

}
