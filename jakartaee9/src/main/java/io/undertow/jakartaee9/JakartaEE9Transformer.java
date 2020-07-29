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

import org.wildfly.transformer.tool.api.ToolUtils;

import static io.undertow.jakartaee9.TransformConstants.INPUT_DIR;
import static io.undertow.jakartaee9.TransformConstants.JAR_EXTENSION;
import static io.undertow.jakartaee9.TransformConstants.JAR_TYPE;
import static io.undertow.jakartaee9.TransformConstants.OUTPUT_DIR;
import static io.undertow.jakartaee9.TransformConstants.SERVLET_SPEC_JAKARTAEE9_ARTIFACT;
import static io.undertow.jakartaee9.TransformConstants.SERVLET_SPEC_JAKARTAEE9_GROUP;
import static io.undertow.jakartaee9.TransformConstants.SERVLET_SPEC_VERSION;
import static io.undertow.jakartaee9.TransformConstants.WEBSOCKETS_SPEC_JAKARTAEE9_ARTIFACT;
import static io.undertow.jakartaee9.TransformConstants.WEBSOCKETS_SPEC_JAKARTAEE9_GROUP;
import static io.undertow.jakartaee9.TransformConstants.WEBSOCKETS_SPEC_VERSION;
import static io.undertow.jakartaee9.UndertowJakartaEE9Logger.LOGGER;

/**
 * Transforms JakartaEE8 Undertow artifacts files into corresponding Jakarta EE9 compatible artifact files.
 *
 * @author Flavia Rainone
 */
public class JakartaEE9Transformer {

    public static void main (String[] args) throws IOException {

        LOGGER.greeting(TransformConstants.VERSION_STRING);
        LOGGER.transformationInfo(SERVLET_SPEC_JAKARTAEE9_GROUP, SERVLET_SPEC_JAKARTAEE9_ARTIFACT,
                SERVLET_SPEC_VERSION, WEBSOCKETS_SPEC_JAKARTAEE9_GROUP, WEBSOCKETS_SPEC_JAKARTAEE9_ARTIFACT,
                WEBSOCKETS_SPEC_VERSION);


        PomTransformer pomTransformer = new PomTransformer(INPUT_DIR, OUTPUT_DIR);
        pomTransformer.transformPoms();
        for (File file : INPUT_DIR.listFiles()) {
            if (file.getName().endsWith(JAR_EXTENSION)) {
                final String newFileName = ArtifactNameTransformer.transformArtifactFileName(file.getName(),
                        JAR_TYPE);
                LOGGER.transformingFile(file.getName(), newFileName);
                ToolUtils.transformJarFile(file, OUTPUT_DIR, null);
                File generatedFile = new File(OUTPUT_DIR.getAbsolutePath() + File.separatorChar + file.getName());
                assert generatedFile.exists();
                generatedFile.renameTo(new File(OUTPUT_DIR.getAbsolutePath() + File.separatorChar + newFileName));
            }
        }
    }
}
