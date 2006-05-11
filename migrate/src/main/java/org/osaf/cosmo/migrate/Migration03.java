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
package org.osaf.cosmo.migrate;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import javax.jcr.NamespaceException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import org.apache.jackrabbit.core.nodetype.NodeTypeManagerImpl;
import org.apache.jackrabbit.util.HexEscaper;

import org.apache.log4j.Logger;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Migrates the 0.2 schema to 0.3 and vice versa.
 */
public class Migration03 extends CopyBasedMigration {
    private static Logger log = Logger.getLogger(Migration03.class);

    /**
     * <code>nodetypes.xml</code>
     */
    public static final String RESOURCE_NODETYPES = "nodetypes.xml";

    private static final String VERSION = "0.3";
    private static final String DB_DRIVER = "org.hsqldb.jdbcDriver";
    private static final String DB_USERNAME = "sa";
    private static final String DB_PASSWORD = "";
    private static final String SQL_LOAD_OVERLORD =
        "SELECT username, password, firstName, lastName, email, dateCreated, dateModified FROM user WHERE username = 'root'";
    private static final String SQL_LOAD_USERS =
        "SELECT LIMIT 0 0 username, password, firstName, lastName, email, dateCreated, dateModified, id FROM user WHERE username != 'root'";
    private static final String SQL_LOAD_ROOT_IDS =
        "SELECT userid FROM userrole WHERE roleid = 1 AND userid != 1";
    private static final String SQL_SHUTDOWN = "SHUTDOWN";

    private Connection connection;
    private String db;

    // CopyBasedMigration methods

    /**
     * Registers namespaces into the current repository, including
     * namespaces used by Cosmo as well as custom namespaces
     * registered into the previous repository.
     *
     * Builtin Cosmo namespaces include:
     *
     * <dl>
     * <dt>dav</dt>
     * <dd>DAV:</dd>
     * <dt>calendar</dt>
     * <dd>http://osafoundation.org/calendar</dd>
     * <dt>ticket</dt>
     * <dd>http://www.xythos.com/namespaces/StorageServer</dd>
     * <dt>cosmo</dt>
     * <dd>http://osafoundation.org/cosmo</dd>
     * </dl>
     *
     * This method is not bound to the lifecycle of the
     * <code>Migration</code>. It should only be called once during
     * the lifetime of a particular repository. Subsequent calls will
     * cause <code>MigrationException<code> to be thrown.
     */
    public static void registerNamespaces(Session previous,
                                          Session current)
        throws MigrationException {
        try {
            NamespaceRegistry prevRegistry =
                previous.getWorkspace().getNamespaceRegistry();
            NamespaceRegistry curRegistry =
                current.getWorkspace().getNamespaceRegistry();

            // namespaces used by our code
            registerNamespace(curRegistry, "dav", "DAV:");
            registerNamespace(curRegistry, "calendar",
                              "http://osafoundation.org/calendar");
            registerNamespace(curRegistry, "ticket",
                              "http://www.xythos.com/namespaces/StorageServer");
            registerNamespace(curRegistry, "cosmo",
                              "http://osafoundation.org/cosmo");

            // custom namespaces
            String[] prevPrefixes = prevRegistry.getPrefixes();
            for (int i=0; i<prevPrefixes.length; i++) {
                if ("caldav".equals(prevPrefixes[i])) {
                    continue;
                }
                registerNamespace(prevRegistry, curRegistry, prevPrefixes[i]);
            }
        } catch (RepositoryException e) {
            throw new MigrationException("unable to register namespaces", e);
        }
    }

