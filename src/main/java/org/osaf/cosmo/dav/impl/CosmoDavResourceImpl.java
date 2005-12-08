/*
 * Copyright 2005 Open Source Applications Foundation
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
package org.osaf.cosmo.dav.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.jcr.Item;
import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Version;

import org.apache.jackrabbit.server.io.ImportContext;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavLocatorFactory;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavSession;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.jcr.JcrDavException;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.simple.DavResourceImpl;
import org.apache.jackrabbit.webdav.simple.ResourceConfig;

import org.apache.log4j.Logger;

import org.osaf.cosmo.CosmoConstants;
import org.osaf.cosmo.io.CosmoImportContext;
import org.osaf.cosmo.jcr.CosmoJcrConstants;
import org.osaf.cosmo.jcr.JCRUtils;
import org.osaf.cosmo.dao.TicketDao;
import org.osaf.cosmo.dav.CosmoDavConstants;
import org.osaf.cosmo.dav.CosmoDavResource;
import org.osaf.cosmo.dav.CosmoDavResourceFactory;
import org.osaf.cosmo.dav.CosmoDavResponse;
import org.osaf.cosmo.dav.property.CalendarComponentRestrictionSet;
import org.osaf.cosmo.dav.property.CalendarDescription;
import org.osaf.cosmo.dav.property.CalendarRestrictions;
import org.osaf.cosmo.dav.property.CosmoDavPropertyName;
import org.osaf.cosmo.dav.property.CosmoResourceType;
import org.osaf.cosmo.dav.property.TicketDiscovery;
import org.osaf.cosmo.icalendar.CosmoICalendarConstants;
import org.osaf.cosmo.model.Ticket;
import org.osaf.cosmo.model.User;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * A subclass of
 * {@link org.apache.jackrabbit.server.simple.dav.DavResourceImpl}
 * that provides Cosmo-specific WebDAV behaviors.
 */
