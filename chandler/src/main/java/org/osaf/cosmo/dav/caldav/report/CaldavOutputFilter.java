/*
 * Copyright 2006 Open Source Applications Foundation
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
package org.osaf.cosmo.dav.caldav.report;

import java.text.ParseException;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.ElementIterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.osaf.cosmo.calendar.data.OutputFilter;
import org.osaf.cosmo.api.ICalendarConstants;
import org.osaf.cosmo.dav.BadRequestException;
import org.osaf.cosmo.dav.DavException;
import org.osaf.cosmo.api.CaldavConstants;
import org.osaf.cosmo.dav.caldav.UnsupportedCalendarDataException;

import org.w3c.dom.Element;

/**
 * A utility for parsing an {@link OutputFilter} from XML.
 */
public class CaldavOutputFilter {
    private static final Log log = LogFactory.getLog(CaldavOutputFilter.class);

    /**
     * Returns an <code>OutputFilter</code> representing the given
     * <code>&lt;C:calendar-data/&gt;> element.
     */
    public static OutputFilter createFromXml(Element cdata)
        throws DavException {
        OutputFilter result = null;
        Period expand = null;
        Period limit = null;
        Period limitfb = null;

        String contentType = DomUtil.getAttribute(cdata, 
            CaldavConstants.ATTR_CALDAV_CONTENT_TYPE,
           	CaldavConstants.NAMESPACE_CALDAV);
        if (contentType != null && ! contentType.equals(ICalendarConstants.MEDIA_TYPE_ICAL))
            throw new UnsupportedCalendarDataException(contentType);
        String version = DomUtil.getAttribute(cdata, 
           	CaldavConstants.ATTR_CALDAV_CONTENT_TYPE,
           	CaldavConstants.NAMESPACE_CALDAV);
        if (version != null && ! version.equals(ICalendarConstants.ICALENDAR_VERSION))
            throw new UnsupportedCalendarDataException();

        // Look at each child element of calendar-data
        for (ElementIterator iter = DomUtil.getChildren(cdata); iter.hasNext();) {

            Element child = iter.nextElement();
            if (CaldavConstants.ELEMENT_CALDAV_COMP.equals(child.getLocalName())) {

                // At the top-level of calendar-data there should only be one
                // <comp> element as VCALENDAR components are the only top-level
                // components allowed in iCalendar data
                if (result != null)
                    return null;

                // Get required name attribute and verify it is VCALENDAR
                String name = 
                    DomUtil.getAttribute(child, CaldavConstants.ATTR_CALDAV_NAME, null);
                if ((name == null) || !Calendar.VCALENDAR.equals(name))
                    return null;

                // Now parse filter item
                result = parseCalendarDataComp(child);

            } else if (CaldavConstants.ELEMENT_CALDAV_EXPAND.equals(child.getLocalName())) {
                expand = parsePeriod(child, true);
            } else if (CaldavConstants.ELEMENT_CALDAV_LIMIT_RECURRENCE_SET.
                       equals(child.getLocalName())) {
                limit = parsePeriod(child, true);
            } else if (CaldavConstants.ELEMENT_CALDAV_LIMIT_FREEBUSY_SET.
                       equals(child.getLocalName())) {
                limitfb = parsePeriod(child, true);
            } else {
                log.warn("Ignoring child " + child.getTagName() + " of " + cdata.getTagName());
            }
        }

        // Now add any limit/expand options, creating a filter if one is not
        // already present
        if ((result == null)
                && ((expand != null) || (limit != null) || (limitfb != null))) {
            result = new OutputFilter("VCALENDAR");
            result.setAllSubComponents();
            result.setAllProperties();
        }
        if (expand != null) {
            result.setExpand(expand);
        }
        if (limit != null) {
            result.setLimit(limit);
        }
        if (limitfb != null) {
            result.setLimitfb(limitfb);
        }

        return result;
    }

    // private methods

    private static OutputFilter parseCalendarDataComp(Element comp) {
        // Get required name attribute
        String name =
            DomUtil.getAttribute(comp, CaldavConstants.ATTR_CALDAV_NAME, null);
        if (name == null)
            return null;

        // Now create filter item
        OutputFilter result = new OutputFilter(name);

        // Look at each child element
        ElementIterator i = DomUtil.getChildren(comp);
        while (i.hasNext()) {
            Element child = i.nextElement();
            if (CaldavConstants.ELEMENT_CALDAV_ALLCOMP.equals(child.getLocalName())) {
                // Validity check
                if (result.hasSubComponentFilters()) {
                    result = null;
                    return null;
                }
                result.setAllSubComponents();

            } else if (CaldavConstants.ELEMENT_CALDAV_ALLPROP.equals(child.getLocalName())) {
                // Validity check
                if (result.hasPropertyFilters()) {
                    result = null;
                    return null;
                }
                result.setAllProperties();

            } else if (CaldavConstants.ELEMENT_CALDAV_COMP.equals(child.getLocalName())) {
                // Validity check
                if (result.isAllSubComponents()) {
                    result = null;
                    return null;
                }
                OutputFilter subfilter = parseCalendarDataComp(child);
                if (subfilter == null) {
                    result = null;
                    return null;
                } else
                    result.addSubComponent(subfilter);
            } else if (CaldavConstants.ELEMENT_CALDAV_PROP.equals(child.getLocalName())) {
                // Validity check
                if (result.isAllProperties()) {
                    result = null;
                    return null;
                }

                // Get required name attribute
                String propname =
                    DomUtil.getAttribute(child, CaldavConstants.ATTR_CALDAV_NAME, null);
                if (propname == null) {
                    result = null;
                    return null;
                }

                // Get optional novalue attribute
                boolean novalue = false;
                String novaluetxt =
                    DomUtil.getAttribute(child, CaldavConstants.ATTR_CALDAV_NOVALUE, null);
                if (novaluetxt != null) {
                    if (DavConstants.VALUE_YES.equals(novaluetxt))
                        novalue = true;
                    else if (DavConstants.VALUE_NO.equals(novaluetxt))
                        novalue = false;
                    else {
                        result = null;
                        return null;
                    }
                }

                // Now add property item
                result.addProperty(propname, novalue);
            }
        }

        return result;
    }

    private static Period parsePeriod(Element node, boolean utc)
        throws DavException {
        DateTime trstart = null;
        DateTime trend = null;
        String start =
            DomUtil.getAttribute(node, CaldavConstants.ATTR_CALDAV_START, null);
        if (start == null)
            throw new BadRequestException("Expected timerange attribute " + CaldavConstants.ATTR_CALDAV_START);
        try {
            trstart = new DateTime(start);
        } catch (ParseException e) {
            throw new BadRequestException("Timerange start not parseable: " + e.getMessage());
        }
        if (utc && !trstart.isUtc())
            throw new BadRequestException("Timerange start must be UTC");

        String end =
            DomUtil.getAttribute(node, CaldavConstants.ATTR_CALDAV_END, null);
        if (end == null)
            throw new BadRequestException("Expected timerange attribute " + CaldavConstants.ATTR_CALDAV_END);
        try {
            trend = new DateTime(end);
        } catch (ParseException e) {
            throw new BadRequestException("Timerange end not parseable: " + e.getMessage());
        }
        if (utc && !trend.isUtc())
            throw new BadRequestException("Timerange end must be UTC");

        return new Period(trstart, trend);
    }
}
