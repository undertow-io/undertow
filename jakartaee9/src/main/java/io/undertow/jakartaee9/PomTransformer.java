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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static io.undertow.jakartaee9.ArtifactNameTransformer.getArtifactName;
import static io.undertow.jakartaee9.ArtifactNameTransformer.transformArtifactFileName;
import static io.undertow.jakartaee9.ArtifactNameTransformer.transformArtifactName;
import static io.undertow.jakartaee9.TransformConstants.POM_TYPE;
import static io.undertow.jakartaee9.TransformConstants.POM_EXTENSION;
import static io.undertow.jakartaee9.TransformConstants.SERVLET_SPEC_JAKARTAEE8_ARTIFACT;
import static io.undertow.jakartaee9.TransformConstants.SERVLET_SPEC_JAKARTAEE8_GROUP;
import static io.undertow.jakartaee9.TransformConstants.SERVLET_SPEC_JAKARTAEE9_ARTIFACT;
import static io.undertow.jakartaee9.TransformConstants.SERVLET_SPEC_JAKARTAEE9_GROUP;
import static io.undertow.jakartaee9.TransformConstants.SERVLET_SPEC_VERSION;
import static io.undertow.jakartaee9.TransformConstants.WEBSOCKETS_SPEC_JAKARTAEE8_ARTIFACT;
import static io.undertow.jakartaee9.TransformConstants.WEBSOCKETS_SPEC_JAKARTAEE8_GROUP;
import static io.undertow.jakartaee9.TransformConstants.WEBSOCKETS_SPEC_JAKARTAEE9_ARTIFACT;
import static io.undertow.jakartaee9.TransformConstants.WEBSOCKETS_SPEC_JAKARTAEE9_GROUP;
import static io.undertow.jakartaee9.TransformConstants.WEBSOCKETS_SPEC_VERSION;
import static io.undertow.jakartaee9.UndertowJakartaEE9Logger.LOGGER;

/**
 * Transforms pom files, moving artifact dependencies from Jakarta EE8 to
 * Jakarta EE9.
 *
 * @author Flavia Rainone
 */
class PomTransformer {
    private static final String VERSION_LINE = "\\s*<version>.*</version>\\s*";

    private static final String OPEN_ARTIFACT = "<artifactId>";
    private static final String CLOSE_ARTIFACT = "</artifactId>";
    private static final String SERVLET_SPEC_JAKARTAEE8_ARTIFACT_LINE = OPEN_ARTIFACT + SERVLET_SPEC_JAKARTAEE8_ARTIFACT + CLOSE_ARTIFACT;
    private static final String SERVLET_SPEC_JAKARTAEE9_ARTIFACT_LINE = OPEN_ARTIFACT + SERVLET_SPEC_JAKARTAEE9_ARTIFACT + CLOSE_ARTIFACT;
    private static final String WEBSOCKETS_SPEC_JAKARTAEE8_ARTIFACT_LINE = OPEN_ARTIFACT + WEBSOCKETS_SPEC_JAKARTAEE8_ARTIFACT + CLOSE_ARTIFACT;
    private static final String WEBSOCKETS_SPEC_JAKARTAEE9_ARTIFACT_LINE = OPEN_ARTIFACT + WEBSOCKETS_SPEC_JAKARTAEE9_ARTIFACT + CLOSE_ARTIFACT;

    private static final String OPEN_GROUP = "<groupId>";
    private static final String CLOSE_GROUP = "</groupId>";
    private static final String SERVLET_SPEC_JAKARTAEE8_GROUP_LINE = OPEN_GROUP + SERVLET_SPEC_JAKARTAEE8_GROUP + CLOSE_GROUP;
    private static final String SERVLET_SPEC_JAKARTAEE9_GROUP_LINE = OPEN_GROUP + SERVLET_SPEC_JAKARTAEE9_GROUP + CLOSE_GROUP;
    private static final String WEBSOCKETS_SPEC_JAKARTAEE8_GROUP_LINE = OPEN_GROUP + WEBSOCKETS_SPEC_JAKARTAEE8_GROUP + CLOSE_GROUP;
    private static final String WEBSOCKETS_SPEC_JAKARTAEE9_GROUP_LINE = OPEN_GROUP + WEBSOCKETS_SPEC_JAKARTAEE9_GROUP + CLOSE_GROUP;

