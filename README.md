cosmo
=====

The OSAF mirror

## Building and deploying

1.  Checkout cosmo from https://github.com/openmash/cosmo  
 `git clone git@github.com:openmash/cosmo.git cosmo && cd cosmo`
1.  Switch to relocations branch  
 `git checkout relocations`
1.  Build cosmo with maven (tests are skipped to speed up process)  
 `mvn clean install -Dmaven.test.skip.exec=true`
1.  Copy cosmo-webapp/target/chandler.war to /opt, an important note - do not copy this file to tomcat/webapps!  
 `cp cosmo-webapp/target/chandler.war /opt`
1.  Go to tomcat home  
 `cd /opt/tomcat`
1.  Download JDBC drivers and put them into lib directory, download java.mail and place it into lib as well  
 - http://repo1.maven.org/maven2/mysql/mysql-connector-java/5.1.12/mysql-connector-java-5.1.12.jar
 - http://repo1.maven.org/maven2/com/h2database/h2/1.3.171/h2-1.3.171.jar
 - http://repo1.maven.org/maven2/javax/mail/mail/1.4.5/mail-1.4.5.jar
1.  Create directory etc in tomcat  
 `mkdir -p /opt/tomcat/etc`
1.  Inside etc place file cosmo.properties (attached in this mail)  
1.  Create directory conf/Catalina/localhost directory if missing  
1.  Copy file chandler.xml (attached) to conf/Catalina/localhost  
 - In chandler.xml you may specify context name in path attribute if you do not want to use "chandler" as path name
 - give proper location of chandler.war (/opt/chandler.war) in docBase attribute


## Setting up the database

### MySQL

+  Create user & password & database for cosmo  
+  Edit `etc/cosmo.properties`  
  uncomment line  
  `cosmo.hibernate.dialect=org.hibernate.dialect.MySQL5InnoDBDialect`  
  comment out other dialects  
+  Edit `conf/Catalina/localhost/chandler.xml`
  uncomment resource  

  `<Resource name="jdbc/cosmo" [...] />` 

+  put database access data - replace `user/password/host/database`  

### H2 (java database)

+  Edit `etc/cosmo.properties`  
  uncomment line
  `cosmo.hibernate.dialect=org.hibernate.dialect.H2Dialect`
  comment other dialects
+  Edit conf/Catalina/localhost/chandler.xml
  uncomment resource

  `<Resource name="jdbc/cosmo" [...] />`
  
+  put database access data - replace database location with disk location where database should be stored.
