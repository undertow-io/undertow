/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.undertow.proxy.mcmp;

import io.undertow.util.HttpString;


/**
 * Constants.
 *
 * @author Jean-Frederic Clere
 */
public final class Constants {


    public static final HttpString CONFIG = new HttpString("CONFIG");

    public static final HttpString ENABLE_APP = new HttpString("ENABLE-APP");

    public static final HttpString DISABLE_APP = new HttpString("DISABLE-APP");

    public static final HttpString STOP_APP = new HttpString("STOP-APP");

    public static final HttpString REMOVE_APP = new HttpString("REMOVE-APP");

    public static final HttpString STATUS = new HttpString("STATUS");

    public static final HttpString DUMP = new HttpString("DUMP");

    public static final HttpString INFO = new HttpString("INFO");

    public static final HttpString PING = new HttpString("PING");

    public static final HttpString GET = new HttpString("GET");

}
