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
package org.osaf.cosmo.migrate;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osaf.cosmo.calendar.RecurrenceExpander;
import org.osaf.cosmo.calendar.util.Dates;


/**
 * Migration implementation that migrates Cosmo 0.6.1 (schema ver 100)
 * to Cosmo 0.7 (schema ver 110)
 * 
 * Supports MySQL5 and Derby dialects only.
 *
 */
public class ZeroPointSixOneToZeroPointSevenMigration extends AbstractMigration {
    
    private static final Log log = LogFactory.getLog(ZeroPointSixOneToZeroPointSevenMigration.class);
    
    static {
        // use custom timezone registry
        System.setProperty("net.fortuna.ical4j.timezone.registry", "org.osaf.cosmo.calendar.CosmoTimeZoneRegistryFactory");
    }
    
    @Override
    public String getFromVersion() {
        return "100";
    }

    @Override
    public String getToVersion() {
        // switching to different schema version format
        return "110";
    }

    
    @Override
    public List<String> getSupportedDialects() {
        ArrayList<String> dialects = new ArrayList<String>();
        dialects.add("Derby");
        dialects.add("MySQL5");
        return dialects;
    }

    public void migrateData(Connection conn, String dialect) throws Exception {
        
        log.debug("starting migrateData()");
        migrateSubscriptions(conn);
        migrateTimeRangeIndexes(conn);
       
    }
    
    /**
     * Add createdate,modifydate to each subscription.
     */
    private void migrateSubscriptions(Connection conn) throws Exception {
        
        PreparedStatement updateStmt = null;
        long count = 0;
        log.debug("starting migrateSubscriptions()");
        
        try {
            updateStmt = conn.prepareStatement("update subscription set createdate=?, modifydate=?");
            long currentTime = System.currentTimeMillis();
            updateStmt.setLong(1, currentTime);
            updateStmt.setLong(2, currentTime);
            count = updateStmt.executeUpdate();
        } finally {
            if(updateStmt!=null)
                updateStmt.close();
        }
        
        log.debug("processed " + count + " subscriptions");
    }
    
    /**
     * Calculate time-range index for each event stamp row.
     */
    private void migrateTimeRangeIndexes(Connection conn) throws Exception {
        
        PreparedStatement stmt = null;
        PreparedStatement updateStmt = null;
        PreparedStatement selectMasterCalStmt = null;
        
        ResultSet rs = null;
        
        long count = 0;
        
        System.setProperty("ical4j.unfolding.relaxed", "true");
        CalendarBuilder calBuilder = new CalendarBuilder();
        
        log.debug("starting migrateTimeRangeIndexes()");
        
        try {
            stmt = conn.prepareStatement("select i.modifiesitemid, s.id, es.icaldata from item i, stamp s, event_stamp es where i.id=s.itemid and s.id=es.stampid");
            updateStmt = conn.prepareStatement("update event_stamp set isfloating=?, startdate=?, enddate=? where stampid=?");
            selectMasterCalStmt = conn.prepareStatement("select es.icaldata from item i, stamp s, event_stamp es where i.id=? and i.id=s.itemid and s.id=es.stampid");
            
            rs = stmt.executeQuery();
            
            while(rs.next()) {
                long modifiesItemId = rs.getLong(1);
                long eventId = rs.getLong(2);
                String icalData = rs.getString(3);
                
                Calendar calendar = null;
                Calendar masterCalendar = null;
                
                try {
                    calendar = calBuilder.build(new StringReader(icalData));
                } catch (ParserException e) {
                    throw e;
                }
                
                // Get master calendar if event is a modification
                if(modifiesItemId!=0) {
                    selectMasterCalStmt.setLong(1, modifiesItemId);
                    ResultSet masterCalRs = selectMasterCalStmt.executeQuery();
                    masterCalRs.next();
                    try {
                        masterCalendar  = calBuilder.build(new StringReader(masterCalRs.getString(1)));
                    } catch (ParserException e) {
                        throw e;
                    }
                    masterCalRs.close();
                }
                
                Object[] indexes = getIndexValues(calendar, masterCalendar);
                updateStmt.setBoolean(1, (Boolean) indexes[2]);
                updateStmt.setString(2, (String) indexes[0]);
                updateStmt.setString(3, (String) indexes[1]);
                updateStmt.setLong(4, eventId);
                
                updateStmt.executeUpdate();
                count++;
            }
            
            
        } finally {
            if(stmt!=null)
                stmt.close();
            if(updateStmt!=null)
                updateStmt.close();
            if(selectMasterCalStmt!=null)
                selectMasterCalStmt.close();
        }
        
        log.debug("processed " + count + " event stamps");
    }
    
    
    /**
     * Calculate time-range index from Calendar.
     * Return Object[] consisting of:
     *
     * Object[0] = startDate index
     * Object[1] = endDate index
     * Object[2] = isFloating index
     */
    private Object[] getIndexValues(Calendar calendar, Calendar masterCalendar) {
        ComponentList events = calendar.getComponents().getComponents(
                Component.VEVENT);
        VEvent event = (VEvent) events.get(0);
        
        Date startDate = getStartDate(event);
        Date endDate = getEndDate(event);
        
        // Handle "missing" endDate
        if(endDate==null && masterCalendar != null) {
            // For "missing" endDate, get the duration of the master event
            // and use with the startDate of the modification to calculate
            // the endDate of the modificaiton
            ComponentList masterEvents = calendar.getComponents().getComponents(
                    Component.VEVENT);
            VEvent masterEvent = (VEvent) masterEvents.get(0);
            Dur duration = getDuration(masterEvent);
            if(duration!=null)
                endDate = Dates.getInstance(duration.getTime(startDate), startDate);
        }
        
        
        if (isRecurring(event)) {
            RecurrenceExpander expander = new RecurrenceExpander();
            Date[] range = expander
                    .calculateRecurrenceRange(calendar);
            startDate = range[0];
            endDate = range[1];
        } else {
            // If there is no end date, then its a point-in-time event
            if (endDate == null)
                endDate = startDate;
        }
        
        boolean isFloating = false;
        
        // must have start date
        if(startDate==null)
            throw new RuntimeException("event must have start date");
        
        // A floating date is a DateTime with no timezone
        if(startDate instanceof DateTime) {
            DateTime dtStart = (DateTime) startDate;
            if(dtStart.getTimeZone()==null && !dtStart.isUtc())
                isFloating = true;
        }
        
        Object[] timeRangeIndex = new Object[3];
        timeRangeIndex[0] = fromDateToStringNoTimezone(startDate);
        
        
        // A null endDate equates to infinity, which is represented by
        // a String that will always come after any date when compared.
        if(endDate!=null)
            timeRangeIndex[1] = fromDateToStringNoTimezone(endDate);
        else
            timeRangeIndex[1] = "Z-TIME-INFINITY";
        
        timeRangeIndex[2] = new Boolean(isFloating);
        
        return timeRangeIndex;
    }
    
    
    /**
     * Get endDate from VEVENT
     */
    private Date getEndDate(VEvent event) {
        DtEnd dtEnd = event.getEndDate(false);
        // if no DTEND, then calculate endDate from DURATION
        if (dtEnd == null) {
            Date startDate = getStartDate(event);
            Dur duration = getDuration(event);
            
            // if no DURATION, then there is no end time
            if(duration==null)
                return null;
            
            Date endDate = null;
            if(startDate instanceof DateTime)
                endDate = new DateTime(startDate);
            else
                endDate = new Date(startDate);
            
            endDate.setTime(duration.getTime(startDate).getTime());
            return endDate;
        }
            
        return dtEnd.getDate();
    }
    