    /**
     * Registers node types into the current repository.
     *
     * Node types are loaded from a classpath resource named by
     * {@link #RESOURCE_NODETYPES}.
     *
     * This method is not bound to the lifecycle of the
     * <code>Migration</code>. It should only be called once during
     * the lifetime of a particular repository. Subsequent calls will
     * cause <code>MigrationException<code> to be thrown.
     */
    public static void registerNodeTypes(Session current)
        throws MigrationException {
        NodeTypeManagerImpl mgr = null;
        try {
            mgr = (NodeTypeManagerImpl)
                current.getWorkspace().getNodeTypeManager();
            NodeType test = mgr.getNodeType("cosmo:user");
            // custom node types are already registered, so bail out
            return;
        } catch (NoSuchNodeTypeException e) {
            // expected when custom node types have not yet been registered
        } catch (RepositoryException e) {
            throw new MigrationException("unable to check for custom node type", e);
        }

        InputStream in = null;
        in = Migration03.class.getClassLoader().
            getResourceAsStream(RESOURCE_NODETYPES);
        if (in == null) {
            throw new MigrationException("nodetype resource " + RESOURCE_NODETYPES + " not found");
        }

        try {
            mgr.registerNodeTypes(new InputSource(in));
        } catch (Exception e) {
            throw new MigrationException("cannot register node types", e);
        }
    }

    /**
     * Connects to the 0.2 user database located at the filesystem
     * location given by {@link #getDb()}, unless a
     * connection has previously been set with
     * {@link #setConnection(Connection)}.
     */
    public void init()
        throws MigrationException {
        if (connection != null) {
            return;
        }

        String url = "jdbc:hsqldb:file:" + db + "userdb";

        if (log.isDebugEnabled()) {
            log.debug("Connecting to user database at " + url);
        }
        try {
            Class.forName(DB_DRIVER);
            connection = DriverManager.getConnection(url, DB_USERNAME,
                                                     DB_PASSWORD);
        } catch (Exception e) {
            throw new MigrationException("Cannot connect to userdb at " +
                                         url, e);
        }
    }

    /**
     */
    public void up(Session previous,
                   Session current)
        throws MigrationException {
        registerNamespaces(previous, current);
        registerNodeTypes(current);

        String schemaVersion = readSchemaVersion(current);
        if (schemaVersion != null) {
            // doublecheck the written schema version. we can only run
            // against our own version
            if (! getVersion().equals(schemaVersion)) {
                throw new MigrationException("target repository is of unsupported version " + schemaVersion);
            }
        }
        else {
            writeSchemaVersion(current);

            // no way to detect if overlord has been imported on a
            // previous run, so ensure that it is only copied on the
            // first run
            HashMap overlord = loadOverlord();
            try {
                long copyStartTime = System.currentTimeMillis();
                copyOverlord(overlord, current);
                long copyStopTime = System.currentTimeMillis();
                System.out.println("Copied overlord in " + formatElapsedSeconds(copyStopTime - copyStartTime));
                long saveStartTime = System.currentTimeMillis();
                current.save();
                long saveStopTime = System.currentTimeMillis();
                System.out.println("Saved overlord in " + formatElapsedSeconds(saveStopTime - saveStartTime));
            } catch (RepositoryException e) {
                try {
                    current.refresh(false);
                } catch (RepositoryException re) {
                    throw new MigrationException("cannot refresh current session", re);                
                }
                throw new MigrationException("cannot copy overlord", e);
            }
        }

        List<HashMap> users = loadUsers();
        System.out.println("Loaded " + users.size() + " users");

        int skipped = 0;
        int copied = 0;
        long startTime = System.currentTimeMillis();
        for (Iterator<HashMap> i=users.iterator(); i.hasNext();) {
            HashMap user = i.next();
            String username = (String) user.get("username");
            try {
                // only save if the home directory was actually copied
                long copyStartTime = System.currentTimeMillis();
                if (copyHome(user, previous, current) != null) {
                    long copyStopTime = System.currentTimeMillis();
                    System.out.println("Copied " + username + " in " + formatElapsedSeconds(copyStopTime - copyStartTime));
                    long saveStartTime = System.currentTimeMillis();
                    current.save();
                    long saveStopTime = System.currentTimeMillis();
                    System.out.println("Saved " + username + " in " + formatElapsedSeconds(saveStopTime - saveStartTime));
                    copied++;
                }
            } catch (RepositoryException e) {
                skipped++;
                System.out.println("SKIPPED " + username);
                e.printStackTrace();
                try {
                    current.refresh(false);
                } catch (RepositoryException re) {
                    throw new MigrationException("cannot refresh current session", re);
                }
                continue;
            }
        }
        long stopTime = System.currentTimeMillis();

        System.out.println(copied + " users copied in " + formatElapsedHours(stopTime - startTime));
        if (skipped > 0) {
            System.out.println(skipped + " users skipped - see stderr for details");
        }
    }

