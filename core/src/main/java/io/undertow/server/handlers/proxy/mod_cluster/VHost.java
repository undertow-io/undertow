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
package io.undertow.server.handlers.proxy.mod_cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@code VHost}
 * <p>
 * This class is a representation of the virtual host
 * </p>
 * Created on Jun 12, 2012 at 3:33:21 PM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class VHost {

    private final String name;
    private final String JVMRoute;
    private long id;

    /**
     * The list of aliases
     */
    private final List<String> aliases;

    /**
     * Create a new instance of {@code VirtualHost}
     */
     VHost(VHostBuilder b) {
        this.name = b.name;
        this.JVMRoute = b.JVMRoute;
        this.aliases = Collections.unmodifiableList(new ArrayList<>(b.aliases));
    }

    /**
     * Getter for aliases list
     *
     * @return the list of aliases
     */
    public List<String> getAliases() {
        return aliases;
    }

    /**
     * Getter for name
     *
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    public String getJVMRoute() {
        return JVMRoute;
    }
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public static VHostBuilder builder() {
        return new VHostBuilder();
    }

    public static class VHostBuilder {

        private String name;
        private String JVMRoute;
        private long id;

        /**
         * The list of aliases
         */
        private final List<String> aliases = new ArrayList<>();

        VHostBuilder() {

        }


        public void setName(String name) {
            this.name = name;
        }

        public void setJVMRoute(String JVMRoute) {
            this.JVMRoute = JVMRoute;
        }

        public void setId(long id) {
            this.id = id;
        }

        /**
         * Add the collection of aliases to the list
         *
         * @param c
         *            the collection to add
         * @return <tt>true</tt> if the aliases was added successfully else
         *         <tt>false</tt>
         */
        public boolean addAliases(Collection<String> c) {
            return this.aliases.addAll(c);
        }

        /**
         * Remove the specified alias from the list of aliases
         *
         * @param alias
         *            the alias to be removed
         * @return <tt>true</tt> if the {@code alias} was removed else
         *         <tt>false</tt>
         */
        public boolean removeAlias(String alias) {
            return this.aliases.remove(alias);
        }

        public VHost build() {
            return new VHost(this);
        }
    }

}
