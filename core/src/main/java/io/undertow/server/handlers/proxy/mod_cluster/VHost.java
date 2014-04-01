/**
 * JBoss, Home of Professional Open Source. Copyright 2012, Red Hat, Inc., and
 * individual contributors as indicated by the @author tags. See the
 * copyright.txt file in the distribution for a full listing of individual
 * contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
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
        this.aliases = Collections.unmodifiableList(new ArrayList<String>(b.aliases));
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
        private final List<String> aliases = new ArrayList<String>();

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