    /**
     */
    public void down(Session current,
                     Session previous)
        throws MigrationException {
        throw new MigrationException("migrating down not supported");
    }

    /**
     */
    public void release()
        throws MigrationException {
        try {
            if (connection != null) {
                Statement st = connection.createStatement();
                st.execute(SQL_SHUTDOWN);
                connection.close();
            }
        } catch (Exception e) {
            throw new MigrationException("Cannot shut down user db", e);
        }
    }

    /**
     */
    public String getVersion() {
        return VERSION;
    }

    // our methods

    /**
     */
    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    /**
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     */
    public String getDb() {
        return db;
    }

    /**
     */
    public void setDb(String db) {
        this.db = db;
    }

    // package protected methods, for individuable testability

    String readSchemaVersion(Session current)
        throws MigrationException {
        try {
            if (current.itemExists("/cosmo:system/cosmo:schema/cosmo:schemaVersion")) {
                Property schemaVersion =
                    (Property) current.getItem("/cosmo:system/cosmo:schema/cosmo:schemaVersion");
                return schemaVersion.getString();
            }
            return null;
        } catch (RepositoryException e) {
            throw new MigrationException("Cannot read schema version", e);
        }
    }

    void writeSchemaVersion(Session current)
        throws MigrationException {
        try {
            Node rootNode = current.getRootNode();
            Node cosmoSystemNode = rootNode.hasNode("cosmo:system") ?
                rootNode.getNode("cosmo:system") :
                rootNode.addNode("cosmo:system", "nt:unstructured");
            Node schemaNode = cosmoSystemNode.hasNode("cosmo:schema") ?
                cosmoSystemNode.getNode("cosmo:schema") :
                cosmoSystemNode.addNode("cosmo:schema", "nt:unstructured");
            schemaNode.setProperty("cosmo:schemaVersion", getVersion());
            current.save();
        } catch (RepositoryException e) {
            throw new MigrationException("Cannot write schema version", e);
        }
    }

    HashMap loadOverlord()
        throws MigrationException {
        HashMap overlord = null;

        if (log.isDebugEnabled()) {
            log.debug("Loading overlord");
        }
        try {
            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery(SQL_LOAD_OVERLORD);
            for (; rs.next();) {
                overlord = resultSetToUser(rs);
                overlord.put("admin", Boolean.TRUE);
            }
            st.close();
        } catch (Exception e) {
            throw new MigrationException("Cannot load overlord", e);
        }

        return overlord;
    }

    List<HashMap> loadUsers()
        throws MigrationException {
        if (log.isDebugEnabled()) {
            log.debug("Loading administrator associations");
        }
        HashSet rootIds = new HashSet();
        try {
            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery(SQL_LOAD_ROOT_IDS);
            for (; rs.next();) {
                Integer id = (Integer) rs.getInt("userid");
                rootIds.add(id);
            }
            st.close();
        } catch (Exception e) {
            throw new MigrationException("Cannot load users", e);
        }

        if (log.isDebugEnabled()) {
            log.debug("Loading users");
        }
        ArrayList users = new ArrayList<HashMap>();
        try {
            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery(SQL_LOAD_USERS);
            while (rs.next()) {
                Integer id = rs.getInt("id");
                HashMap user = resultSetToUser(rs);
                if (rootIds.contains(id)) {
                    user.put("admin", Boolean.TRUE);
                }
                users.add(user);
            }
            st.close();
        } catch (Exception e) {
            throw new MigrationException("Cannot load users", e);
        }

        // sort case insensitively
        Collections.sort(users, new Comparator<HashMap>() {
            public int compare(HashMap o1, HashMap o2) {
                String u1 = (String) o1.get("username");
                String u2 = (String) o2.get("username");
                return u1.compareToIgnoreCase(u2);
            }
        });

        return users;
    }

