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
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * {@code Nodes}
 *
 * Created on Jul 5, 2012 at 2:42:31 PM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
@XmlRootElement(name = "nodes")
public class XmlNodes implements Serializable {

    private List<XmlNode> nodes;

    /**
     *
     */
    private static final long serialVersionUID = 982132341234234234L;

    /**
     * Create a new instance of {@code Nodes}
     */
    public XmlNodes() {
        super();
    }

    /**
     * Getter for nodes
     *
     * @return the nodes
     */
    @XmlElement(name = "node")
    public List<XmlNode> getNodes() {
        return this.nodes;
    }

    /**
     * Setter for the nodes
     *
     * @param nodes the nodes to set
     */
    public void setNodes(List<XmlNode> nodes) {
        this.nodes = nodes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (XmlNode n : this.nodes) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(n);
            i++;
        }
        return sb.append(']').toString();
    }
}
