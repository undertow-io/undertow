/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;

/**
 *
 * NOTE: If you add a new method here you must also add it to {@link io.undertow.server.HttpParser}
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Methods {

    private Methods() {
    }

    public static final String OPTIONS_STRING = "OPTIONS";
    public static final String GET_STRING = "GET";
    public static final String HEAD_STRING = "HEAD";
    public static final String POST_STRING = "POST";
    public static final String PUT_STRING = "PUT";
    public static final String DELETE_STRING = "DELETE";
    public static final String TRACE_STRING = "TRACE";
    public static final String CONNECT_STRING = "CONNECT";
    public static final String PROPFIND_STRING = "PROPFIND";
    public static final String PROPPATCH_STRING = "PROPPATCH";
    public static final String MKCOL_STRING = "MKCOL";
    public static final String COPY_STRING = "COPY";
    public static final String MOVE_STRING = "MOVE";
    public static final String LOCK_STRING = "LOCK";
    public static final String UNLOCK_STRING = "UNLOCK";
    public static final String ACL_STRING = "ACL";
    public static final String REPORT_STRING = "REPORT";
    public static final String VERSION_CONTROL_STRING = "VERSION-CONTROL";
    public static final String CHECKIN_STRING = "CHECKIN";
    public static final String CHECKOUT_STRING = "CHECKOUT";
    public static final String UNCHECKOUT_STRING = "UNCHECKOUT";
    public static final String SEARCH_STRING = "SEARCH";
    public static final String MKWORKSPACE_STRING = "MKWORKSPACE";
    public static final String UPDATE_STRING = "UPDATE";
    public static final String LABEL_STRING = "LABEL";
    public static final String MERGE_STRING = "MERGE";
    public static final String BASELINE_CONTROL_STRING = "BASELINE_CONTROL";
    public static final String MKACTIVITY_STRING = "MKACTIVITY";


    public static final HttpString OPTIONS = new HttpString(OPTIONS_STRING);
    public static final HttpString GET = new HttpString(GET_STRING);
    public static final HttpString HEAD = new HttpString(HEAD_STRING);
    public static final HttpString POST = new HttpString(POST_STRING);
    public static final HttpString PUT = new HttpString(PUT_STRING);
    public static final HttpString DELETE = new HttpString(DELETE_STRING);
    public static final HttpString TRACE = new HttpString(TRACE_STRING);
    public static final HttpString CONNECT = new HttpString(CONNECT_STRING);
    public static final HttpString PROPFIND =new HttpString(PROPFIND_STRING);
    public static final HttpString PROPPATCH =new HttpString(PROPPATCH_STRING);
    public static final HttpString MKCOL =new HttpString(MKCOL_STRING);
    public static final HttpString COPY =new HttpString(COPY_STRING);
    public static final HttpString MOVE =new HttpString(MOVE_STRING);
    public static final HttpString LOCK =new HttpString(LOCK_STRING);
    public static final HttpString UNLOCK =new HttpString(UNLOCK_STRING);
    public static final HttpString ACL =new HttpString(ACL_STRING);
    public static final HttpString REPORT =new HttpString(REPORT_STRING);
    public static final HttpString VERSION_CONTROL =new HttpString(VERSION_CONTROL_STRING);
    public static final HttpString CHECKIN =new HttpString(CHECKIN_STRING);
    public static final HttpString CHECKOUT =new HttpString(CHECKOUT_STRING);
    public static final HttpString UNCHECKOUT =new HttpString(UNCHECKOUT_STRING);
    public static final HttpString SEARCH =new HttpString(SEARCH_STRING);
    public static final HttpString MKWORKSPACE =new HttpString(MKWORKSPACE_STRING);
    public static final HttpString UPDATE =new HttpString(UPDATE_STRING);
    public static final HttpString LABEL =new HttpString(LABEL_STRING);
    public static final HttpString MERGE =new HttpString(MERGE_STRING);
    public static final HttpString BASELINE_CONTROL =new HttpString(BASELINE_CONTROL_STRING);
    public static final HttpString MKACTIVITY =new HttpString(MKACTIVITY_STRING);


}
