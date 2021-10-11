package com.oncolens.fhirengine.model;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Builder
@Data
public class AuditEvent {
    private String id; // Identifier for the event
    private String processuuid;  //A UUID that is unique to the run. All events in the run will have the same id which makes it easy to group
    private String message;  // short message describing the audit event Ex. field validation failed
    private String userfriendlymessage; // Longer more meaningful message EX. NPI needs to be 10 digits long, found only 8
    private String status;//Status of the Event - started, running, errored etc.
    private String source; // An identifier for the source for the event Ex. "Fhir Processor", "Flat file Loader" etc
    private String customer; // Customer for whom this process is being run,Ex. - UKY
    private String siteid; // Site id of the organization for which this process is being run Ex. 3456
    private String originfile; //For fhir, this is the cohort list sent by the site. For flat file it is the data file sent by the site
    private String workflow; //For fhir it is the flow Ex. DSTU2EPIC, R4CERNER etc. For flat files it could be CCDFLOW, CSVFLOW etc.
    private String subject; //Context for the event. For ex. "main process", "Observations fetch" etc.
    private String time; //Time when the event occurred
    private String scope; // name space of the Audit event - For ex. "com.oncolens.data.integrations.audit"
}


