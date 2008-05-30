/*
 * Copyright 2008 Open Source Applications Foundation
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
dojo.provide("cosmo.ui.widget.DetailView");
dojo.require("dijit._Templated");
dojo.require("dijit.form.TextBox");
dojo.require("dijit.form.Textarea");
dojo.require("dijit.form.DateTextBox");
dojo.require("dijit.form.TimeTextBox");
dojo.require("dijit.form.CheckBox");
dojo.require("dijit.form.Button");
dojo.require("cosmo.model.Item");
dojo.require("cosmo.datetime.timezone");
dojo.require("cosmo.util.html");

dojo.requireLocalization("cosmo.ui.widget", "DetailView");

dojo.declare("cosmo.ui.widget.DetailView", [dijit._Widget, dijit._Templated], {
    templatePath: dojo.moduleUrl("cosmo", "ui/widget/templates/DetailView.html"),
    widgetsInTemplate: true,

    initItem: null,

    // Attach points
    triageButtons: null,
    nowButton: null,
    laterButton: null,
    doneButton: null,
    starButton: null,
    mailButton: null,
    titleInput: null,
    notesInput: null,
    titleSpan: null,
    eventButton: null,
    locationInput: null,
    allDayInput: null,
    startDateInput: null,
    startTimeInput: null,
    endDateInput: null,
    endTimeInput: null,
    timezoneRegionSelector: null,
    timezoneCitySelector: null,
    statusSelector: null,
    recurrenceSelector: null,
    untilInput: null,
    byline: null,
    removeButton: null,
    saveButton: null,

    //fields
    hasEvent: false,
    item: null,

    updateFromItem: function(item){
        this.titleInput.setValue(item.getDisplayName());
        this.notesInput.setValue(item.getBody());
        this.setTriageStatus(item.getTriageStatus());
        var eventStamp = item.getEventStamp();
        if (eventStamp){
            this.hasEvent = true;
            this.updateFromEventStamp(eventStamp);
        } else {
            this.hasEvent = false;
            this.clearEventFields();
            this.disableEventFields();
        }
        if(item.getTaskStamp()) this.setStarred();
        else this.setUnstarred();
        this.item = item;
    },

    clearTriage: function(){
        dojo.removeClass(this.nowButton, "cosmoTriageNowButtonSelected");
        dojo.removeClass(this.laterButton, "cosmoTriageLaterButtonSelected");
        dojo.removeClass(this.doneButton, "cosmoTriageDoneButtonSelected");
    },

    setTriageNow: function(){
        this.clearTriage();
        dojo.addClass(this.nowButton, "cosmoTriageNowButtonSelected");
    },

    setTriageLater: function(){
        this.clearTriage();
        dojo.addClass(this.laterButton, "cosmoTriageLaterButtonSelected");
    },

    setTriageDone: function(){
        this.clearTriage();
        dojo.addClass(this.doneButton, "cosmoTriageDoneButtonSelected");
    },

    setTriageStatus: function(status){
        switch(status){
            case cosmo.model.TRIAGE_NOW:
                this.setTriageNow();
                break;
            case cosmo.model.TRIAGE_LATER:
                this.setTriageLater();
                break;
            case cosmo.model.TRIAGE_DONE:
                this.setTriageDone();
                break;
            default:
                throw new Error("Triage must be now later or done.");
        }
    },

    setStarred: function(){
        dojo.addClass(this.starButton, "cosmoTaskButtonSelected");
    },

    setUnstarred: function(){
        dojo.removeClass(this.starButton, "cosmoTaskButtonSelected");
    },

    /* Event Stamp functions */

    updateFromEventStamp: function(stamp){
        this.locationInput.setValue(stamp.getLocation());

        var startDate = stamp.getStartDate();
        var endDate = stamp.getEndDate();
        this.startDateInput.setValue(startDate);
        this.endDateInput.setValue(endDate);
        if (stamp.getAllDay()){
            this.startTimeInput.setAttribute("disabled", true);
            this.endTimeInput.setAttribute("disabled", true);
        } else {
            this.startTimeInput.setValue(startDate);
            this.endTimeInput.setValue(endDate);
        }
        if (startDate.tzId){
            this.updateFromTimezone(cosmo.datetime.timezone.getTimezone(startDate.tzId));
        } else {
            this.clearTimezoneSelectors();
        }

        this.updateAllDay(stamp.getAllDay());
        this.statusSelector.value = stamp.getStatus();
        this.updateFromRrule(stamp.getRrule());
    },

    updateAllDay: function(allDay){
        this.allDayInput.setValue(allDay);
        if (allDay){
            this.timezoneRegionSelector.setAttribute("disabled", true);
            this.timezoneCitySelector.setAttribute("disabled", true);
            this.startTimeInput.setAttribute("disabled", true);
            this.endTimeInput.setAttribute("disabled", true);
        } else {
            this.timezoneRegionSelector.disabled = false;
            this.startTimeInput.setAttribute("disabled", false);
            this.endTimeInput.setAttribute("disabled", false);
        }
    },

    updateFromTimezone: function(tz){
        if (tz){
            var tzId = tz.tzId;
            var region = tzId.split("/")[0];
            this.updateFromTimezoneRegion(region);
            cosmo.util.html.setSelect(this.timezoneCitySelector, tzId);
        } else {
            this.clearTimezoneSelectors();
        }
    },

    updateFromTimezoneRegion: function(region){
        if (region){
            cosmo.util.html.setSelect(this.timezoneRegionSelector, region);
            cosmo.util.html.setSelectOptions(this.timezoneCitySelector, this.getTimezoneIdOptions(region));
            this.timezoneCitySelector.disabled = false;
        } else {
            this.clearTimezoneSelectors();
        }
    },

    clearTimezoneSelectors: function(){
        this.timezoneRegionSelector.value = "";
        this.timezoneCitySelector.value = "";
        this.timezoneCitySelector.setAttribute("disabled", true);
    },

    getTimezoneIdOptions: function(region){
        return [{text: this.l10n.noTzCity,
                 value: "" }
               ].concat(dojo.map(cosmo.datetime.timezone.getTzIdsForRegion(region),
                   function(id){
                       return {
                           text: id.substr(
                               id.indexOf("/") + 1).replace(/_/g," "),
                               value: id
                       };
                   }));
    },

    updateFromRrule: function(rrule){
        if (rrule){
            if (rrule.isSupported()){
                this.recurrenceSelector.value = rrule.getFrequency();
            } else {
                this.recurrenceSelector.value = 'custom';
            }
            this.untilInput.setAttribute("disabled", false);
            var endDate = rrule.getEndDate();
            if (endDate) this.untilInput.setValue(endDate);
        } else {
            this.untilInput.setAttribute("disabled", true);
            this.recurrenceSelector.value = 'once';
        }
    },

    clearEventFields: function(){
        this.locationInput.setValue("");
        this.allDayInput.setValue("");
        this.startDateInput.setValue("");
        this.startTimeInput.setValue("");
        this.endDateInput.setValue("");
        this.endTimeInput.setValue("");
        this.timezoneRegionSelector.value = "";
        this.timezoneCitySelector.value = "";
        this.statusSelector.value = "";
        this.recurrenceSelector.value = "";
        this.untilInput.setValue("");
    },

    disableEventFields: function(){
        this.locationInput.setAttribute("disabled", true);
        this.allDayInput.setAttribute("disabled", true);
        this.startDateInput.setAttribute("disabled", true);
        this.startTimeInput.setAttribute("disabled", true);
        this.endDateInput.setAttribute("disabled", true);
        this.endTimeInput.setAttribute("disabled", true);
        this.timezoneRegionSelector.setAttribute("disabled", true);
        this.timezoneCitySelector.setAttribute("disabled", true);
        this.statusSelector.setAttribute("disabled", true);
        this.recurrenceSelector.setAttribute("disabled", true);
        this.untilInput.setAttribute("disabled", true);
    },

    // event handlers
    tzRegionOnChange: function(e){
        this.updateFromTimezoneRegion(e.target.value);
    },

    rruleOnChange: function(e){
        var frequency = e.target.value;
        if (frequency == "once") this.updateFromRecurrenceRule(null);
        else if (frequency == "custom") {/*TODO??*/}
        else this.updateFromRrule(new cosmo.model.RecurrenceRule({frequency: frequency}));

    },

    allDayOnChange: function(value){
        this.updateAllDay(value);
    },

    // lifecycle functions
    constructor: function(){
        this.l10n = dojo.i18n.getLocalization("cosmo.ui.widget", "DetailView");

    },

    postCreate: function(){
        if (this.initItem) this.updateFromItem(this.initItem);
    }
});