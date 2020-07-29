/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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
package io.undertow.jakartaee9;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import static org.jboss.logging.Logger.Level.INFO;

/**
 * Logger for Jakarta EE9 module.
 *
 * @author Flavia Rainone
 */
@MessageLogger(projectCode = "Undertow Jakarta EE9")
interface UndertowJakartaEE9Logger extends BasicLogger {
    UndertowJakartaEE9Logger LOGGER = Logger.getMessageLogger(
            UndertowJakartaEE9Logger.class, "io.undertow.jakartaee9");

    // Greeting
    @LogMessage(level = INFO)
    @Message(value = "Undertow Jakarta EE9 Transformer Version %s")
    void greeting(String version);

    @LogMessage(level = INFO)
    @Message(value = "Dependencies that will be used in the new generated modules:\n\t%s:%s-%s\n\t%s:%s-%s")
    void transformationInfo (String servletDependencyGroupId, String servletDependencyArtifactId, String servletDependencyVersion,
            String websocketsDependencyGroupId, String websocketsDependencyArtifactId, String websocketsDependencyVersion);

    @LogMessage(level = INFO)
    @Message(value = "Transforming %s into %s")
    void transformingFile (String fileName, String newFileName);

    @LogMessage(level = INFO)
    @Message(value = "Installing artifact file %s with pom %s")
    void installingFile (String artifactFileName, String pomFileName);
}
