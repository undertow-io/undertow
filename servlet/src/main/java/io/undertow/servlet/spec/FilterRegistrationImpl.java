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

package io.undertow.servlet.spec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;

import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.FilterMappingInfo;

/**
 * @author Stuart Douglas
 */
public class FilterRegistrationImpl implements FilterRegistration, FilterRegistration.Dynamic {

    private final FilterInfo filterInfo;
    private final Deployment deployment;
    private final ServletContextImpl servletContext;

    public FilterRegistrationImpl(final FilterInfo filterInfo, final Deployment deployment, ServletContextImpl servletContext) {
        this.filterInfo = filterInfo;
        this.deployment = deployment;
        this.servletContext = servletContext;
    }

    @Override
    public void addMappingForServletNames(final EnumSet<DispatcherType> dispatcherTypes, final boolean isMatchAfter, final String... servletNames) {
        servletContext.addMappingForServletNames(filterInfo, dispatcherTypes, isMatchAfter, servletNames);
    }

    @Override
    public Collection<String> getServletNameMappings() {
        DeploymentInfo deploymentInfo = deployment.getDeploymentInfo();
        final List<String> ret = new ArrayList<>();
        for(final FilterMappingInfo mapping : deploymentInfo.getFilterMappings()) {
            if(mapping.getMappingType() == FilterMappingInfo.MappingType.SERVLET) {
                if(mapping.getFilterName().equals(filterInfo.getName())) {
                    ret.add(mapping.getMapping());
                }
            }
        }
        return ret;
    }

    @Override
    public void addMappingForUrlPatterns(final EnumSet<DispatcherType> dispatcherTypes, final boolean isMatchAfter, final String... urlPatterns) {
        servletContext.addMappingForUrlPatterns(filterInfo, dispatcherTypes, isMatchAfter, urlPatterns);
    }

    @Override
    public Collection<String> getUrlPatternMappings() {
        DeploymentInfo deploymentInfo = deployment.getDeploymentInfo();
        final List<String> ret = new ArrayList<>();
        for(final FilterMappingInfo mapping : deploymentInfo.getFilterMappings()) {
            if(mapping.getMappingType() == FilterMappingInfo.MappingType.URL) {
                if(mapping.getFilterName().equals(filterInfo.getName())) {
                    ret.add(mapping.getMapping());
                }
            }
        }
        return ret;
    }

    @Override
    public String getName() {
        return filterInfo.getName();
    }

    @Override
    public String getClassName() {
        return filterInfo.getFilterClass().getName();
    }

    @Override
    public boolean setInitParameter(final String name, final String value) {
        if(filterInfo.getInitParams().containsKey(name)) {
            return false;
        }
        filterInfo.addInitParam(name, value);
        return true;
    }

    @Override
    public String getInitParameter(final String name) {
        return filterInfo.getInitParams().get(name);
    }

    @Override
    public Set<String> setInitParameters(final Map<String, String> initParameters) {
        final Set<String> ret = new HashSet<>();
        for(Map.Entry<String, String> entry : initParameters.entrySet()) {
            if(!setInitParameter(entry.getKey(), entry.getValue())) {
                ret.add(entry.getKey());
            }
        }
        return ret;
    }

    @Override
    public Map<String, String> getInitParameters() {
        return filterInfo.getInitParams();
    }

    @Override
    public void setAsyncSupported(final boolean isAsyncSupported) {
        filterInfo.setAsyncSupported(isAsyncSupported);
    }
}
