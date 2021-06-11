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

import static io.undertow.jakartaee9.TransformConstants.VERSION_STRING;

/**
 * Parses an Jakarta EE8 compatible artifact name and transforms it into the
 * equivalent Jakarta EE9 compatible counterpart.
 *
 * @author Flavia Rainone
 */
class ArtifactNameTransformer {
    private static final String JAKARTA_EE9_SUFFIX = "-jakartaee9";

    static String transformArtifactName(String fileName, String artifactType) {
        return getArtifactName(fileName, artifactType) + JAKARTA_EE9_SUFFIX;
    }

    static String transformArtifactFileName(String fileName, String artifactType) {
        final String outputFileSuffix = JAKARTA_EE9_SUFFIX + "-" + VERSION_STRING + "." + artifactType;
        return getArtifactName(fileName, artifactType) + outputFileSuffix;
    }

    static String getArtifactName(String fileName, String artifactType) {
        final String inputFileSuffix = "-" + VERSION_STRING + "." +  artifactType;
        return fileName.substring(0, fileName.length() - inputFileSuffix.length());
    }
}
