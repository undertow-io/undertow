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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.proxy.mod_cluster;

import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.TYPEMEM;
import static io.undertow.server.handlers.proxy.mod_cluster.MCMPConstants.TYPESYNTAX;

/**
 * @author Emanuel Muckenhuber
 */
enum MCMPErrorCode {

    CANT_READ_NODE(TYPEMEM, "MEM: Can't read node"),
    CANT_UPDATE_NODE(TYPEMEM, "MEM: Can't update or insert node"),
    CANT_UPDATE_CONTEXT(TYPEMEM, "MEM: Can't update or insert context"),
    NODE_STILL_EXISTS(TYPESYNTAX, "MEM: Old node still exist"),
    ;

    private final String type;
    private final String message;
    MCMPErrorCode(String type, String message) {
        this.type = type;
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    /** the syntax error messages
     String SMESPAR = "SYNTAX: Can't parse message";
     String SBALBIG = "SYNTAX: Balancer field too big";
     String SBAFBIG = "SYNTAX: A field is too big";
     String SROUBIG = "SYNTAX: JVMRoute field too big";
     String SROUBAD = "SYNTAX: JVMRoute can't be empty";
     String SDOMBIG = "SYNTAX: LBGroup field too big";
     String SHOSBIG = "SYNTAX: Host field too big";
     String SPORBIG = "SYNTAX: Port field too big";
     String STYPBIG = "SYNTAX: Type field too big";
     String SALIBAD = "SYNTAX: Alias without Context";
     String SCONBAD = "SYNTAX: Context without Alias";
     String SBADFLD = "SYNTAX: Invalid field ";
     String SBADFLD1 = " in message";
     String SMISFLD = "SYNTAX: Mandatory field(s) missing in message";
     String SCMDUNS = "SYNTAX: Command is not supported";
     String SMULALB = "SYNTAX: Only one Alias in APP command";
     String SMULCTB = "SYNTAX: Only one Context in APP command";
     String SREADER = "SYNTAX: %s can't read POST data";

     String MBALAUI = "MEM: Can't update or insert balancer";
     String MHOSTRD = "MEM: Can't read host alias";
     String MHOSTUI = "MEM: Can't update or insert host alias";
     */

}
