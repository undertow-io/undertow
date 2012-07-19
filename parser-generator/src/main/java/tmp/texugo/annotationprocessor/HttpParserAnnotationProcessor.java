/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package tmp.texugo.annotationprocessor;

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
@SupportedAnnotationTypes("tmp.texugo.annotationprocessor.HttpParserConfig")
@SupportedOptions({
})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class HttpParserAnnotationProcessor extends AbstractProcessor {

    private Filer filer;

    @Override
    public void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer =  processingEnv.getFiler();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

        for (Element element : roundEnv.getElementsAnnotatedWith(HttpParserConfig.class)) {
            final HttpParserConfig parser = element.getAnnotation(HttpParserConfig.class);
            final byte[] newClass = ParserGenerator.createTokenizer(((TypeElement)element).getQualifiedName().toString(), parser.verbs(), parser.versions(), parser.headers());
            try {
                JavaFileObject file = filer.createClassFile(((TypeElement) element).getQualifiedName() + ParserGenerator.CLASS_NAME_SUFFIX, element);
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
