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

package io.undertow.servlet.api;

/**
 *
 * Interface that is provided by the container to create a servlet / filter / listener
 * definition from a given class, based on the annotations present on the class.
 *
 * This is needed to allow for annotations to be taken into account when servlets etc are
 * added programatically.
 *
 * @author Stuart Douglas
 */
public interface ClassIntrospecter {

    <T> InstanceFactory<T> createInstanceFactory(final Class<T> clazz) throws NoSuchMethodException;

}
