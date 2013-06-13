cosmo
=====

The OSAF mirror

1. Checkout cosmo from https://github.com/openmash/cosmo
git clone git@github.com:openmash/cosmo.git cosmo && cd cosmo
2. Switch to relocations branch
git checkout relocations
3. Build cosmo with maven (tests are skipped to spped up process)
mvn clean install -Dmaven.test.skip.exec=true
4. Copy cosmo-webapp/target/chandler.war to /opt, an important note - do not copy this file to tomcat/webapps!
cp cosmo-webapp/target/chandler.war /opt
5. Go to tomcat home
cd /opt/tomcat
6. Download JDBC drivers and put them into lib directory, download java.mail and place it into lib as well
- http://repo1.maven.org/maven2/mysql/mysql-connector-java/5.1.12/mysql-connector-java-5.1.12.jar
- http://repo1.maven.org/maven2/com/h2database/h2/1.3.171/h2-1.3.171.jar
- http://repo1.maven.org/maven2/javax/mail/mail/1.4.5/mail-1.4.5.jar
7. Create directory etc in tomcat
mkdir -p /opt/tomcat/etc
8. Inside etc place file cosmo.properties (attached in this mail)
9. Create directory conf/Catalina/localhost directory if missing
10. Copy file chandler.xml (attached) to conf/Catalina/localhost
- In chandler.xml you may specify context name in path attribute if you do not want to use "chandler" as path name
- give proper location of chandler.war (/opt/chandler.war) in docBase attribute


Instructions for setting up the database:
1. MySQL
create user & password & database for cosmo, then edit
a) etc/cosmo.properties
> uncomment line
cosmo.hibernate.dialect=org.hibernate.dialect.MySQL5InnoDBDialect
> comment other dialects
b) conf/Catalina/localhost/chandler.xml
> uncomment resource
  <Resource name="jdbc/cosmo" type="javax.sql.DataSource" maxActive="100"
            maxIdle="30" maxWait="10000" username="[user]"
            password="[password]" defaultAutoCommit="false"
            driverClassName="com.mysql.jdbc.Driver"
   url="jdbc:mysql://[host]:3306/[database]?useUnicode=true&amp;characterEncoding=UTF-8" />
> put database access data - replace user/password/host/database

2. H2 (java database)
> uncomment line
cosmo.hibernate.dialect=org.hibernate.dialect.H2Dialect
> comment other dialects
b) conf/Catalina/localhost/chandler.xml
> uncomment resource
  <Resource name="jdbc/cosmo" type="javax.sql.DataSource" maxActive="100"
            maxIdle="30" maxWait="10000" username="sa"
            password="" defaultAutoCommit="false"
            driverClassName="org.h2.Driver"
   url="jdbc:h2:file:[database location]" />
> put database access data - replace database location with disk location where database should be stored.