    private static final String OPEN_VERSION = "<version>";
    private static final String CLOSE_VERSION = "</version>";
    private static final String SERVLET_SPEC_VERSION_LINE = OPEN_VERSION + SERVLET_SPEC_VERSION + CLOSE_VERSION;
    private static final String WEBSOCKETS_SPEC_VERSION_LINE = OPEN_VERSION + WEBSOCKETS_SPEC_VERSION + CLOSE_VERSION;

    private final File inputDir;
    private final File outputDir;

    PomTransformer(File inputDir, File outputDir) {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
    }

    void transformPoms() throws IOException {
        Map<String, String> artifactMapping = new HashMap();
        Stream<Path> paths = Files.walk(inputDir.toPath());
        paths.forEach((Path p) -> {
            final String fileName = p.getFileName().toString();
            if (fileName.endsWith(POM_EXTENSION))
                artifactMapping.put(getArtifactName(fileName, POM_TYPE), transformArtifactName(fileName,
                        POM_TYPE));
        });
        for (File file : inputDir.listFiles()) {
            if (file.getName().endsWith(POM_EXTENSION))
                transformPom(file, artifactMapping);
        }
    }

    private void transformPom(final File pomFile, Map<String, String> artifactMapping) throws IOException {
        // input and output file names
        final String pomFileName = pomFile.getName();
        final String transformedPomFileName = transformArtifactFileName(pomFileName,
                POM_TYPE);
        final File transformedPomFile = new File(outputDir, transformedPomFileName);
        transformedPomFile.createNewFile();
        LOGGER.transformingFile(pomFileName, transformedPomFileName);

        // process lines
        final Stream<String> pomLines = Files.lines(pomFile.toPath());
        final Stream<String> transformedPomLines = pomLines.map(line -> transformPomLine(line, artifactMapping));
        final FileWriter fileWriter = new FileWriter(transformedPomFile);
        transformedPomLines.forEachOrdered(line -> {
            if (line == null)
                return;
            try {
                fileWriter.write(line);
                fileWriter.write('\n');
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
        fileWriter.close();
    }

    private boolean skipVersion;

    private String transformPomLine(String line, Map<String, String> artifactMapping) {
        for (Map.Entry<String, String> mappedArtifact: artifactMapping.entrySet()) {
            if (line.matches(".*" + mappedArtifact.getKey() + ".*")) {
                return line.replaceAll(mappedArtifact.getKey(), mappedArtifact.getValue());
            }
        }
        if (skipVersion) {
            skipVersion = false;
            if (line.matches(VERSION_LINE))
                return null;
        }
        if (line.contains(SERVLET_SPEC_JAKARTAEE8_GROUP_LINE)) {
            return getTabSpaces(line) + SERVLET_SPEC_JAKARTAEE9_GROUP_LINE;
        }
        if (line.contains (WEBSOCKETS_SPEC_JAKARTAEE8_GROUP_LINE)) {
            return getTabSpaces(line) + WEBSOCKETS_SPEC_JAKARTAEE9_GROUP_LINE;
        }
        if (line.contains(SERVLET_SPEC_JAKARTAEE8_ARTIFACT_LINE)) {
            final String spaces = getTabSpaces(line);
            skipVersion = true;
            return spaces + SERVLET_SPEC_JAKARTAEE9_ARTIFACT_LINE + "\n"
                    + spaces + SERVLET_SPEC_VERSION_LINE;
        }
        if (line.contains(WEBSOCKETS_SPEC_JAKARTAEE8_ARTIFACT_LINE)) {
            final String spaces = getTabSpaces(line);
            skipVersion = true;
            return spaces + WEBSOCKETS_SPEC_JAKARTAEE9_ARTIFACT_LINE + "\n"
                    + spaces + WEBSOCKETS_SPEC_VERSION_LINE;
        }
        return line;
    }

    private String getTabSpaces(String inputLine) {
        return inputLine.substring(0, inputLine.indexOf('<'));
    }
}