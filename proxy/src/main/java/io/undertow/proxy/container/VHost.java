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
package io.undertow.proxy.container;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
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
public class VHost implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 7136466678635260031L;

    private String name;
    private String JVMRoute;
    private long id;

    /**
     * The list of aliases
     */
    private List<String> aliases;

    /**
     * Create a new instance of {@code VirtualHost}
     */
    public VHost() {
        this.aliases = new ArrayList<String>();
    }

    /**
     * Create a new instance of {@code VHost}
     *
     * @param name
     *            The name of the virtual Host
     * @param aliases
     *            the list of aliases of the virtual host
     */
    public VHost(String name, List<String> aliases) {
        this.name = name;
        this.aliases = aliases;
    }

    /**
     * Add the specified alias to the list
     *
     * @param alias
     *            the alias to be added
     * @return <tt>true</tt> if the {@code alias} was added successfully else
     *         <tt>false</tt>
     */
    public boolean addAlias(String alias) {
        return this.aliases.add(alias);
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

    /**
     * Getter for aliases list
     *
     * @return the list of aliases
     */
    public String[] getAliases() {
        return this.aliases.toArray(new String[this.aliases.size()]);
    }

    /**
     * Setter for the aliases list
     *
     * @param aliases
     *            the alias to set
     */
    public void setAliases(List<String> aliases) {
        this.aliases = aliases;
    }

    /**
     * Getter for name
     *
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Setter for the name
     *
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getJVMRoute() {
        return JVMRoute;
    }

    public void setJVMRoute(String jVMRoute) {
        JVMRoute = jVMRoute;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

}
