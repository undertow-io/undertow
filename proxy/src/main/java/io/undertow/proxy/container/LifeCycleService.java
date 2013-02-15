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

/**
 * {@code LifeCycleService}
 *
 * Created on Jul 6, 2012 at 7:04:17 PM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public interface LifeCycleService {

    /**
     * Initialize the service
     *
     * @throws Exception
     */
    void init() throws Exception;

    /**
     * Start the service and make it available
     *
     * @throws Exception
     */
    void start() throws Exception;

    /**
     * Pause the service to make it unavailable. This method does not stop the service.
     *
     * @throws Exception
     */
    void pause() throws Exception;

    /**
     * Stop the service to make it unavailable.
     *
     * @throws Exception
     */
    void stop() throws Exception;

    /**
     * Destroy the service.
     *
     * @throws Exception
     */
    void destroy() throws Exception;

    /**
     * @return <tt>true</tt> if the service was already initialized, else <tt>false</tt>
     */
    boolean isInitialized();

    /**
     * @return <tt>true</tt> if the service was already started, else <tt>false</tt>
     */
    boolean isStarted();

    /**
     * @return <tt>true</tt> if the service was paused, else <tt>false</tt>
     */
    boolean isPaused();

    /**
     * @return <tt>true</tt> if the service was stopped, else <tt>false</tt>
     */
    boolean isStopped();
}
