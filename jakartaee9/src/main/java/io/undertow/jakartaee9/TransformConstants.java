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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Some static constants used by other classes.
 *
 * @author Flavia Rainone
 */
class TransformConstants {
    private static final String MAVEN_PROJECT_DIR_PROPERTY = "maven.multiModuleProjectDirectory";
    private static final String JAKARTAEE9_DIR_NAME = File.separator + "jakartaee9";
    private static final String INPUT_DIR_NAME = File.separator + "target" + File.separator + "input";
    private static final String OUTPUT_DIR_NAME = File.separator + "target" + File.separator + "output";

    // input and output dirs
    static final File INPUT_DIR;
    static final File OUTPUT_DIR;

    // artifact types and extensions
    static final String JAR_TYPE = "jar";
    static final String JAR_EXTENSION = "." + JAR_TYPE;
    static final String POM_TYPE = "pom";
    static final String POM_EXTENSION = "." + POM_TYPE;

    // spec versions
    static final String SERVLET_SPEC_VERSION_PROPERTY = "version.jakarta.servlet-api";
    static final String WEBSOCKETS_SPEC_VERSION_PROPERTY = "version.jakarta.websocket-client-api";
    static final String SERVLET_SPEC_VERSION = System.getProperty(SERVLET_SPEC_VERSION_PROPERTY);
    static final String WEBSOCKETS_SPEC_VERSION = System.getProperty(WEBSOCKETS_SPEC_VERSION_PROPERTY);

    // servlet spec constants
    static final String SERVLET_SPEC_JAKARTAEE8_GROUP = "org.jboss.spec.javax.servlet";
    static final String SERVLET_SPEC_JAKARTAEE9_GROUP = "jakarta.servlet";
    static final String SERVLET_SPEC_JAKARTAEE8_ARTIFACT = "jboss-servlet-api_4.0_spec";
    static final String SERVLET_SPEC_JAKARTAEE9_ARTIFACT = "jakarta.servlet-api";

    // websockets constants
    static final String WEBSOCKETS_SPEC_JAKARTAEE8_GROUP = "org.jboss.spec.javax.websocket";
    static final String WEBSOCKETS_SPEC_JAKARTAEE9_GROUP = "jakarta.websocket";
    static final String WEBSOCKETS_SPEC_JAKARTAEE8_ARTIFACT = "jboss-websocket-api_1.1_spec";
    static final String WEBSOCKETS_SPEC_JAKARTAEE9_ARTIFACT = "jakarta.websocket-client-api";

    // Undertow version
    static final String VERSION_STRING;

    static {
        Properties versionProps = new Properties();
        String versionString = "(unknown)";
        try {
            final InputStream stream = TransformConstants.class.getClassLoader().getResourceAsStream("Version.properties");
            try {
                final InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                try {
                    versionProps.load(reader);
                    versionString = versionProps.getProperty("version", versionString);
                } finally {
                    try {
                        reader.close();
                    } catch (Throwable ignored) {
                    }
                }
            } finally {
                try {
                    stream.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        VERSION_STRING = versionString;

        String projectDirectoryPath = System.getProperty(MAVEN_PROJECT_DIR_PROPERTY);
        assert projectDirectoryPath != null;
        if (!projectDirectoryPath.endsWith(JAKARTAEE9_DIR_NAME))
            projectDirectoryPath = projectDirectoryPath + JAKARTAEE9_DIR_NAME;
        INPUT_DIR = new File(projectDirectoryPath + INPUT_DIR_NAME);
        OUTPUT_DIR = new File(projectDirectoryPath + OUTPUT_DIR_NAME);
        assert INPUT_DIR.exists();
        if (!OUTPUT_DIR.exists())
            OUTPUT_DIR.mkdir();
    }
}
