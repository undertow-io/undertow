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
package io.undertow.proxy.container;

/**
 * {@code LifeCycleServiceAdapter} An abstract adapter class for receiving life cycle events. The methods in this class are
 * empty. This class exists as convenience for creating service objects.
 *
 *
 * Created on Jul 6, 2012 at 7:08:24 PM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public abstract class LifeCycleServiceAdapter implements LifeCycleService {

    private boolean initialized = false;
    private boolean started;
    private boolean paused;
    private boolean stopped = true;

    /**
     * Create a new instance of {@code LifeCycleServiceAdapter}
     */
    public LifeCycleServiceAdapter() {
        super();
    }

    /*
     * (non-Javadoc).
     *
     * @see org.apache.LifeCycleService#init()
     */
    @Override
    public void init() throws Exception {
        // NOPE
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.LifeCycleService#start()
     */
    @Override
    public void start() throws Exception {
        // NOPE
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.LifeCycleService#pause()
     */
    @Override
    public void pause() throws Exception {
        // NOPE
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.LifeCycleService#stop()
     */
    @Override
    public void stop() throws Exception {
        // NOPE
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.LifeCycleService#destroy()
     */
    @Override
    public void destroy() throws Exception {
        // NOPE
    }

    /**
     * Set the new value of the initialized tag
     *
     * @param value
     */
    protected void setInitialized(boolean value) {
        this.initialized = value;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.LifeCycleService#isInitialized()
     */
    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    /**
     * Set the new value of the started tag
     *
     * @param value
     */
    protected void setStarted(boolean value) {
        this.started = value;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.LifeCycleService#isStarted()
     */
    @Override
    public boolean isStarted() {
        return this.started;
    }

    /**
     * Set the new value of the paused tag
     *
     * @param value
     */
    protected void setPaused(boolean value) {
        this.paused = value;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.LifeCycleService#isPaused()
     */
    @Override
    public boolean isPaused() {
        return this.paused;
    }

    /**
     * Set the new value of the stopped tag
     *
     * @param value
     */
    protected void setStopped(boolean value) {
        this.stopped = value;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.LifeCycleService#isStopped()
     */
    @Override
    public boolean isStopped() {
        return this.stopped;
    }
}
