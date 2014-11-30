package SergeantCohort.CohortConnection;

import java.io.IOException;

public interface ICohortConnection
{
    /**
       Actually begin the service monitoring.
     */
    public void start_service();
    
    /**
       Listen for the connection's timing out.
     */
    public void add_connection_listener(ICohortConnectionListener listener);
    
    /**
       Register for CohortMessage-s.  Guarantees that will receive
       messages in order that they arrive from transport layer.
     */
    public void add_cohort_message_listener(ICohortMessageListener listener);

    /**
       @returns The id of the remote cohort.
     */
    public int remote_cohort_id();
}