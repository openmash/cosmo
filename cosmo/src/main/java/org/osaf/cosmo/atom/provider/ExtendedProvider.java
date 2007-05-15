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


import org.apache.abdera.protocol.server.provider.Provider;
import org.apache.abdera.protocol.server.provider.RequestContext;
import org.apache.abdera.protocol.server.provider.ResponseContext;

public interface ExtendedProvider extends Provider {

    /**
     * <p>
     * Updates the targeted collection.
     * </p>
     * <p>
     * If the <code>name</code> request parameter is provided, uses
     * its value to set the collection's name and display name.
     * </p>
     * <p>
     * Returns <code>204 No Content</code> on success.
     * </p>
     */
    public ResponseContext updateCollection(RequestContext request);
}