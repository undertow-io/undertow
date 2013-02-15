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

import javax.xml.bind.annotation.XmlRegistry;

/**
 * {@code ObjectFactory}
 *
 * Created on Jul 5, 2012 at 2:24:55 PM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
@XmlRegistry
public class ObjectFactory {

    /**
     * Create a new instance of {@code ObjectFactory}
     */
    public ObjectFactory() {
        super();
    }

    /**
     * @return a new instance of {@code Node}
     */
    public XmlNode createXmlNode() {
        return new XmlNode();
    }

    /**
     * @return a new instance of {@code XmlNodes}
     */
    public XmlNodes createXmlNodes() {
        return new XmlNodes();
    }

}
