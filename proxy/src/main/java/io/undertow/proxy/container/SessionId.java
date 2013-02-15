/**
 * JBoss, Home of Professional Open Source. Copyright 2013, Red Hat, Inc., and
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
import java.util.Date;

/**
 * {@code SessionId}
 *
 * @author Jean-Frederic Clere
 */
public class SessionId implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * SessionId
     */
    private String sessionId;

    /**
     * JVMRoute
     */
    private String jmvRoute;

    /**
      * Date last updated.
      */
    private Date updateTime;

    /**
     * Create a new instance of {@code SessionId}
     */
    public SessionId() {
        super();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getJmvRoute() {
        return jmvRoute;
    }

    public void setJmvRoute(String jmvRoute) {
        this.jmvRoute = jmvRoute;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

}
