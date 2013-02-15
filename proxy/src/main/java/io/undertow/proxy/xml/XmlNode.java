/**
 * JBoss, Home of Professional Open Source. Copyright 2012, Red Hat, Inc., and individual contributors as indicated by the @author
 * tags. See the copyright.txt file in the distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package io.undertow.proxy.xml;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * {@code Node}
 *
 * Created on Jul 5, 2012 at 2:24:42 PM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
@XmlRootElement(name = "node")
public class XmlNode implements Serializable {

    private String hostname;
    private int port;

    /**
     *
     */
    private static final long serialVersionUID = 533330892734892364L;

    /**
     * Create a new instance of {@code Node}
     */
    public XmlNode() {
        super();
    }

    /**
     * Getter for hostname.
     *
     * @return the hostname
     */
    @XmlElement
    public String getHostname() {
        return this.hostname;
    }

    /**
     * Setter for the hostname
     *
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * Getter for port
     *
     * @return the port
     */
    @XmlElement
    public int getPort() {
        return this.port;
    }

    /**
     * Setter for the port
     *
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return this.hostname + ":" + this.port;
    }

}
