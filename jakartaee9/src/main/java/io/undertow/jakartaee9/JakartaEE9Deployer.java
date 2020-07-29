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

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.Properties;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

import static io.undertow.jakartaee9.TransformConstants.JAR_EXTENSION;
import static io.undertow.jakartaee9.TransformConstants.OUTPUT_DIR;
import static io.undertow.jakartaee9.TransformConstants.POM_EXTENSION;
import static io.undertow.jakartaee9.UndertowJakartaEE9Logger.LOGGER;

/**
 * Deploys Jakarta EE9 compatible artifacts.
 *
 * @author Flavia Rainone
 */
public class JakartaEE9Deployer {

    public static void main (String[] args)
            throws MavenInvocationException {
        File[] pomFiles = OUTPUT_DIR.listFiles(new FilenameFilter() {
            @Override public boolean accept(File dir, String name) {
                return name.endsWith(POM_EXTENSION);
            }
        });
        for (File pomFile : pomFiles) {
            final String pomFileName = pomFile.getAbsolutePath();
            final String jarFileName = pomFile.getAbsolutePath().substring(0, pomFileName.length() - POM_EXTENSION
                    .length()) + JAR_EXTENSION;
            LOGGER.installingFile(jarFileName, pomFile.getAbsolutePath());

            final Properties properties = new Properties();
            properties.put("file", jarFileName);
            properties.put("pomFile", pomFileName);

            final InvocationRequest request = new DefaultInvocationRequest();
            request.setGoals(Collections.singletonList("deploy:deploy-file"));
            request.setProperties(properties);
            Invoker invoker = new DefaultInvoker();
            invoker.execute( request );
        }
    }
}
