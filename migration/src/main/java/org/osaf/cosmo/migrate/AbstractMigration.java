/*
 * Copyright 2005-2006 Open Source Applications Foundation
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Abstrace Migration implementation that relies on
 * SQL scripts and JDBC.  The SQL scripts contain DDL
 * statements and are responsible for migrating the
 * schema.  The abstract migrateData() method should use
 * JBDC to perform the data migration.
 * 
 * The SQL scripts are read from the classpath and
 * are expected to have a name in the form:
 * [fromVersion]-to-[toVersion]-[dialect]-pre.sql
 * [fromVersion]-to-[toVersion]-[dialect]-post.sql
 * 
 * The "pre" script is run first, then the migrateData()
 * method is called, then the "post" script is run.
 *
 */
public abstract class AbstractMigration implements Migration {

    private static final Log log = LogFactory.getLog(AbstractMigration.class);
    
    private String fromVersion = null;
    private String toVersion = null;
    
    public void migrate(Connection conn, String dialect) throws Exception {
        migrateSchema(conn, dialect);
        migrateData(conn, dialect);
        migrateSchemaCleanup(conn, dialect);
    }
    
    public String getFromVersion() {
        return fromVersion;
    }

    public void setFromVersion(String fromVersion) {
        this.fromVersion = fromVersion;
    }

    public String getToVersion() {
        return toVersion;
    }

    public void setToVersion(String toVersion) {
        this.toVersion = toVersion;
    }

    protected void migrateSchema(Connection conn, String dialect) throws Exception {
        
        log.debug("begin migrateSchema()");
        
        String resourceName = "/" + getSchemaUpdateFileName(dialect);
        InputStream is = getClass().getResourceAsStream(resourceName);
        
        if(is==null)
            throw new RuntimeException("unable to find script: " + resourceName);
        
        InputStreamReader reader = new InputStreamReader(is);
        
        BufferedReader in = new BufferedReader(reader);
        Statement stmt = conn.createStatement();
        String cmd = in.readLine();
        while(cmd != null) {
            if(cmd.length()>0 && !cmd.startsWith("#")) {
                log.debug("executing " + cmd);
                stmt.executeUpdate(cmd);
            }
            cmd = in.readLine();
        }
        stmt.close();
        log.debug("done migrateSchema()");
    }
    
    /**
     * Perform data migration.
     * @param conn database connection
     * @param dialect dialect to use
     * @throws Exception
     */
    protected abstract void migrateData(Connection conn, String dialect) throws Exception;
    
    protected void migrateSchemaCleanup(Connection conn, String dialect) throws Exception {
        
        String resourceName = "/" + getPostMigrationUpdateFileName(dialect);
        InputStream is = getClass().getResourceAsStream(resourceName);
        
        if(is==null)
            throw new RuntimeException("unable to find script: " + resourceName);
        
        InputStreamReader reader = new InputStreamReader(is);
        
        BufferedReader in = new BufferedReader(reader);
        Statement stmt = conn.createStatement();
        String cmd = in.readLine();
        while(cmd != null) {
            if(cmd.length()>0 && !cmd.startsWith("#")) {
                log.debug("executing " + cmd);
                stmt.executeUpdate(cmd);
            }
            cmd = in.readLine();
        }
        
        stmt.close();
    }
    
    private String getBaseFileName(String dialect) {
        return getFromVersion() + "-to-" + getToVersion() + "-" +
            dialect;
    }
    
    private String getSchemaUpdateFileName(String dialect) {
        return getBaseFileName(dialect) + "-pre.sql";
    }
    
    private String getPostMigrationUpdateFileName(String dialect) {
        return getBaseFileName(dialect) + "-post.sql";
    }

}
