/**
 * JBoss, Home of Professional Open Source. Copyright 2012, Red Hat, Inc., and individual contributors as indicated by the @author
 * tags. See the copyright.txt file in the distribution for a full listing of individual contributors. This is free software;
 * you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option) any later version. This software is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details. You should have received a copy of the
 * GNU Lesser General Public License along with this software; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package io.undertow.proxy.xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.jboss.logging.Logger;

/**
 * {@code XmlConfig} Created on Jul 5, 2012 at 2:30:02 PM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class XmlConfig {

    /**
     *
     */
    private static final Logger logger = Logger.getLogger(XmlConfig.class);
    /**
     *
     */
    private static final String CONTEXT_PATH = XmlConfig.class.getPackage().getName();

    /**
     *
     */
    private static final String CONFIG_PATH = "conf" + File.separatorChar + "nodes.xml";

    /**
     * Create a new instance of {@code XmlConfig}.
     */
    private XmlConfig() {
        super();
    }

    /**
     * @return the list of nodes or null if it can't parse the file.
     * @throws Exception
     */
    public static XmlNodes loadNodes() throws Exception {

        FileInputStream fis = new FileInputStream(CONFIG_PATH);
        try {
            logger.info("Loading nodes configurations");
            XmlNodes nodes = (XmlNodes) xmlToObject(fis);
            return nodes;
        } catch (Throwable t) {
            logger.error("Unable to load nodes", t);
            t.printStackTrace();
            return null;
        }
    }

    /**
     * @throws Exception
     */
    private static Object xmlToObject(InputStream is) throws Exception {
        JAXBContext jc = JAXBContext.newInstance(CONTEXT_PATH);
        Unmarshaller u = jc.createUnmarshaller();
        return u.unmarshal(is);
    }
}