    Node copyOverlord(HashMap overlord,
                      Session current)
        throws RepositoryException {
        Node home = null;
        if (current.itemExists("/r/ro/root")) {
            home = (Node) current.getItem("/r/ro/root");
        }
        else {
            Node root = current.getRootNode();
            Node l1 = root.hasNode("r") ?
                root.getNode("r") : root.addNode("r", "nt:unstructured");
            Node l2 = l1.hasNode("ro") ?
                l1.getNode("ro") : l1.addNode("ro", "nt:unstructured");
            home = l2.addNode("root", "nt:base");
            home.addMixin("cosmo:user");
            home.setProperty("cosmo:username", "root");
            home.setProperty("cosmo:admin", Boolean.TRUE);
        }

        // username, admin are not modifiable
        home.setProperty("cosmo:password", (String) overlord.get("password"));
        home.setProperty("cosmo:firstName", (String) overlord.get("firstName"));
        home.setProperty("cosmo:lastName", (String) overlord.get("lastName"));
        home.setProperty("cosmo:email", (String) overlord.get("email"));
        home.setProperty("cosmo:dateCreated",
                         (Calendar) overlord.get("dateCreated"));
        home.setProperty("cosmo:dateModified",
                         (Calendar) overlord.get("dateModified"));

        return home;
    }

    String calculateOldHomePath(String username) {
        String escapedUsername = HexEscaper.escape(username);
        // bug 5825: explicitly escape periods in the first or second
        // characters of the username when calculating the old path
        String periodEscaped =
            escapedUsername.substring(0, 2).replaceAll("\\.", "%2e");
        return "/" + periodEscaped + escapedUsername.substring(2);
    }

    String calculateNewHomePath(String username) {
        String n1 = HexEscaper.escape(username.substring(0, 1));
        String n2 = HexEscaper.escape(username.substring(0, 2));
        return "/" + n1 + "/" + n2 + "/" + HexEscaper.escape(username);
    }

    Node copyHome(HashMap user,
                  Session previous,
                  Session current)
        throws RepositoryException {
        String username = (String) user.get("username");

        String newHomePath = calculateNewHomePath(username);
        if (current.itemExists(newHomePath)) {
            // looks like a previous migration run already copied this
            // user's home.
            return null;
        }

        // find old home node
        String oldHomePath = calculateOldHomePath(username);
        Node oldHomeNode = (Node) previous.getItem(oldHomePath);

        // create structural nodes
        String[] segments = newHomePath.substring(1).split("/");
        Node root = current.getRootNode();
        Node l1 = root.hasNode(segments[0]) ?
            root.getNode(segments[0]) :
            root.addNode(segments[0], "nt:unstructured");
        Node l2 = l1.hasNode(segments[1]) ?
            l1.getNode(segments[1]) :
            l1.addNode(segments[1], "nt:unstructured");

        // copy home node
        Node home = copyNode(oldHomeNode, l2, "cosmo:homecollection");

        // copy user details
        home.addMixin("cosmo:user");
        home.setProperty("cosmo:username", (String) user.get("username"));
        home.setProperty("cosmo:password", (String) user.get("password"));
        home.setProperty("cosmo:firstName", (String) user.get("firstName"));
        home.setProperty("cosmo:lastName", (String) user.get("lastName"));
        home.setProperty("cosmo:email", (String) user.get("email"));
        home.setProperty("cosmo:admin",
                         ((Boolean) user.get("admin")).booleanValue());
        home.setProperty("cosmo:dateCreated",
                         (Calendar) user.get("dateCreated"));
        home.setProperty("cosmo:dateModified",
                         (Calendar) user.get("dateModified"));

        return home;
    }

    // private methods

    private static void registerNamespace(NamespaceRegistry curRegistry,
                                          String prefix,
                                          String uri)
        throws RepositoryException {
        try {
            curRegistry.getURI(prefix);
        } catch (NamespaceException e) {
            // namespace prefix is not registered in the current
            // repository, so register it
            curRegistry.registerNamespace(prefix, uri);
            System.out.println("Registered namespace " + prefix + ": " + uri);
        }
    }

