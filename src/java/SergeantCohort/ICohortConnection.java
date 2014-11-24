package SergeantCohort;

import java.io.IOException;

public interface ICohortConnection
{
    /**
       Send heartbeat message to other side.
     */
    public void send_heartbeat(long view_number) throws IOException;
    
    /**
       Listen for the connection's timing out.
     */
    public void add_connection_listener(ICohortConnectionListener listener);
    
    /**
       Register for CohortMessage-s.  Guarantees that will receive
       messages in order that they arrive from transport layer.
     */
    public void add_cohort_message_listener(ICohortMessageListener listener);
}