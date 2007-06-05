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
package org.osaf.cosmo.atom.generator;

import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.osaf.cosmo.model.User;

/**
 * An interface for classes that generate the user preferences feed.
 *
 * @see Entry
 * @see Feed
 * @see User
 */
public interface PreferencesFeedGenerator {

    /**
     * Generates an Atom feed containing entries for a user's
     * preferences.
     *
     * @param user the user
     * @throws GeneratorException
     */
    public Feed generateFeed(User user)
        throws GeneratorException;

    /**
     * Generates an Atom entry representing a specific user
     * preference.
     *
     * @param user the user
     * @param key the preference key
     * @throws GeneratorException
     */
    public Entry generateEntry(User user,
                               String key)
        throws GeneratorException;
}