public class CosmoDavResourceImpl extends DavResourceImpl 
    implements CosmoDavResource, ApplicationContextAware {
    private static final Logger log = Logger.getLogger(CosmoDavResource.class);
    private static final String BEAN_TICKET_DAO = "ticketDao";

    private String baseUrl;
    private ApplicationContext applicationContext;
    private boolean initializing;
    private Map tickets;
    private Map ownedTickets;
    private boolean isCalendarHomeCollection;
    private boolean isCalendarCollection;

    /**
     */
    public CosmoDavResourceImpl(DavResourceLocator locator,
                                CosmoDavResourceFactory factory,
                                DavSession session,
                                ResourceConfig config)
        throws RepositoryException, DavException {
        super(locator, factory, session, config);
        initializing = false;
        isCalendarHomeCollection = exists() &&
            getNode().isNodeType(CosmoJcrConstants.NT_CALDAV_HOME);
        isCalendarCollection = exists() &&
            getNode().isNodeType(CosmoJcrConstants.NT_CALDAV_COLLECTION);
    }

    // DavResource methods

    /**
     */
    public String getComplianceClass() {
        return CosmoDavResource.COMPLIANCE_CLASS;
    }

    /**
     */
    public String getSupportedMethods() {
        // can only make a calendar collection inside a regular
        // collection (NEVER inside another calendar collection)
        if (exists () && isCollection() && ! isCalendarCollection()) {
            return CosmoDavResource.METHODS + ", MKCALENDAR";
        }
        return CosmoDavResource.METHODS;
    }

    /**
     */
    public DavResource getCollection() {
        CosmoDavResourceImpl c = (CosmoDavResourceImpl) super.getCollection();
        c.setBaseUrl(baseUrl);
        c.setApplicationContext(applicationContext);
        return c;
    }

    // CosmoDavResource methods

    /**
     */
    public boolean isTicketable() {
        try {
            return exists() &&
                getNode().isNodeType(CosmoJcrConstants.NT_TICKETABLE);
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns true if this resource represents a calendar home
     * collection.
     */
    public boolean isCalendarHomeCollection() {
        return isCalendarHomeCollection;
    }

    /**
     * Returns true if this resource represents a calendar
     * collection.
     */
    public boolean isCalendarCollection() {
        return isCalendarCollection;
    }

    /**
     */
    public void setIsCalendarCollection(boolean isCalendarCollection) {
        this.isCalendarCollection = isCalendarCollection;
    }

    /**
     * For calendar collection resources, returns a
     * <code>Calendar</code> representing the calendar objects
     * contained within the collection.
     */
    public Calendar getCollectionCalendar()
        throws DavException {
        if (! isCalendarCollection()) {
            return null;
        }

        Calendar calendar = new Calendar();
        calendar.getProperties().add(new ProdId(CosmoConstants.PRODUCT_ID));
        calendar.getProperties().add(Version.VERSION_2_0);
        calendar.getProperties().add(CalScale.GREGORIAN);

        // extract the events and timezones for each child event and
        // add them to the collection calendar object
        // XXX: cache the built calendar as a property of the resource
        // node
        // index the timezones by tzid so that we only include each tz
        // once. if for some reason different event resources have
        // different tz definitions for a tzid, *shrug* last one wins
        // for this same reason, we use a single calendar builder/time
        // zone registry
        HashMap tzIdx = new HashMap();
        Node childNode = null;
        Node contentNode = null;
        InputStream data = null;
        CalendarBuilder builder = new CalendarBuilder();
        Calendar childCalendar = null;
        Component tz = null;
        Property tzId = null;
        try {
            for (NodeIterator i=getNode().getNodes(); i.hasNext();) {
                childNode = i.nextNode();
                if (! childNode.
                    isNodeType(CosmoJcrConstants.NT_CALDAV_RESOURCE)) {
                    continue;
                }

                contentNode =
                    childNode.getNode(CosmoJcrConstants.NN_JCR_CONTENT);
                data = contentNode.getProperty(CosmoJcrConstants.NP_JCR_DATA).
                    getStream();
                childCalendar = builder.build(data);

                for (Iterator j=childCalendar.getComponents().
                         getComponents(Component.VEVENT).iterator();
                     j.hasNext();) {
                    calendar.getComponents().add((Component) j.next());
                }

                for (Iterator j=childCalendar.getComponents().
                         getComponents(Component.VTIMEZONE).iterator();
                     j.hasNext();) {
                    tz = (Component) j.next();
                    tzId = tz.getProperties().getProperty(Property.TZID);
                    if (! tzIdx.containsKey(tzId.getValue())) {
                        tzIdx.put(tzId.getValue(), tz);
                    }
                }
            }

            for (Iterator i=tzIdx.values().iterator(); i.hasNext();) {
                calendar.getComponents().add((Component) i.next());
            }

            return calendar;
        } catch (RepositoryException e) {
            log.error("can't get collection calendar", e);
            throw new JcrDavException(e);
        } catch (Exception e) {
            log.error("can't get collection calendar", e);
            throw new DavException(CosmoDavResponse.SC_INTERNAL_SERVER_ERROR,
                                   "can't get collection calendar");
        }
    }

    /**
     * Associates a ticket with this resource and saves it into
     * persistent storage.
     */
    public void saveTicket(Ticket ticket)
        throws DavException {
        if (!exists()) {
            throw new DavException(CosmoDavResponse.SC_CONFLICT);
        }
	if (isLocked(this)) {
            throw new DavException(CosmoDavResponse.SC_LOCKED);
        }
        if (!isTicketable()) {
            throw new DavException(CosmoDavResponse.SC_METHOD_NOT_ALLOWED);
        }

        try {
            Node resource = getNode();

            ticket.setOwner(getLoggedInUser().getUsername());

            TicketDao dao = (TicketDao) applicationContext.
                getBean(BEAN_TICKET_DAO, TicketDao.class);
            dao.createTicket(resource.getPath(), ticket);
        } catch (Exception e) {
            log.error("cannot save ticket for resource " + getResourcePath(),
                      e);
            throw new DavException(CosmoDavResponse.SC_INTERNAL_SERVER_ERROR,
                                   e.getMessage());
        }

        // refresh the ticketdiscovery property
        getProperties().add(new TicketDiscovery(this));
    }

    /**
     * Removes the association between the ticket and this resource
     * and deletes the ticket from persistent storage.
     */
    public void removeTicket(Ticket ticket)
        throws DavException {
        if (!exists()) {
            throw new DavException(CosmoDavResponse.SC_CONFLICT);
        }
	if (isLocked(this)) {
            throw new DavException(CosmoDavResponse.SC_LOCKED);
        }
        if (!isTicketable()) {
            throw new DavException(CosmoDavResponse.SC_METHOD_NOT_ALLOWED);
        }

        try {
            TicketDao dao = (TicketDao) applicationContext.
                getBean(BEAN_TICKET_DAO, TicketDao.class);
            dao.removeTicket(getNode().getPath(), ticket);
        } catch (Exception e) {
            log.error("cannot remove ticket " + ticket.getId() +
                      " for resource " + getResourcePath(), e);
            throw new DavException(CosmoDavResponse.SC_INTERNAL_SERVER_ERROR,
                                   e.getMessage());
        }

        // refresh the ticketdiscovery property
        getProperties().add(new TicketDiscovery(this));
    }

    /**
     * Returns the ticket with the given id on this resource. Does not
     * execute any security checks.
     */
    public Ticket getTicket(String id) {
        initTickets();
        return (Ticket) tickets.get(id);
    }

    /**
     * Returns all tickets owned by the named user on this resource,
     * or an empty <code>Set</code> if the user does not own any
     * tickets.
     *
     * @param username
     */
    public Set getTickets(String username) {
        initTickets();
        Set t = (Set) ownedTickets.get(username);
        return t != null ? t : new HashSet();
    }

    /**
     * Returns all tickets owned by the currently logged in user on
     * this resource, or an empty <code>Set</code> if the user does
     * not own any tickets.
     */
    public Set getLoggedInUserTickets() {
        return getTickets(getLoggedInUser().getUsername());
    }

    /**
     * Returns a resource locator for the named principal's homedir.
     */
    public DavResourceLocator getHomedirLocator(String principal) {
        return getLocator().getFactory().
            createResourceLocator(baseUrl, "/" + principal);
    }

    // DavResourceImpl methods

    /**
     */
    protected void initProperties() {
        if (! initializing) {
            initializing = true;
            super.initProperties();
            DavPropertySet properties = getProperties();

            if (isCalendarCollection() ||
                isCalendarHomeCollection()) {

                // override the default resource type property with
                // our own that sets the appropriate resource types
                // for calendar home collections (caldav section 4.2)
                // and calendar collections (caldav section 4.3)
                int[] resourceTypes = new int[2];
                resourceTypes[0] = CosmoResourceType.COLLECTION;
                resourceTypes[1] = isCalendarCollection() ?
                    CosmoResourceType.CALENDAR_COLLECTION :
                    CosmoResourceType.CALENDAR_HOME;
                properties.add(new CosmoResourceType(resourceTypes));
                // Windows XP support
                properties.add(new DefaultDavProperty(DavPropertyName.
                                                      ISCOLLECTION,
                                                      "1"));

                // calendar-description property (caldav section
                // 4.4.1) has a language attribute
                try {
                    if (getNode().hasProperty(CosmoJcrConstants.
                                              NP_CALDAV_CALENDARDESCRIPTION)) {
                        String text = getNode().
                            getProperty(CosmoJcrConstants.
                                        NP_CALDAV_CALENDARDESCRIPTION).
                            getString();
                        String lang = getNode().
                            getProperty(CosmoJcrConstants.NP_XML_LANG).
                            getString();
                        properties.add(new CalendarDescription(text, lang));
                    }
                } catch (RepositoryException e) {
                    log.warn("Unable to retrieve calendar description", e);
                }
            }

            if (isCalendarCollection()) {
                // calendar-component-restriction-set property (caldav
                // section 4.4.2)
                // the entire Cosmo server allows only the components
                // specified by this constant, and this behavior can
                // not be modified by clients
                DavProperty davprop =
                    new CalendarComponentRestrictionSet(ICALENDAR_COMPONENTS);
                properties.add(davprop);

                // calendar-restrictions property (caldav section
                // 4.4.3)
                // the entire Cosmo server allows non-calendar data
                // within calendar collections, and this behavior can
                // not be modified by clients
                properties.add(new CalendarRestrictions());
            }

            if (isTicketable()) {
                initTickets();
                properties.add(new TicketDiscovery(this));
            }

            initializing = false;
        }
    }

    /**
     */
    protected ImportContext getImportContext(InputContext inputCtx,
                                             String systemId)
        throws IOException {
        return new CosmoImportContext(getNode(), systemId, inputCtx);
    }

    /**
     */
    protected void initTickets() {
        // this should only happen before CosmoDavServlet.service
        // executes - for instance validating preconditions when
        // locking
        if (applicationContext == null) {
            return;
        }

        if (isTicketable() && tickets == null && exists()) {
            tickets = new HashMap();
            ownedTickets = new HashMap();

            try {
                TicketDao dao = (TicketDao)
                    applicationContext.getBean(BEAN_TICKET_DAO, TicketDao.class);
                for (Iterator i=dao.getTickets(getNode().getPath()).iterator();
                     i.hasNext();) {
                    Ticket ticket = (Ticket) i.next();
                    tickets.put(ticket.getId(), ticket);
                    Set ownedBy = (Set) ownedTickets.get(ticket.getOwner());
                    if (ownedBy == null) {
                        ownedBy = new HashSet();
                        ownedTickets.put(ticket.getOwner(), ownedBy);
                    }
                    ownedBy.add(ticket);
                }
            } catch (RepositoryException e) {
                log.warn("error getting tickets for node", e);
            }
        }
    }

    // ApplicationContextAware methods

    /**
     */
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // our methods

    /**
     */
    protected User getLoggedInUser() {
        CosmoDavResourceFactory cosmoFactory =
            (CosmoDavResourceFactory) getFactory();
        return cosmoFactory.getSecurityManager().getSecurityContext().
            getUser();
    }

    /**
     * Set the base URL for the server on which this resource lives
     * (could be statically configured or dynamically calculated
     * per-request).
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
