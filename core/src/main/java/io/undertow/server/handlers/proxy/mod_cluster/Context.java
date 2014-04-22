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

/**
 * {@code Context}
 *
 * Created on Jun 12, 2012 at 4:24:58 PM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class Context {

    /**
     * {@code Status}
     * <p>
     * This class represents the status of the context, node etc.
     * </p>
     *
     * @author Jean-Frederic Clere
     */
    public enum Status {
        ENABLED,
        DISABLED,
        STOPPED,
        REMOVED;
    }

     /**
     * Status of the application: ENABLED, DISABLED or STOPPED.
     */
    private volatile Status status;
    /**
     * The context path. (String) URL to be mapped.
     */
    private final String path;

    /**
     * The corresponding node identification.
     */
    private final String jvmRoute;

    /**
     * The virtualhost id.
     */
    private final long hostid;

    /**
     * The number of active requests
     */
    private final long nbRequests;

    Context(ContextBuilder b) {
        status = b.status;
        path = b.path;
        jvmRoute = b.jvmRoute;
        hostid = b.hostid;
        nbRequests = b.nbRequests;
    }

    /**
     * @return true if this context is enabled
     */
    public boolean isEnabled() {
        return this.status == Status.ENABLED;
    }

    /**
     * @return true if this context is disabled
     */
    public boolean isDisabled() {
        return this.status == Status.DISABLED;
    }

    /**
     * @return true if this context is stopped
     */
    public boolean isStopped() {
        return this.status == Status.STOPPED;
    }
    /**
     * Getter for status
     *
     * @return the status of the context
     */
    public Status getStatus() {
        return this.status;
    }

    /**
     * Setter for the status
     *
     * @param status
     *            the status to set
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Context[Path: " + this.path + ", Status: " + this.status + ", Node: " + this.jvmRoute + ", Host: " + this.hostid +  "]";
    }

    /**
     * Getter for path
     *
     * @return the path
     */
    public String getPath() {
        return this.path;
    }

    public String getJvmRoute() {
        return jvmRoute;
    }

    public long getHostid() {
        return hostid;
    }

    public long getNbRequests() {
        return nbRequests;
    }

    public static ContextBuilder builder() {
        return new ContextBuilder();
    }

    public static final class ContextBuilder {

        ContextBuilder() {

        }
        private Status status;
        private String path;
        private String jvmRoute;
        private long hostid;
        private long nbRequests;

        public void setStatus(Status status) {
            this.status = status;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public void setJvmRoute(String jvmRoute) {
            this.jvmRoute = jvmRoute;
        }

        public void setHostid(long hostid) {
            this.hostid = hostid;
        }

        public void setNbRequests(long nbRequests) {
            this.nbRequests = nbRequests;
        }

        public Status getStatus() {
            return status;
        }

        public String getPath() {
            return path;
        }

        public String getJvmRoute() {
            return jvmRoute;
        }

        public long getHostid() {
            return hostid;
        }

        public long getNbRequests() {
            return nbRequests;
        }

        public Context build() {
            return new Context(this);
        }
    }
}
