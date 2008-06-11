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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.osaf.cosmo.atom.AtomConstants;
import org.osaf.cosmo.model.Preference;
import org.osaf.cosmo.model.text.XhtmlPreferenceFormat;

/**
 * Base class for for {@link PreferencesProvider} tests.
 */
public abstract class BasePreferencesCollectionAdapterTestCase
    extends BaseCollectionAdapterTestCase implements AtomConstants {
    private static final Log log =
        LogFactory.getLog(BasePreferencesCollectionAdapterTestCase.class);

    protected BaseCollectionAdapter createAdapter() {
        PreferencesCollectionAdapter adapter = new PreferencesCollectionAdapter();
        adapter.setUserService(helper.getUserService());
        return adapter;
    }

    protected String serialize(Preference pref) {
        XhtmlPreferenceFormat formatter = new XhtmlPreferenceFormat();
        return formatter.format(pref);
    }
}