    /**
     * Get startDate from VEVENT
     */
    private Date getStartDate(VEvent event) {
        DtStart dtStart = event.getStartDate();
        if (dtStart == null)
            return null;
        return dtStart.getDate();
    }
    
    /**
     * Get duration from VEVENT
     */
    private Dur getDuration(VEvent event) {
        Duration duration = (Duration)
        event.getProperties().getProperty(Property.DURATION);
        if (duration != null)
            return duration.getDuration();
        else
            return null;
    }
    
    /**
     * Get RRULEs from VEVENT
     */
    private List<Recur> getRecurrenceRules(VEvent event) {
        ArrayList<Recur> l = new ArrayList<Recur>();
        if(event!=null) {
            for (RRule rrule : (List<RRule>) event.getProperties().
                     getProperties(Property.RRULE))
                l.add(rrule.getRecur());
        }
        return l;
    }
    
    /**
     * Get RDATEs from VEVENT
     */
    public DateList getRecurrenceDates(VEvent event) {
        
        DateList l = null;
        
        if(event==null)
            return null;
        
        for (RDate rdate : (List<RDate>) event.getProperties().
                 getProperties(Property.RDATE)) {
            if(l==null) {
                if(Value.DATE.equals(rdate.getParameter(Parameter.VALUE)))
                    l = new DateList(Value.DATE);
                else
                    l = new DateList(Value.DATE_TIME);
            }
            l.addAll(rdate.getDates());
        }
            
        return l;
    }
    
    /**
     * Determine if VEVENT is recurring
     */
    private boolean isRecurring(VEvent event) {
        if(getRecurrenceRules(event).size()>0)
            return true;
        
        DateList rdates = getRecurrenceDates(event);
        
        return (rdates!=null && rdates.size()>0);
    }
    
    private String fromDateToStringNoTimezone(Date date) {
        if(date==null)
            return null;
        
        if(date instanceof DateTime) {
            DateTime dt = (DateTime) date;
            // If DateTime has a timezone, then convert to UTC before
            // serializing as String.
            if(dt.getTimeZone()!=null) {
                // clone instance first to prevent changes to original instance
                DateTime copy = new DateTime(dt);
                copy.setUtc(true);
                return copy.toString();
            } else {
                return dt.toString();
            }
        } else {
            return date.toString();
        }
    }

}