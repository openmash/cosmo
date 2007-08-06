/*
 * Copyright 2006-2007 Open Source Applications Foundation
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
package org.osaf.cosmo.dav;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.jackrabbit.webdav.DavResourceLocator;

import org.osaf.cosmo.dav.DavException;
import org.osaf.cosmo.dav.DavRequest;
import org.osaf.cosmo.dav.DavResource;
import org.osaf.cosmo.dav.DavResourceFactory;
import org.osaf.cosmo.dav.NotFoundException;
import org.osaf.cosmo.dav.impl.DavCalendarCollection;
import org.osaf.cosmo.dav.impl.DavCollection;
import org.osaf.cosmo.dav.impl.DavEvent;
import org.osaf.cosmo.dav.impl.DavFile;
import org.osaf.cosmo.dav.impl.DavHomeCollection;
import org.osaf.cosmo.dav.impl.DavJournal;
import org.osaf.cosmo.dav.impl.DavTask;
import org.osaf.cosmo.model.CalendarCollectionStamp;
import org.osaf.cosmo.model.CollectionItem;
import org.osaf.cosmo.model.EventStamp;
import org.osaf.cosmo.model.FileItem;
import org.osaf.cosmo.model.HomeCollectionItem;
import org.osaf.cosmo.model.Item;
import org.osaf.cosmo.model.NoteItem;
import org.osaf.cosmo.model.TaskStamp;
import org.osaf.cosmo.security.CosmoSecurityManager;
import org.osaf.cosmo.service.ContentService;
import org.osaf.cosmo.util.UriTemplate;

/**
 * Standard implementation of <code>DavResourceFactory</code>.
 *
 * @see DavResource
 * @see Item
 */
public class StandardResourceFactory implements DavResourceFactory {
    private static final Log log =
        LogFactory.getLog(DavResourceFactory.class);
    private static final UriTemplate TEMPLATE_COLLECTION =
        new UriTemplate("/collection/{uid}/*");
    private static final UriTemplate TEMPLATE_ITEM =
        new UriTemplate("/item/{uid}/*");

    private ContentService contentService;
    private CosmoSecurityManager securityManager;

    public StandardResourceFactory(ContentService contentService,
                                   CosmoSecurityManager securityManager) {
        this.contentService = contentService;
        this.securityManager = securityManager;
    }

    /**
     * <p>
     * Resolves a {@link DavResourceLocator} into a {@link DavResource}.
     * </p>
     * <p>
     * If the identified resource does not exist and the request method
     * indicates that one is to be created, returns a resource backed by a 
     * newly-instantiated item that has not been persisted or assigned a UID.
     * Otherwise, if the resource does not exists, then a
     * {@link NotFoundException} is thrown.
     * </p>
     * <p>
     * The type of resource to create is chosen as such:
     * <ul>
     * <li><code>MKCALENDAR</code>: {@link DavCalendarCollection}</li>
     * <li><code>MKCOL</code>: {@link DavCollection}</li>
     * <li><code>PUT</code>, <code>COPY</code>, <code>MOVE</code></li>:
     * {@link DavFile}</li>
     * </ul>
     */
    public DavResource resolve(DavResourceLocator locator,
                               DavRequest request)
        throws DavException {
        DavResource resource = resolve(locator);
        if (resource != null)
            return resource;

        // we didn't find an item in storage for the resource, so either
        // the request is creating a resource or the request is targeting a
        // nonexistent item.
        if (request.getMethod().equals("MKCALENDAR"))
            return new DavCalendarCollection(locator, this);
        if (request.getMethod().equals("MKCOL"))
            return new DavCollection(locator, this);
        if (request.getMethod().equals("PUT") ||
            request.getMethod().equals("COPY") ||
            request.getMethod().equals("MOVE"))
            // will be replaced by the provider if a different resource
            // type is required
            return new DavFile(locator, this);

        throw new NotFoundException();
    }

    /**
     * <p>
     * Resolves a {@link DavResourceLocator} into a {@link DavResource}.
     * </p>
     * <p>
     * If the identified resource does not exists, returns <code>null</code>.
     * </p>
     */
    public DavResource resolve(DavResourceLocator locator) {
        String uri = locator.getResourcePath();
        if (log.isDebugEnabled())
            log.debug("resolving URI " + uri);

        UriTemplate.Match match = null;

        match = TEMPLATE_COLLECTION.match(uri);
        if (match != null)
            return createUidResource(locator, match);

        match = TEMPLATE_ITEM.match(uri);
        if (match != null)
            return createUidResource(locator, match);

        return createUnknownResource(locator, uri);
    }

    /**
     * <p>
     * Instantiates a <code>DavResource</code> representing the
     * <code>Item</code> located by the given <code>DavResourceLocator</code>.
     * </p>
     */
    public DavResource createResource(DavResourceLocator locator,
                                      Item item) {
        if (item == null)
            throw new IllegalArgumentException("item cannot be null");

        if (item instanceof HomeCollectionItem)
            return new DavHomeCollection((HomeCollectionItem) item, locator,
                                         this);

        if (item instanceof CollectionItem) {
            if (item.getStamp(CalendarCollectionStamp.class) != null)
                return new DavCalendarCollection((CollectionItem) item,
                                                 locator, this);
            else
                return new DavCollection((CollectionItem) item, locator, this);
        }

        if (item instanceof NoteItem) {
            if (item.getStamp(EventStamp.class) != null)
                return new DavEvent((NoteItem) item, locator, this);
            else if (item.getStamp(TaskStamp.class) != null)
                return new DavTask((NoteItem) item, locator, this);
            else 
                return new DavJournal((NoteItem) item, locator, this);
        } 

        return new DavFile((FileItem) item, locator, this);
    }

    // our methods

    protected DavResource createUidResource(DavResourceLocator locator,
                                            UriTemplate.Match match) {
        String uid = match.get("uid");
        String path = match.get("*");
        Item item = path != null ?
            contentService.findItemByPath(path, uid) :
            contentService.findItemByUid(uid);
        return item != null ? createResource(locator, item) : null;
    }

    protected DavResource createUnknownResource(DavResourceLocator locator,
                                                String uri) {
        Item item = contentService.findItemByPath(uri);
        return item != null ? createResource(locator, item) : null;
    }

    public ContentService getContentService() {
        return contentService;
    }

    public CosmoSecurityManager getSecurityManager() {
        return securityManager;
    }
}
