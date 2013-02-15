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

/**
 * {@code Balancer}
 *
 * Created on Jun 12, 2012 at 3:32:28 PM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class Balancer implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 7107364166635260031L;
    /**
     * The number of the Balancer
     */
    private int number;
    /**
     * Name of the balancer. max size: 40, Default: "mycluster"
     */
    private String name = "mycluster";
    /**
     * Yes: use JVMRoute to stick a request to a node, No: ignore JVMRoute.
     * Default: "Yes"
     */
    private boolean stickySession = true;
    /**
     * Name of the cookie containing the sessionid. Max size: 30 Default:
     * "JSESSIONID"
     */
    private String stickySessionCookie = "JSESSIONID";
    /**
     * Name of the parametre containing the sessionid. Max size: 30. Default:
     * "jsessionid"
     */
    private String stickySessionPath = "jsessionid";
    /**
     * Yes: remove the sessionid (cookie or parameter) when the request can't be
     * routed to the right node. No: send it anyway. Default: "No"
     */
    private boolean stickySessionRemove = false;
    /**
     * Yes: Return an error if the request can't be routed according to
     * JVMRoute, No: Route it to another node. Default: "Yes"
     */
    private boolean stickySessionForce = true;
    /**
     * value in seconds: time to wait for an available worker. Default: "0" no
     * wait.
     */
    private int waitWorker = 0;
    /**
     * value: number of attempts to send the request to the backend server.
     * Default: "1"
     */
    private int maxattempts = 1;

    /**
     * Create a new instance of {@code Balancer}
     */
    public Balancer() {
        super();
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
        if (name != null && name.length() > 40) {
            this.name = name.substring(0, 40);
        } else {
            this.name = name;
        }
    }

    /**
     * Getter for stickySession
     *
     * @return the stickySession
     */
    public boolean isStickySession() {
        return this.stickySession;
    }

    /**
     * Setter for the stickySession
     *
     * @param stickySession
     *            the stickySession to set
     */
    public void setStickySession(boolean stickySession) {
        this.stickySession = stickySession;
    }

    /**
     * Getter for stickySessionCookie
     *
     * @return the stickySessionCookie
     */
    public String getStickySessionCookie() {
        return this.stickySessionCookie;
    }

    /**
     * Setter for the stickySessionCookie
     *
     * @param stickySessionCookie
     *            the stickySessionCookie to set
     */
    public void setStickySessionCookie(String stickySessionCookie) {
        if (stickySessionCookie != null && stickySessionCookie.length() > 30) {
            this.stickySessionCookie = stickySessionCookie.substring(0, 30);
        } else {
            this.stickySessionCookie = stickySessionCookie;
        }

    }

    /**
     * Getter for stickySessionPath
     *
     * @return the stickySessionPath
     */
    public String getStickySessionPath() {
        return this.stickySessionPath;
    }

    /**
     * Setter for the stickySessionPath
     *
     * @param stickySessionPath
     *            the stickySessionPath to set
     */
    public void setStickySessionPath(String stickySessionPath) {
        if (stickySessionPath != null && stickySessionPath.length() > 30) {
            this.stickySessionPath = stickySessionPath.substring(0, 30);
        } else {
            this.stickySessionPath = stickySessionPath;
        }
    }

    /**
     * Getter for stickySessionRemove
     *
     * @return the stickySessionRemove
     */
    public boolean isStickySessionRemove() {
        return this.stickySessionRemove;
    }

    /**
     * Setter for the stickySessionRemove
     *
     * @param stickySessionRemove
     *            the stickySessionRemove to set
     */
    public void setStickySessionRemove(boolean stickySessionRemove) {
        this.stickySessionRemove = stickySessionRemove;
    }

    /**
     * Getter for stickySessionForce
     *
     * @return the stickySessionForce
     */
    public boolean isStickySessionForce() {
        return this.stickySessionForce;
    }

    /**
     * Setter for the stickySessionForce
     *
     * @param stickySessionForce
     *            the stickySessionForce to set
     */
    public void setStickySessionForce(boolean stickySessionForce) {
        this.stickySessionForce = stickySessionForce;
    }

    /**
     * Getter for waitWorker
     *
     * @return the waitWorker
     */
    public int getWaitWorker() {
        return this.waitWorker;
    }

    /**
     * Setter for the waitWorker
     *
     * @param waitWorker
     *            the waitWorker to set
     */
    public void setWaitWorker(int waitWorker) {
        this.waitWorker = waitWorker;
    }

    /**
     * Getter for maxattempts
     *
     * @return the maxattempts
     */
    public int getMaxattempts() {
        return this.maxattempts;
    }

    /**
     * Setter for the maxattempts
     *
     * @param maxattempts
     *            the maxattempts to set
     */
    public void setMaxattempts(int maxattempts) {
        this.maxattempts = maxattempts;
    }

    /**
     * Getter for number
     *
     * @return the number
     */
    public int getNumber() {
        return this.number;
    }

    /**
     * Setter for the number
     *
     * @param number
     *            the number to set
     */
    public void setNumber(int number) {
        this.number = number;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return new StringBuilder("balancer: [").append(this.number).append("], Name: ")
                .append(this.name).append(", Sticky: ").append(this.stickySession ? 1 : 0)
                .append(" [").append(this.stickySessionCookie).append("]/[")
                .append(this.stickySessionPath).append("], remove: ")
                .append(this.stickySessionRemove ? 1 : 0).append(", force: ")
                .append(this.stickySessionForce ? 1 : 0).append(", Timeout: ")
                .append(this.waitWorker).append(", Maxtry: ").append(this.maxattempts).toString();
    }
}
