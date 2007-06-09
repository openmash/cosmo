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

/**
 * A module that provides translators from data received from a
 * JSON-RPC service to cosmo.model.Object objects.
 */
dojo.provide("cosmo.service.translators.eim");

dojo.require("dojo.date.serialize");
dojo.require("dojo.lang.*");
dojo.require("dojo.json");
dojo.require("dojo.string");

dojo.require("cosmo.service.eim");
dojo.require("cosmo.model.*");
dojo.require("cosmo.service.translators.common");
dojo.require("cosmo.datetime.serialize");
dojo.require("cosmo.util.html");

dojo.declare("cosmo.service.translators.Eim", null, {
    
    initializer: function (){
        with (this.rruleConstants) {
        with (cosmo.model.RRULE_FREQUENCIES){
            this.rruleFrequenciesToRruleConstants = {};
            this.rruleFrequenciesToRruleConstants[FREQUENCY_DAILY] = DAILY;
            this.rruleFrequenciesToRruleConstants[FREQUENCY_WEEKLY] = WEEKLY;
            this.rruleFrequenciesToRruleConstants[FREQUENCY_BIWEEKLY] = WEEKLY + ";INTERVAL=2";
            this.rruleFrequenciesToRruleConstants[FREQUENCY_MONTHLY] = MONTHLY;
            this.rruleFrequenciesToRruleConstants[FREQUENCY_YEARLY] = YEARLY;
        }}
    },
    
    // a hash from link rels to useful url names
    urlNameHash: {
        "edit": "atom-edit"
    },
    
    getUrls: function (xml){
            var urls = {};//item.getUrls();
            var links = cosmo.util.html.getElementsByTagName(xml, "link");
            for (var i = 0; i < links.length; i++){
                var link = links[i];
                var rel = link.getAttribute("rel");
                var href = link.getAttribute("href");
                urls[this.urlNameHash[rel] || rel] = href;
            }
            return urls;
    },
    
    RID_FMT: "%Y%m%dT%H%M%S",

    // Wrap each of the specified property's getter with a function
    // that will call the lazy loader and then revert each
    // getter.
    setLazyLoader: function (object, propertyNames, loaderFunction){
        var oldGetters = {};
        for (var i = 0; i < propertyNames.length; i++) {
            var propertyName = propertyNames[i];
            var getterName = "get" + dojo.string.capitalize(propertyName);
            var oldGetter = object[getterName];
            oldGetters[getterName] = oldGetter;
            object[getterName] = 
                function(){
                    loaderFunction(object, propertyNames);
                    for (var oldGetterName in oldGetters){
                        object[oldGetterName] = oldGetters[oldGetterName];
                    }
                    return object[getterName]();
                }  
        }
    },
    
    translateGetCollection: function (atomXml, oldCollection){
        var ticketKey;
        var ticketElement = cosmo.util.html.getElementsByTagName(atomXml, "cosmo", "ticket")[0];
        if (ticketElement) ticketKey = ticketElement.firstChild.nodeValue;
        var uid = atomXml.getElementsByTagName("id")[0].firstChild.nodeValue.substring(9);
        var displayName = cosmo.util.html.getElementsByTagName(atomXml, "title")[0].firstChild.nodeValue;
        var collection = oldCollection || new cosmo.model.Collection();
        collection.setUid(uid);
        collection.setDisplayName(displayName);
        collection.setUrls(this.getUrls(atomXml));

        if (ticketKey) collection.setTicketKey(uid);

        return collection;
    },
          
    translateGetCollections: function (atomXml, kwArgs){
        var workspaces = atomXml.getElementsByTagName("workspace");
        var collections = [];
        for (var i = 0; i < workspaces.length; i++){
            var workspace = workspaces[i];
            
            var title = cosmo.util.html.getElementsByTagName(workspace, "atom", "title")[0];

            if (title.firstChild.nodeValue == "meta") continue;

            var collectionElements = workspace.getElementsByTagName("collection");
            
            for (var j = 0; j < collectionElements.length; j++){
                var collection = this.collectionXmlToCollection(collectionElements[j]);
                this.setLazyLoader(collection, ["urls"], kwArgs.lazyLoader);
                collections.push(collection);
            }
        }
        return collections;
    },
    
    translateGetSubscriptions: function (atomXml, kwArgs){
        var entries = atomXml.getElementsByTagName("entry");
        var subscriptions = [];
        for (var i = 0; i < entries.length; i++){
            var entry = entries[i];
            
            var displayNameEl = cosmo.util.html.getElementsByTagName(entry, "title")[0];
            var displayName = displayNameEl.firstChild.nodeValue;
            var ticketEl = cosmo.util.html.getElementsByTagName(entry, "cosmo", "ticket")[0];
            var ticket = ticketEl.firstChild.nodeValue;
            var uidEl = cosmo.util.html.getElementsByTagName(entry, "cosmo", "collection")[0];
            var uid = uidEl.firstChild.nodeValue;
            var subscription = new cosmo.model.Subscription({
                displayName: displayName,
                tickeyKey: ticket,
                uid: uid
            })
            this.setLazyLoader(subscription, ["urls"], kwArgs.lazyLoader);
          
        }
        return subscriptions;
    },
    
    collectionXmlToCollection: function (collectionXml){
        return collection = new cosmo.model.Collection(
            {
                //TODO: replace this with the correct uid grabbing code
                uid: collectionXml.getAttribute("href").split("/")[1],
                displayName: cosmo.util.html.getElementsByTagName(collectionXml, "atom", "title")
                    [0].firstChild.nodeValue
            }
        );
    },
    
    translateExpandRecurringItem: function(atomXml){
        if (!atomXml){
            throw new cosmo.service.translators.ParseError("Cannot parse null, undefined, or false");
        }
        var entry = atomXml.getElementsByTagName("entry")[0];
        try {
            var contentEl = entry.getElementsByTagName("content")[0];
        } catch (e){
            throw new cosmo.service.translators.
               ParseError("Could not find content element for entry " + (i+1));
        }
        var content = cosmo.util.html.getElementTextContent(contentEl);
        
        var recordSets = dojo.json.evalJson(content);

        var masterItem = this.recordSetToObject(recordSets[0]);
        masterItem.setUrls(this.getUrls(entry));

        var items = [];
        // All record sets after the first are occurrences
        for (var i = 1; i < recordSets.length; i++){
           var item = this.recordSetToModification(recordSets[i], masterItem)
           //TODO: remove this hack to get edit urls into modifications
           if (item.hasModification()){
              item.setUrls({"atom-edit": "item/" + this.getUid(item)});
           }
           items.push(item); 
        }
        return items;
    },
    
    translateGetItem: function(atomXml, kwArgs){
        if (!atomXml){
            throw new cosmo.service.translators.ParseError("Cannot parse null, undefined, or false");
        }
        
        var entry = atomXml.getElementsByTagName("entry")[0];
        return this.entryToItem(entry, null, kwArgs);
          
    },
    
    translateSaveCreateItem: function(atomXml, kwArgs){
        kwArgs = kwArgs || {};
        if (!atomXml){
            throw new cosmo.service.translators.ParseError("Cannot parse null, undefined, or false");
        }
        
        var entry = atomXml.getElementsByTagName("entry")[0];
        return this.entryToItem(entry, kwArgs.masterItem, kwArgs);
          
    },
    
    translateGetItems: function (atomXml){
        if (!atomXml){
            throw new cosmo.service.translators.ParseError("Cannot parse null, undefined, or false");
        }
        var entries = atomXml.getElementsByTagName("entry");
        var items = {};
        var mods = {};
        for (var i = 0; i < entries.length; i++){
            var entry = entries[i];
            var uuid = this.getEntryUuid(entry);
            if (!uuid.split(":")[1]){
                items[uuid] = this.entryToItem(entry)
            }
            else {
                mods[uuid] = entry;
            }
        }
        
        // Remove the master events at the end, cause they're returned as occurrences
        var masterRemoveList = {};

        for (var uuid in mods){
            var masterUuid = uuid.split(":")[0];
            var masterItem = items[uuid.split(":")[0]];
            if (!masterItem) throw new cosmo.service.translators.ParseError(
              "Could not find master event for modification " +
              "with uuid " + uuid);

            items[uuid] = this.entryToItem(mods[uuid], masterItem);
            masterRemoveList[masterUuid] = true;
        }
        for (var uuid in masterRemoveList){
            delete items[uuid];
        }
        
        var itemArray = [];
        for (var uid in items){
            itemArray.push(items[uid]);
        }
        return itemArray;
    },
    
    entryToItem: function (/*XMLElement*/entry, /*cosmo.model.Item*/ masterItem, kwArgs){
            var uuid = this.getEntryUuid(entry);
            var uuidParts = uuid.split(":");
            var uidParts = uuidParts.slice(2);
            try {
                var contentElement = entry.getElementsByTagName("content")[0];
            } catch (e){
                throw new cosmo.service.translators.
                   ParseError("Could not find content element for entry " + (i+1));
            }

            var content = cosmo.util.html.getElementTextContent(contentElement);

            var item;
            // If we have a second part to the uid, this entry is a
            // recurrence modification.
            if (masterItem){
                item = this.recordSetToModification(dojo.json.evalJson(content), masterItem, kwArgs); 
            }
            else {
                item = this.recordSetToObject(dojo.json.evalJson(content), kwArgs);
            }
            if (item.isMaster() || (item.isOccurrence() && item.hasModification())){
                item.setUrls(this.getUrls(entry));
            }
            return item;
    },
    
    recordSetToObject: function (/*Object*/ recordSet, kwArgs){
        kwArgs = kwArgs || {};
        //TODO
        /* We can probably optimize this by grabbing the
         * appropriate properties from the appropriate records
         * and passing them into the constructor. This will probably
         * be a little less elegant, and will require the creation of
         * more local variables, so we should play with this later.
         */
        var note = kwArgs.oldObject || new cosmo.model.Note(
         {
             uid: recordSet.uuid
         }
        );
        for (recordName in recordSet.records){
        with (cosmo.service.eim.constants){

           var record = recordSet.records[recordName]

           switch(recordName){

           case prefix.ITEM:
               note.initializeProperties(this.itemRecordToItemProps(record), {noDefaults: true})
               break;
           case prefix.NOTE:
               note.initializeProperties(this.noteRecordToNoteProps(record), {noDefaults: true})
               break;
           case prefix.EVENT:
               note.getStamp(prefix.EVENT, true, this.getEventStampProperties(record));
               break;
           case prefix.TASK:
              note.getStamp(prefix.TASK, true, this.getTaskStampProperties(record));
               break;
           case prefix.MAIL:
              note.getStamp(prefix.MAIL, true, this.getMailStampProperties(record));
              break;
           }
        }

        }
        return note;

    },
    
    /*
     * 
     */
    recordSetToModification: function (recordSet, masterItem, kwArgs){
        kwArgs = kwArgs || {};
        var uidParts = recordSet.uuid.split(":");
        
        var modifiedProperties = {};
        var modifiedStamps = {};

        for (recordName in recordSet.records){
            with (cosmo.service.eim.constants){
               var record = recordSet.records[recordName];
    
               switch(recordName){
    
               case prefix.ITEM:
                   dojo.lang.mixin(modifiedProperties, this.itemRecordToItemProps(record));
                   break;
               case prefix.NOTE:
                    dojo.lang.mixin(modifiedProperties, this.noteRecordToNoteProps(record));
                   break;
               case prefix.EVENT:
                   modifiedStamps[prefix.EVENT] = this.getEventStampProperties(record);
                   break;
               case prefix.TASK:
                   modifiedStamps[prefix.TASK] = this.getTaskStampProperties(record);
                   break;
               case prefix.MAIL:
                   modifiedStamps[prefix.MAIL] = this.getMailStampProperties(record);
                   break;
               }
            }
        }
        var recurrenceId = this.recurrenceIdToDate(uidParts[1]);
        
        if (!dojo.lang.isEmpty(modifiedProperties)
            || !dojo.lang.isEmpty(modifiedStamps)){
            var mod = new cosmo.model.Modification(
                {
                    "recurrenceId": recurrenceId,
                    "modifiedProperties": modifiedProperties,
                    "modifiedStamps": modifiedStamps
                }
            );
            masterItem.addModification(mod);
        }
        
        return kwArgs.oldObject || masterItem.getNoteOccurrence(recurrenceId);
    },
    
    recurrenceIdToDate: function (/*String*/ rid){
         return cosmo.datetime.fromIso8601(rid);
    },

    subscriptionToAtomEntry: function (subscription){
         return '<entry xmlns="http://www.w3.org/2005/Atom" xmlns:cosmo="http://osafoundation.org/cosmo/Atom">' +
         '<title>' + subscription.getDisplayName() + '</title>' +
         '<updated>' + dojo.date.toRfc3339(new Date()) + '</updated>' +
         '<author><name>' + cosmo.util.auth.getUsername() + '</name></author>' +
         '<cosmo:ticket>' + subscription.getTicketKey() + '</cosmo:ticket>' +
         '<cosmo:collection>' + subscription.getUid() + '</cosmo:collection>' +
         '</entry>'
    },

    itemToAtomEntry: function (object){
         var jsonObject = this.objectToRecordSet(object);
         return '<entry xmlns="http://www.w3.org/2005/Atom">' +
         '<title>' + object.getDisplayName() + '</title>' +
         '<id>urn:uuid:' + this.getUid(object) + '</id>' +
         '<updated>' + dojo.date.toRfc3339(new Date()) + '</updated>' +
         '<author><name>' + cosmo.util.auth.getUsername() + '</name></author>' +
         '<content type="application/eim+json">' + dojo.json.serialize(jsonObject) + '</content>' +
         '</entry>'
    },
    
    
    getUid: function (/*cosmo.model.Note*/ note){
        if (note instanceof cosmo.model.NoteOccurrence){
            return note.getUid() + ":" + note.recurrenceId.strftime(this.RID_FMT);
        } else {
            return note.getUid();
        }
    },

    objectToRecordSet: function (note){
        if (note instanceof cosmo.model.NoteOccurrence){
            return this.noteOccurrenceToRecordSet(note);
        } else if (note instanceof cosmo.model.Note){
            return this.noteToRecordSet(note);
        } else {
            throw new cosmo.service.translators.exception.ModelToRecordSetException(
                "note is neither a Note nor a NoteOccurrence, don't know how to translate."
            )
        }
    },
    
    noteToRecordSet: function(note){
        var records = {
            item: this.noteToItemRecord(note),
            note: this.noteToNoteRecord(note),
            modby: this.noteToModbyRecord(note)
        };

        if (note.getEventStamp()) records.event = this.noteToEventRecord(note);
        if (note.getTaskStamp()) records.task = this.noteToTaskRecord(note);
        if (note.getMailStamp()) records.mail = this.noteToMailRecord(note);
        
        var recordSet =  {
            uuid: this.getUid(note),
            records: records
        };
        var deletedStamps = note.getDeletedStamps();
        if (deletedStamps.length > 0){
            recordSet.deletedRecords = deletedStamps;
        }
        
        return recordSet;
    },

    noteOccurrenceToRecordSet: function(noteOccurrence){
        var modification = noteOccurrence.getMaster().getModification(noteOccurrence.recurrenceId);
        var records = {
            modby: this.noteToModbyRecord(noteOccurrence)
        }
        if (this.modificationHasItemModifications(modification))  
            records.item =  this.modifiedOccurrenceToItemRecord(noteOccurrence);
        if (this.modificationHasNoteModifications(modification))
            records.note = this.modifiedOccurrenceToNoteRecord(noteOccurrence);
        
        records.event = this.modifiedOccurrenceToEventRecord(noteOccurrence)
        
        if (modification.getModifiedStamps().task){
            records.task = this.modificationToTaskRecord(modification)
        }
        if (modification.getModifiedStamps().mail){
            records.mail = this.modifiedOccurrenceToMailRecord(noteOccurrence)
        }
        var recordSet =  {
            uuid: this.getUid(noteOccurrence),
            records: records
        };
        
        return recordSet;
        
    },
    
    modificationHasItemModifications: function (modification){
        var props = modification.getModifiedProperties();
        return (props.displayName || props.triageRank || props.triageStatus || props.autoTriage)    
    },

    noteToItemRecord: function(note){
        var props = {};
        props.displayName = note.getDisplayName();
        props.triageRank = note.getRank();
        props.triageStatus = note.getTriageStatus();
        props.autoTriage = note.getAutoTriage();
        props.uuid = this.getUid(note);
        return this.propsToItemRecord(props);
    },
    
    modifiedOccurrenceToItemRecord: function(modifiedOccurrence){
        var modification = modifiedOccurrence.getMaster().getModification(modifiedOccurrence.recurrenceId)
        var props = modification.getModifiedProperties();
        props.uuid = this.getUid(modifiedOccurrence);
        var record = this.propsToItemRecord(props);
        var missingFields = [];
        if (record.fields.title == undefined) missingFields.push("title");
        if (record.fields.triage == undefined) missingFields.push("triage");
        record.missingFields = missingFields;
        return record;
    },
    
    propsToItemRecord: function(props){
        var fields = {};
        with (cosmo.service.eim.constants){
        
            if (props.displayName != undefined) fields.title = [type.TEXT, props.displayName];
            if (props.triageStatus || props.triageRank || props.autoTriage)
                fields.triage =  [type.TEXT, [props.triageStatus, this.fixTriageRank(props.triageRank), props.autoTriage? 1 : 0].join(" ")];
            
            return {
                prefix: prefix.ITEM,
                ns: ns.ITEM,
                key: {
                    uuid: [type.TEXT, props.uuid]
                },
                fields: fields
            }
        }
    
    },
    
    // Make sure triage rank ends in two decimals
    fixTriageRank: function(rank){
        if (rank.toString().match(/d*\.\d\d/)) return rank;
        else return rank + ".00";
    },

    noteToNoteRecord: function(note){
        var props = {}
        props.body = note.getBody();
        props.uuid = this.getUid(note);
        return this.propsToNoteRecord(props);
    },
    
    modificationHasNoteModifications: function (modification){
        return !!modification.getModifiedProperties().body;
    },
    
    
    modifiedOccurrenceToNoteRecord: function(modifiedOccurrence){
        var modification = noteOccurrence.getMaster().getModification(noteOccurrence.recurrenceId)
        var props = modification.getModifiedProperties();
        props.uuid = this.getUid(modifiedOccurrence);
        var record = this.propsToNoteRecord(props);
        var missingFields = [];
        if (record.fields.body == undefined) missingFields.push("body");
        record.missingFields = missingFields;
        return record;
    },
    
    propsToNoteRecord: function(props){
        with (cosmo.service.eim.constants){
            var fields = {};
            if (props.body != undefined) fields.body = [type.CLOB, props.body];
            return {
                prefix: prefix.NOTE,
                ns: ns.NOTE,
                key: {
                    uuid: [type.TEXT, props.uuid]
                },
                fields: fields
            }
        }
    },

    noteToMailRecord: function(note){
        var props = {};
        stamp = note.getMailStamp();
        props.messageId = stamp.getMessageId();
        props.headers = stamp.getHeaders();
        props.fromAddress = stamp.getFromAddress();
        props.toAddress = stamp.getToAddress();
        props.ccAddress = stamp.getCcAddress();
        props.bccAddress = stamp.getBccAddress();
        props.originators = stamp.getOriginators();
        props.dateSent = stamp.getDateSent();
        props.inReplyTo = stamp.getInReplyTo();
        props.references = stamp.getReferences();
        props.uuid = this.getUid(note);
        return this.propsToMailRecord(props);

    },

    modifiedOccurrenceToMailRecord: function(modifiedOccurrence){
        var modification = modifiedOccurrence.getMaster().getModification(modifiedOccurrence.recurrenceId);
        var props = modification.getModifiedStamps().mail || {};
        props.uuid = modifiedOccurrence.getUid();
        var record = this.propsToMailRecord(props);
        var missingFields = [];
        if (record.fields.messageId == undefined) missingFields.push("messageId");
        if (record.fields.headers == undefined) missingFields.push("headers");
        if (record.fields.fromAddress == undefined) missingFields.push("fromAddress");
        if (record.fields.toAddress == undefined) missingFields.push("toAddress");
        if (record.fields.ccAddress == undefined) missingFields.push("ccAddress");
        if (record.fields.bccAddress == undefined) missingFields.push("bccAddress");
        if (record.fields.originators == undefined) missingFields.push("originators");
        if (record.fields.dateSent == undefined) missingFields.push("dateSent");
        if (record.fields.inReplyTo == undefined) missingFields.push("inReplyTo");
        if (record.fields.references == undefined) missingFields.push("references");
        record.missingFields = missingFields;
        return record;
    },
    
    propsToMailRecord: function(props){
        with (cosmo.service.eim.constants){
            var fields = {};
            var missingFields = [];
            if (props.messageId != undefined) fields.messageId = [type.TEXT, props.messageId];
            if (props.headers != undefined) fields.headers = [type.CLOB, props.headers];
            if (props.fromAddress != undefined) fields.fromAddress = [type.TEXT, props.fromAddress.join(",")];
            if (props.toAddress != undefined) fields.toAddress = [type.TEXT, props.toAddress.join(",")];
            if (props.ccAddress != undefined) fields.ccAddress = [type.TEXT, props.ccAddress.join(",")];
            if (props.bccAddress != undefined) fields.bccAddress = [type.TEXT, props.bccAddress.join(",")];
            if (props.originators != undefined) fields.originators = [type.TEXT, props.originators.join(",")];
            if (props.dateSent != undefined) fields.dateSent = [type.TEXT, props.dateSent];
            if (props.inReplyTo != undefined) fields.inReplyTo = [type.TEXT, props.inReplyTo];
            if (props.references != undefined) fields.references = [type.CLOB, props.references];
            
            return record = {
                prefix: prefix.MAIL,
                ns: ns.MAIL,
                key: {
                    uuid: [type.TEXT, props.uuid]
                },
                fields: fields
            }
            return record;
        }   
    },
    
    noteToEventRecord: function(note){
        var props = {};
        stamp = note.getEventStamp();
        props.allDay = stamp.getAllDay();
        props.anyTime = stamp.getAnyTime();
        props.startDate = stamp.getStartDate();
        props.rrule = stamp.getRrule();
        props.status = stamp.getStatus();
        props.location = stamp.getLocation();
        props.duration = stamp.getDuration();
        props.exdates = stamp.getExdates();
        props.uuid = this.getUid(note);
        return this.propsToEventRecord(props);

    },

    modifiedOccurrenceToEventRecord: function(modifiedOccurrence){
        var modification = modifiedOccurrence.getMaster().getModification(modifiedOccurrence.recurrenceId);
        var props = modification.getModifiedStamps().event || {};
        props.uuid = modifiedOccurrence.getUid();
        var record = this.propsToEventRecord(props);
        var missingFields = [];
        if (record.fields.dtstart == undefined) missingFields.push("dtstart");
        if (record.fields.status == undefined) missingFields.push("status");
        if (record.fields.location == undefined) missingFields.push("location");
        if (record.fields.duration == undefined) missingFields.push("duration");
        record.missingFields = missingFields;
        return record;
    },
    
    propsToEventRecord: function(props){
        with (cosmo.service.eim.constants){
            var fields = {};
            if (props.startDate != undefined) fields.dtstart = 
                [type.TEXT, this.dateToEimDtstart(props.startDate, props.allDay, props.anyTime)];
            if (props.status != undefined) fields.status = [type.TEXT, props.status];
            if (props.location != undefined) fields.location = [type.TEXT, props.location];
            if (props.duration != undefined) fields.duration = [type.TEXT, props.duration.toIso8601()];
            if (props.rrule != undefined) fields.rrule = [type.TEXT, this.rruleToICal(props.rrule)];
            if (props.exdates != undefined) fields.exdates = [type.TEXT, this.exdatesToEim(props.exdates)];
            
            return record = {
                prefix: prefix.EVENT,
                ns: ns.EVENT,
                key: {
                    uuid: [type.TEXT, props.uuid]
                },
                fields: fields
            }
        }

        
    },

    exdatesToEim: function(exdates){
        return ";VALUE=DATE-TIME:" + dojo.lang.map(
                exdates,
                function(date){return date.strftime("%Y%m%dT%H%M%S");}
            ).join(",");
    },
    
    dateToEimDtstart: function (start, allDay, anyTime){
        return [(anyTime? ";X-OSAF-ANYTIME=TRUE" : ""),
                ";VALUE=",
                ((allDay || anyTime)? "DATE" : "DATE-TIME"),
                ":",
                ((allDay || anyTime)?
                    start.strftime("%Y%m%d"):
                    start.strftime("%Y%m%dT%H%M%S"))
                ].join("");
        
    },

    noteToTaskRecord: function (note){

        var stamp = note.getTaskStamp();

        with (cosmo.service.eim.constants){
            return {
                prefix: prefix.TASK,
                ns: ns.TASK,
                key: {
                    uuid: [type.TEXT, this.getUid(note)]
                },
                fields: {}

            }
        }

    },

    noteToModbyRecord: function(note){
        with (cosmo.service.eim.constants){
            return {
                prefix: prefix.MODBY,
                ns: ns.MODBY,
                key:{
                    uuid: [type.TEXT, this.getUid(note)],
                    userid: [type.TEXT, ""],
                    action: [type.INTEGER, 100], //TODO: figure this out
                    timestamp: [type.DECIMAL, new Date().getTime()]
                }
            }
        }
    },

    getEventStampProperties: function (record){

        var properties = {};
        if (record.fields){
            if (record.fields.dtstart){
                properties.startDate = this.fromEimDate(record.fields.dtstart[1]);
                var dateParams = this.dateParamsFromEimDate(record.fields.dtstart[1]);
                if (dateParams.anyTime != undefined) properties.anyTime = dateParams.anyTime;
                if (dateParams.allDay != undefined) properties.allDay = dateParams.allDay;
            }
    
            if (record.fields.duration) properties.duration=
                    new cosmo.model.Duration(record.fields.duration[1]);
            if (record.fields.location) properties.location = record.fields.location[1];
            if (record.fields.rrule) properties.rrule = this.parseRRule(record.fields.rrule[1]);
            if (record.fields.exrule) properties.exrule = this.parseRRule(record.fields.exrule[1]);
            if (record.fields.exdate) properties.exdates = this.parseExdate(record.fields.exdate[1]);
            if (record.fields.status) properties.status = record.fields.status[1];
        }
        return properties;

    },

    getTaskStampProperties: function (record){

        return {};
    },

    getMailStampProperties: function (record){
        var properties = {};
        if (record.fields){
            if (record.fields.messageId) properties.messageId = record.fields.messageId[1];
            if (record.fields.headers) properties.headers = record.fields.headers[1];
            if (record.fields.fromAddress) properties.fromAddress = this.parseList(record.fields.fromAddress[1]);
            if (record.fields.toAddress) properties.toAddress = this.parseList(record.fields.toAddress[1]);
            if (record.fields.ccAddress) properties.ccAddress = this.parseList(record.fields.ccAddress[1]);
            if (record.fields.bccAddress) properties.bccAddress = this.parseList(record.fields.bccAddress[1]);
            if (record.fields.originators) properties.originators = this.parseList(record.fields.originators[1]);
            if (record.fields.dateSent) properties.dateSent = record.fields.dateSent[1]; //TODO: parse
            if (record.fields.inReplyTo) properties.inReplyTo = record.fields.inReplyTo[1];
            if (record.fields.references) properties.references = record.fields.references[1];
        }
        return properties;
    },
    
    parseList: function(listString){
       if (!listString) return listString;
       else return listString.split(",");
    },
    
    itemRecordToItemProps: function(record){
        var props = {};
        if (record.fields){
            if (record.fields.title) props.displayName = record.fields.title[1];
            if (record.fields.createdOn) props.creationDate = record.fields.createdOn[1];
            if (record.fields.triage) this.addTriageStringToItemProps(record.fields.triage[1], props);
        }
        return props
    },

    noteRecordToNoteProps: function(record){
        var props = {};
        if (record.fields){
            if (record.fields.body) props.body = record.fields.body[1];
        }
        return props
    },

    fromEimDate: function (dateString){
        var date = dateString.split(":")[1];
        return cosmo.datetime.fromIso8601(date);
    },

    addTriageStringToItemProps: function (triageString, props){
        var triageArray = triageString.split(" ");

        props.triageStatus = triageArray[0];

        props.rank = triageArray[1];

        /* This looks weird, but because of JS's weird casting stuff, it's necessary.
         * Try it if you don't believe me :) - travis@osafoundation.org
         */
        props.autoTriage = triageArray[2] == true;
    },

    dateParamsFromEimDate: function (dateString){
        var returnVal = {};
        var params = dateString.split(":")[0].split(";");
        for (var i = 0; i < params.length; i++){
            var param = params[i].split("=");
            if (param[0].toLowerCase() == "x-osaf-anytime") {
                returnVal.anyTime = true;
            }
            if (param[0].toLowerCase() == "value") {
                returnVal.value = param[1].toLowerCase();
            }
        }
        
        if ((returnVal.value == "date") && !returnVal.anyTime) returnVal.allDay = true;
        return returnVal;
    },
    
    rruleToICal: function (rrule){
        if (rrule.isSupported()){
            var recurrenceRuleList = [
               ";FREQ=",
                this.rruleFrequenciesToRruleConstants[rrule.getFrequency()]
             ]
             var endDate = rrule.getEndDate();
             if (endDate){
                recurrenceRuleList.push(";UNTIL=");
                recurrenceRuleList.push(dojo.date.strftime(rrule.getEndDate().getUTCDateProxy(), "%Y%m%dT%H%M%SZ"));
             }
            
            return recurrenceRuleList.join("");
        } 
        else {
            return rrulePropsToICal(rrule.getUnsupportedRule());
        }
    },

    rrlePropsToICal: function (rProps){
        var iCalProps = [];
        for (var key in rProps){
            iCalProps.push(key);
            iCalProps.push("=")
            if (dojo.lang.isArray(rProps[key])){
                iCalProps.push(rProps[key].join());
            }
            else if (rProps[key] instanceof cosmo.datetime.Date){
                iCalProps.push(
                    dojo.date.strftime(rProps[key].getUTCDateProxy(), "%Y%m%dT%H%M%SZ")
                );
            }
            else {
                iCalProps.push(rProps[key]);
            }
            iCalProps.push(";");
            return iCalProps.join("");
        }
    },

    parseRRule: function (rule){
        if (!rule) {
            return null;
        }
        return this.rPropsToRRule(this.parseRRuleToHash(rule));
    },
    
    parseExdate: function (exdate){
        if (!exdate) return null;
        return dojo.lang.map(
                exdate.split(":")[1].split(","),
                cosmo.datetime.fromIso8601
         );
    },

    //Snagged from dojo.cal.iCalendar
    parseRRuleToHash: function (rule){
        var rrule = {}
        var temp = rule.split(";");
        for (var y=0; y<temp.length; y++) {
            if (temp[y] != ""){
                var pair = temp[y].split("=");
                var key = pair[0].toLowerCase();
                var val = pair[1];
                if ((key == "freq") || (key=="interval") || (key=="until")) {
                    rrule[key]= val;
                } else {
                    var valArray = val.split(",");
                    rrule[key] = valArray;
                }
            }
        }
        return rrule;
    },

    rruleConstants: {
      SECONDLY: "SECONDLY",
      MINUTELY: "MINUTELY",
      HOURLY: "HOURLY",
      DAILY: "DAILY",
      MONTHLY:"MONTHLY",
      WEEKLY:  "WEEKLY",
      YEARLY: "YEARLY"
    },
    
    isRRuleUnsupported: function (recur){

        with (this.rruleConstants){

        if (recur.freq == SECONDLY
                || recur.freq == MINUTELY) {
            return true;
        }
        //If they specified a count, it's custom
        if (recur.count != undefined){
            return true;
        }

        if (recur.byyearday){
            return true;
        }

        if (recur.bymonthday){
            return true;
        }

        if (recur.bymonth){
            return true;
        }

        if (recur.byweekno){
            return true;
        }

        if (recur.byday){
            return true;
        }

        if (recur.byhour){
            return true;
        }

        if (recur.byminute){
            return true;
        }

        if (recur.bysecond){
            return true;
        }

        var interval = parseInt(recur.interval);

        //We don't support any interval except for "1" or none (-1)
        //with the exception of "2" for weekly events, in other words bi-weekly.
        if (!isNaN(interval) && interval != 1 ){

            //if this is not a weekly event, it's custom.
            if (recur.freq != WEEKLY){
               return true;
            }

            //so it IS A weekly event, but the value is not "2", so it's custom
            if (interval != 2){
                return true;
            }
        }
        }
        return false;
    },


    rPropsToRRule: function (rprops){
        if (this.isRRuleUnsupported(rprops)) {
            // TODO set something more readable?
            return new cosmo.model.RecurrenceRule({
                isSupported: false,
                unsupportedRule: rprops
            });
        } else {
            var RecurrenceRule = cosmo.model.RRULE_FREQUENCIES;
            var Recur = this.rruleConstants;
            var recurrenceRule = {}
            // Set frequency
            if (rprops.freq == Recur.WEEKLY) {
                if (rprops.interval == 1 || !rprops.interval){
                    recurrenceRule.frequency = RecurrenceRule.FREQUENCY_WEEKLY;
                }
                else if (rprops.interval == 2){
                    recurrenceRule.frequency = RecurrenceRule.FREQUENCY_BIWEEKLY;
                }
            }
            else if (rprops.freq == Recur.MONTHLY) {
                recurrenceRule.frequency = RecurrenceRule.FREQUENCY_MONTHLY;
            }
            else if (rprops.freq == Recur.DAILY) {
                recurrenceRule.frequency = RecurrenceRule.FREQUENCY_DAILY;
            }
            else if (rprops.freq == Recur.YEARLY) {
                recurrenceRule.frequency = RecurrenceRule.FREQUENCY_YEARLY;
            }

            // Set until date
            if (rprops.until) {
                recurrenceRule.endDate = cosmo.datetime.fromIso8601(rprops.until);
            }
            
            recurrenceRule = new cosmo.model.RecurrenceRule(recurrenceRule);
            

            return recurrenceRule;
        }

    },
    
    getEntryUuid: function (entry){
        try {
            var uuid = entry.getElementsByTagName("id")[0];
        } catch (e){
            throw new cosmo.service.translators.
               ParseError("Could not find id element for entry " + entry);
        }
        uuid = unescape(uuid.firstChild.nodeValue.substring(9));
        return uuid;
    }

    


});

cosmo.service.translators.eim = new cosmo.service.translators.Eim();


