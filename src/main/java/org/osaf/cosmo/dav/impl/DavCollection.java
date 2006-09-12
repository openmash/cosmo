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
package org.osaf.cosmo.dav.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.jackrabbit.server.io.IOUtil;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceIterator;
import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavServletRequest;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.DavSession;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.io.OutputContext;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyIterator;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.property.ResourceType;

import org.apache.log4j.Logger;

import org.osaf.cosmo.dav.CosmoDavMethods;
import org.osaf.cosmo.model.CalendarCollectionItem;
import org.osaf.cosmo.model.CollectionItem;
import org.osaf.cosmo.model.ContentItem;
import org.osaf.cosmo.model.Item;
import org.osaf.cosmo.model.ModelValidationException;
import org.osaf.cosmo.util.PathUtil;

/**
 * Extends <code>DavResourceBase</code> to adapt the Cosmo
 * <code>CollectionItem</code> to the DAV resource model.
 *
 * This class does not define any live properties.
 *
 * @see DavResourceBase
 * @see CollectionItem
 */
public class DavCollection extends DavResourceBase {
    private static final Logger log = Logger.getLogger(DavCollection.class);
    private static final int[] RESOURCE_TYPES;
    private static final Set DEAD_PROPERTY_FILTER = new HashSet();

    private ArrayList members;

    static {
        RESOURCE_TYPES = new int[] { ResourceType.COLLECTION };
    }

    /** */
    public DavCollection(CollectionItem collection,
                         DavResourceLocator locator,
                         DavResourceFactory factory,
                         DavSession session) {
        super(collection, locator, factory, session);
        members = new ArrayList();
    }

    /** */
    public DavCollection(DavResourceLocator locator,
                         DavResourceFactory factory,
                         DavSession session) {
        this(new CollectionItem(), locator, factory, session);
    }

    // DavResource

    /** */
    public String getSupportedMethods() {
        return "OPTIONS, GET, HEAD, TRACE, PROPFIND, PROPPATCH, COPY, DELETE, MOVE, MKTICKET, DELTICKET, MKCOL, MKCALENDAR";
    }

    /** */
    public long getModificationTime() {
        return -1;
    }

    /** */
    public String getETag() {
        return "";
    }

    /** */
    public void spool(OutputContext outputContext)
        throws IOException {
        writeHtmlDirectoryIndex(outputContext);
    }

    /**
     * Adds the given member resource to the collection (or updates it
     * if it is an existing file resource).
     *
     * Calls the following methods:
     *
     * <ol>
     * <li> {@link #populateItem(InputContext)} on the member to
     * populate its backing item from the input context</li>
     * <li> {@link #saveSubcollection(DavCollection)} or
     * {@link #saveFile(DavFile)} to actually save the
     * member into storage</li>
     * </ol>
     *
     */
    public void addMember(DavResource member,
                          InputContext inputContext)
        throws DavException {
        ((DavResourceBase)member).populateItem(inputContext);

        if (member instanceof DavCollection) {
            saveSubcollection((DavCollection)member);
        } else {
            saveFile((DavFile)member);
        }

        members.add(member);
    }

    /** */
    public MultiStatusResponse addMember(DavResource member,
                                         InputContext inputContext,
                                         DavPropertySet properties)
        throws DavException {
        MultiStatusResponse msr =
            ((DavResourceBase)member).populateAttributes(properties);

        addMember(member, inputContext);

        return msr;
    }

    /** */
    public DavResourceIterator getMembers() {
        loadMembers();
        return new DavResourceIteratorImpl(members);
    }

    /** */
    public DavResource getMember(String href)
        throws DavException {
        // XXX
        throw new UnsupportedOperationException();
    }

    /**
     * Removes the given member resource from the collection.
     *
     * Calls {@link #removeSubcollection(DavCollection)} or
     * {@link #removeFile(DavFile)} to actually remove the
     * member from storage.
     */
    public void removeMember(DavResource member)
        throws DavException {
        if (member instanceof DavCollection) {
            removeSubcollection((DavCollection)member);
        } else {
            removeFile((DavFile)member);
        }

        members.remove(member);
    }

    // our methods

    /** */
    protected int[] getResourceTypes() {
        return RESOURCE_TYPES;
    }

    /** */
    protected void loadLiveProperties() {
        // no additional live properties
    }

    /** */
    protected void setLiveProperty(DavProperty property) {
        // no additional live properties
    }

    /** */
    protected void removeLiveProperty(DavPropertyName name) {
        // no additional live properties
    }

    /** */
    protected Set getDeadPropertyFilter() {
        return DEAD_PROPERTY_FILTER;
    }

    /**
     * Saves the given collection resource to storage.
     */
    protected void saveSubcollection(DavCollection member)
        throws DavException {
        CollectionItem collection = (CollectionItem) getItem();

        if (member instanceof DavCalendarCollection) {
            CalendarCollectionItem subcollection =
                (CalendarCollectionItem) member.getItem();

            if (log.isDebugEnabled())
                log.debug("creating calendar collection " +
                          member.getResourcePath());

            // XXX: what exceptions need to be caught?
            subcollection = getContentService().
                createCalendar(collection, subcollection);
            member.setItem(subcollection);
        } else {
            CollectionItem subcollection = (CollectionItem) member.getItem();

            if (log.isDebugEnabled())
                log.debug("creating collection " + member.getResourcePath());

            // XXX: what exceptions need to be caught?
            subcollection = getContentService().
                createCollection(collection, subcollection);
            member.setItem(subcollection);
        }
    }