    private static void registerNamespace(NamespaceRegistry prevRegistry,
                                          NamespaceRegistry curRegistry,
                                          String prefix)
        throws RepositoryException {
        registerNamespace(curRegistry, prefix, prevRegistry.getURI(prefix));
    }

    private HashMap resultSetToUser(ResultSet rs)
        throws SQLException {
        HashMap user = new HashMap();
        user.put("username", rs.getString("username"));
        user.put("password", rs.getString("password"));
        user.put("firstName", rs.getString("firstName"));
        user.put("lastName", rs.getString("lastName"));
        user.put("email", rs.getString("email"));
        user.put("admin", Boolean.FALSE);
        Calendar created = Calendar.getInstance();
        created.setTime(rs.getDate("dateCreated"));
        user.put("dateCreated", created);
        Calendar modified = Calendar.getInstance();
        modified.setTime(rs.getDate("dateModified"));
        user.put("dateModified", modified);
        return user;
    }

    private Node copyNode(Node original,
                          Node parent)
        throws RepositoryException {
        return copyNode(original, parent, null);
    }

    private Node copyNode(Node original,
                          Node parent,
                          String primaryType)
        throws RepositoryException {
        long startTime = System.currentTimeMillis();

        if (primaryType == null) {
            primaryType =
                translateNodeType(original.getPrimaryNodeType().getName());
        }

        // copy the original node into a child of the parent node
        if (log.isDebugEnabled()) {
            log.debug("copying node named " + original.getName() + " of type " +
                      primaryType + " into " + parent.getPath());
        }
        Node copied = parent.addNode(original.getName(), primaryType);

        // add mixin types
        NodeType[] previousMixins = original.getMixinNodeTypes();
        for (int j=0; j<previousMixins.length; j++) {
            String mixinType = translateNodeType(previousMixins[j].getName());
            if ("cosmo:homecollection".equals(mixinType)) {
                // home collection nodes are created explicitly by the
                // copyHome method - ignore them here
                continue;
            }
            if (log.isDebugEnabled()) {
                log.debug("adding mixin type " + mixinType);
            }
            copied.addMixin(mixinType);
        }

        // 0.3 has a subtype of caldav:resource for events, so add it
        // 0.to all nodes with that type (to distinguish from tasks
        // 0.etc in the future)
        if (original.isNodeType("caldav:resource")) {
            if (log.isDebugEnabled()) {
                log.debug("adding mixin type calendar:event");
            }
            copied.addMixin("calendar:event");

        }

        // copy properties
        for (PropertyIterator k=original.getProperties(); k.hasNext();) {
            Property prop = k.nextProperty();
            if (prop.getDefinition().isProtected()) {
                continue;
            }
            if (log.isDebugEnabled()) {
                log.debug("setting property " +
                          translatePropertyName(prop.getName()));
            }
            if (prop.getDefinition().isMultiple()) {
                copyMultiValuedProperty(prop, copied);
            }
            else {
                copyProperty(prop, copied);
            }
        }

        // 0.3 adds dav:created and dav:lastModified which are
        // initialized from corresponding 0.2 jcr builtin properties
        if (copied.isNodeType("dav:collection") ||
            copied.isNodeType("dav:resource")) {
            if (log.isDebugEnabled()) {
                log.debug("setting property dav:created");
            }
            Calendar created = original.getProperty("jcr:created").getDate();
            copied.setProperty("dav:created", created);
            if (log.isDebugEnabled()) {
                log.debug("setting property dav:lastModified");
            }
            Calendar lastMod = original.hasProperty("jcr:lastModified") ?
                original.getProperty("jcr:lastModified").getDate() :
                created;
            copied.setProperty("dav:lastModified", lastMod);
        }

        // 0.3 expects the calendar:supportedComponentSet property on
        // 0.calendar collections to be initialized
        if (original.isNodeType("caldav:collection")) {
            Value[] values = new Value[1];
            values[0] =
                copied.getSession().getValueFactory().createValue("VEVENT");
            if (log.isDebugEnabled()) {
                log.debug("setting property calendar:supportedComponentSet");
            }
            copied.setProperty("calendar:supportedComponentSet",
                               values);
        }

        long stopTime = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("copied " + copied.getPath() + " in " + formatElapsedSeconds(stopTime - startTime));
        }

