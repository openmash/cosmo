/*
 * Copyright 2005-2007 Open Source Applications Foundation
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
package org.osaf.cosmo.dav.caldav.property;

import org.apache.jackrabbit.webdav.xml.DomUtil;

import org.osaf.cosmo.api.ICalendarConstants;
import org.osaf.cosmo.api.CaldavConstants;
import org.osaf.cosmo.dav.property.StandardDavProperty;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Represents the <code>CALDAV:calendar-data</code> property as used
 * to transmit a set of icalendar objects in the body of a report
 * response.
 */
public class CalendarData extends StandardDavProperty {

    public CalendarData(String calendarData) {
        super(CaldavConstants.CALENDARDATA, calendarData, true);
    }

    public Element toXml(Document document) {
        Element e = super.toXml(document);

        DomUtil.setAttribute(e, CaldavConstants.ATTR_CALDAV_CONTENT_TYPE,
    		CaldavConstants.NAMESPACE_CALDAV, ICalendarConstants.MEDIA_TYPE_ICAL);
        DomUtil.setAttribute(e, CaldavConstants.ATTR_CALDAV_VERSION,
    		CaldavConstants.NAMESPACE_CALDAV, ICalendarConstants.ICALENDAR_VERSION);

        return e;
    }
}