    /**
     * Saves the given file resource to storage.
     */
    protected void saveFile(DavFile member)
        throws DavException {
        CollectionItem collection = (CollectionItem) getItem();
        ContentItem content = (ContentItem) member.getItem();

        // XXX: what exceptions need to be caught?
        if (content.getId() != -1) {
            if (log.isDebugEnabled())
                log.debug("updating file " + member.getResourcePath());

            content = getContentService().updateContent(content);
        } else {
            if (log.isDebugEnabled())
                log.debug("creating file " + member.getResourcePath());
            
            content =
                getContentService().createContent(collection, content);
        }

        member.setItem(content);
    }

    /**
     * Removes the given collection resource from storage.
     */
    protected void removeSubcollection(DavCollection member)
        throws DavException {
        CollectionItem collection = (CollectionItem) getItem();

        if (member instanceof DavCalendarCollection) {
            CalendarCollectionItem subcollection =
                (CalendarCollectionItem) member.getItem();

            if (log.isDebugEnabled())
                log.debug("removing calendar collection " +
                          subcollection.getName() +
                          " from " + collection.getName());

            // XXX: what exceptions need to be caught?
            getContentService().removeCalendar(subcollection);
        } else {
            CollectionItem subcollection = (CollectionItem) member.getItem();

            if (log.isDebugEnabled())
                log.debug("removing collection " + subcollection.getName() +
                          " from " + collection.getName());

            // XXX: what exceptions need to be caught?
            getContentService().removeCollection(subcollection);
        }
    }

    /**
     * Removes the given file resource from storage.
     */
    protected void removeFile(DavFile member)
        throws DavException {
        CollectionItem collection = (CollectionItem) getItem();
        ContentItem content = (ContentItem) member.getItem();

        // XXX: what exceptions need to be caught?
        if (log.isDebugEnabled())
            log.debug("removing content " + content.getName() +
                      " from " + collection.getName());

        getContentService().removeContent(content);
    }

    private void loadMembers() {
        for (Iterator i=((CollectionItem)getItem()).getChildren().iterator();
             i.hasNext();) {
            Item memberItem = (Item) i.next();
            String memberPath = getResourcePath() + "/" + memberItem.getName();
            try {
                DavResourceLocator memberLocator =
                    getLocator().getFactory().
                    createResourceLocator(getLocator().getPrefix(),
                                          getLocator().getWorkspacePath(),
                                          memberPath, false);
                DavResource member =
                    ((StandardDavResourceFactory)getFactory()).
                    createResource(memberLocator, getSession(), memberItem);
                members.add(member);
            } catch (DavException e) {
                // XXX should never happen
                log.error("error loading member resource for item " +
                          memberItem.getName() + " in collection " +
                          getResourcePath(), e);
            }
        }
    }

    // creates a DavResource wrapping the given member item and adds
    // it to the internal members list
    private void stashMember(Item memberItem) {
        if (log.isDebugEnabled())
            log.debug("stashing member " + memberItem.getName());

        String memberPath = getResourcePath() + "/" + memberItem.getName();
        try {
            DavResourceLocator memberLocator =
                getLocator().getFactory().
                createResourceLocator(getLocator().getPrefix(),
                                      getLocator().getWorkspacePath(),
                                      memberPath, false);
            DavResource member =
                ((StandardDavResourceFactory)getFactory()).
                createResource(memberLocator, getSession(), memberItem);

            members.add(member);
        } catch (DavException e) {
            // XXX should never happen
            log.error("error stashing member resource for item " +
                      memberItem.getName() + " in collection " +
                      getResourcePath(), e);
        }
    }

    private void writeHtmlDirectoryIndex(OutputContext context)
        throws IOException {
        if (log.isDebugEnabled())
            log.debug("writing html directory index for  " +
                      getItem().getName());

        context.setContentType(IOUtil.buildContentType("text/html", "UTF-8"));
        // XXX content length unknown unless we write a temp file
        // modification time and etag are undefined for a collection

        if (! context.hasStream()) {
            return;
        }

        PrintWriter writer =
            new PrintWriter(new OutputStreamWriter(context.getOutputStream(),
                                                   "utf8"));
        String title = getLocator().getResourcePath();
        writer.write("<html><head><title>");
        writer.write(title); // XXX: html escape
        writer.write("</title></head>");
        writer.write("<body>");
        writer.write("<h1>");
        writer.write(title); // XXX: html escape
        writer.write("</h1>");
        writer.write("<ul>");
        if (! getLocator().getResourcePath().equals("/")) {
            writer.write("<li><a href=\"../\">..</a></li>");
        }
        for (DavResourceIterator i=getMembers(); i.hasNext();) {
            DavResourceBase child = (DavResourceBase) i.nextResource();
            String name =
                PathUtil.getBasename(child.getLocator().getResourcePath()); 
            String displayName = child.getItem().getName();
            writer.write("<li><a href=\"");
            writer.write(name); // XXX URI escape
            if (child.isCollection()) {
                writer.write("/");
            }
            writer.write("\">");
            writer.write(displayName); // XXX: html escape
            writer.write("</a></li>");
        }
        writer.write("</ul>");
        writer.write("</body>");
        writer.write("</html>");
        writer.write("\n");
        writer.close();
    }
}