        copyChildNodes(original, copied);

        return copied;
    }

    private void copyProperty(Property original,
                              Node current)
        throws RepositoryException {
        String name = translatePropertyName(original.getName());
        Value value = original.getValue();
        int type = original.getType();
        switch (type) {
        case PropertyType.BINARY:
            current.setProperty(name, value.getStream());
            break;
        case PropertyType.BOOLEAN:
            current.setProperty(name, value.getBoolean());
            break;
        case PropertyType.DATE:
            current.setProperty(name, value.getDate());
            break;
        case PropertyType.DOUBLE:
            current.setProperty(name, value.getDouble());
            break;
        case PropertyType.LONG:
            current.setProperty(name, value.getLong());
            break;
        case PropertyType.STRING:
            current.setProperty(name, value.getString());
            break;
        }
    }
    
    private void copyMultiValuedProperty(Property original,
                                         Node current)
        throws RepositoryException {
        String name = translatePropertyName(original.getName());
        ValueFactory valueFactory = current.getSession().getValueFactory();
        Value[] values = original.getValues();
        Value[] newValues = new Value[values.length];
        int type = original.getType();
        switch (type) {
        case PropertyType.BINARY:
            for (int i=0; i<values.length; i++) {
                newValues[i] = valueFactory.createValue(values[i].getStream());
            }
            break;
        case PropertyType.BOOLEAN:
            for (int i=0; i<values.length; i++) {
                newValues[i] = valueFactory.createValue(values[i].getBoolean());
            }
            break;
        case PropertyType.DATE:
            for (int i=0; i<values.length; i++) {
                newValues[i] = valueFactory.createValue(values[i].getDate());
            }
            break;
        case PropertyType.DOUBLE:
            for (int i=0; i<values.length; i++) {
                newValues[i] = valueFactory.createValue(values[i].getDouble());
            }
            break;
        case PropertyType.LONG:
            for (int i=0; i<values.length; i++) {
                newValues[i] = valueFactory.createValue(values[i].getLong());
            }
            break;
        case PropertyType.STRING:
            for (int i=0; i<values.length; i++) {
                newValues[i] = valueFactory.createValue(values[i].getString());
            }
            break;
        }
        current.setProperty(name, newValues);
    }

    private void copyChildNodes(Node original,
                                Node parent)
        throws RepositoryException {
        for (NodeIterator i=original.getNodes(); i.hasNext();) {
            Node child = i.nextNode();
            if (child.getDefinition().isProtected()) {
                continue;
            }
            copyNode(child, parent);
        }
    }

    private String translateNodeType(String original) {
        if ("mix:ticketable".equals(original)) {
            return "ticket:ticketable";
        }
        if ("caldav:collection".equals(original)) {
            return "calendar:collection";
        }
        if ("caldav:resource".equals(original)) {
            return "calendar:resource";
        }
        if ("caldav:home".equals(original)) {
            return "cosmo:homecollection";
        }
        return original;
    }

    private String translatePropertyName(String original) {
        if ("caldav:calendar-description".equals(original)) {
            return "calendar:description";
        }
        if ("caldav:uid".equals(original)) {
            return "calendar:uid";
        }
        if ("xml:lang".equals(original)) {
            return "calendar:language";
        }
        return original;
    }

    private String formatElapsedHours(long elapsedTime) {
        long seconds = elapsedTime / 1000;
        long hours = seconds / 3600;
        seconds = seconds - (hours * 3600);
        long minutes = seconds / 60;
        seconds = seconds - (minutes * 60);
        return hours + "h " +
            minutes + "m " +
            seconds + "s";
    }

    private String formatElapsedSeconds(long elapsedTime) {
        long seconds = elapsedTime / 1000;
        long milliseconds = elapsedTime - (seconds * 1000);
        String fraction = "";
        if (milliseconds < 10) {
            fraction = "00" + milliseconds;
        }
        else if (milliseconds < 100) {
            fraction = "0" + milliseconds;
        }
        else {
            fraction = "" + milliseconds;
        }
        return seconds + "." + fraction + "s";
    }
}
