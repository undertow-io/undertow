/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package io.undertow.conduits;

import java.util.zip.Deflater;

/**
 * @author ckozak
 */
public class NewInstanceDeflaterPool implements DeflaterPool {

    private final int level;
    private final boolean nowrap;

    public NewInstanceDeflaterPool(int level, boolean nowrap) {
        this.level = level;
        this.nowrap = nowrap;
    }

    @Override
    public Deflater getDeflater() {
        return new Deflater(level, nowrap);
    }

    @Override
    public void returnDeflater(Deflater deflater) {
        deflater.end();
    }
}
