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
package org.osaf.cosmo.dav.caldav.report;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.osaf.cosmo.dav.ForbiddenException;
import org.osaf.cosmo.api.CaldavConstants;

/**
 * An exception indicating that the data in a report query filter is not
 * correctly constructed.
 */
public class InvalidFilterException extends ForbiddenException {
    
    public InvalidFilterException(Exception e) {
        super(e.getMessage());
        getNamespaceContext().addNamespace(CaldavConstants.PRE_CALDAV, CaldavConstants.NS_CALDAV);
    }

    protected void writeContent(XMLStreamWriter writer)
        throws XMLStreamException {
        writer.writeStartElement(CaldavConstants.NS_CALDAV, "valid-filter");
        writer.writeCharacters(getMessage());
        writer.writeEndElement();
    }
}
