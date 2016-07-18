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

package io.undertow.annotationprocessor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

/**
 * @author Stuart Douglas
 */
@SupportedAnnotationTypes("io.undertow.annotationprocessor.HttpParserConfig")
@SupportedOptions({
})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class HttpParserAnnotationProcessor extends AbstractProcessor {

    private Filer filer;

    @Override
    public void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(HttpParserConfig.class)) {
            final HttpParserConfig parser = element.getAnnotation(HttpParserConfig.class);
            if (parser == null) {
                continue;
            }

            final RequestParserGenerator requestGenerator = new RequestParserGenerator(((TypeElement) element).getQualifiedName().toString());
            final byte[] newClass = requestGenerator.createTokenizer(parser.methods(), parser.protocols(), parser.headers());
            try {
                JavaFileObject file = filer.createClassFile(((TypeElement) element).getQualifiedName() + AbstractParserGenerator.CLASS_NAME_SUFFIX, element);
                final OutputStream out = file.openOutputStream();
                try {
                    out.write(newClass);
                } finally {
                    try {
                        out.close();
                    } catch (IOException e) {

                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(HttpResponseParserConfig.class)) {
            ResponseParserGenerator responseGenerator = new ResponseParserGenerator(((TypeElement) element).getQualifiedName().toString());
            final HttpResponseParserConfig parser = element.getAnnotation(HttpResponseParserConfig.class);
            if (parser == null) {
                continue;
            }
            final byte[] newClass = responseGenerator.createTokenizer(new String[0], parser.protocols(), parser.headers());
            try {
                JavaFileObject file = filer.createClassFile(((TypeElement) element).getQualifiedName() + AbstractParserGenerator.CLASS_NAME_SUFFIX, element);
                final OutputStream out = file.openOutputStream();
                try {
                    out.write(newClass);
                } finally {
                    try {
                        out.close();
                    } catch (IOException e) {

                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return true;
    }

}
