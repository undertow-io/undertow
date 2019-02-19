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
package io.undertow.servlet.test.security.custom;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.handlers.form.FormEncodedDataDefinition;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.FormParserFactory.ParserDefinition;
import io.undertow.servlet.handlers.security.ServletFormAuthenticationMechanism;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * <p>
 * Custom Authentication Mechanism that will share the encoding set by DeploymentManagementImpl
 */
public class CustomEncodingAuthenticationMechanism extends ServletFormAuthenticationMechanism {

    public static final Factory FACTORY = new Factory();
    public String charset = null;

    public CustomEncodingAuthenticationMechanism(FormParserFactory formParserFactory, String name, String loginPage, String errorPage) {
        super(formParserFactory, name, loginPage, errorPage);
        this.charset = getcharset(formParserFactory);
    }

    public String getcharset(FormParserFactory formParserFactory) {
        ParserDefinition parserDefinition = null;
        ParserDefinition[] parserDefinitions = (ParserDefinition[]) getPrivateField(formParserFactory, "parserDefinitions");
        if(parserDefinitions != null) {
            parserDefinition = parserDefinitions[0];
            if(parserDefinition instanceof FormEncodedDataDefinition) {
                FormEncodedDataDefinition formEncodedDataDefinition = (FormEncodedDataDefinition) parserDefinition;
                return (String) getPrivateField(formEncodedDataDefinition, "defaultEncoding");
            }
        }
        return null;
    }

    private Object getPrivateField(Object object, String fieldName) {
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            if (Modifier.isPrivate(field.getModifiers())) {
                field.setAccessible(true);
            }
            return field.get(object);
        } catch (Exception e) {
            return null;
        }
    }

    public static final class Factory implements AuthenticationMechanismFactory {

        @Override
        public AuthenticationMechanism create(String mechanismName, IdentityManager identityManager, FormParserFactory formParserFactory, Map<String, String> properties) {
            return new CustomEncodingAuthenticationMechanism(formParserFactory, mechanismName, properties.get(LOGIN_PAGE), properties.get(ERROR_PAGE));
        }
    }
}
