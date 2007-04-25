/*
 * Copyright 2007 Open Source Applications Foundation
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
package org.osaf.cosmo.atom.provider;

import org.apache.abdera.protocol.server.provider.RequestContext;
import org.apache.abdera.protocol.server.provider.TargetType;

import org.osaf.cosmo.model.CollectionItem;

public class CollectionTarget extends BaseItemTarget {

    private CollectionItem collection;

    public CollectionTarget(RequestContext request,
                            CollectionItem collection) {
        this(request, collection, null, null);
    }

    public CollectionTarget(RequestContext request,
                            CollectionItem collection,
                            String projection,
                            String format) {
        super(TargetType.TYPE_COLLECTION, request, projection, format);
        this.collection = collection;
    }

    public CollectionItem getCollection() {
        return collection;
    }
}
