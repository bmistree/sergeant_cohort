package SergeantCohort;

import java.io.IOException;

import ProtocolLibs.CohortMessageProto.CohortMessage;

public class TCPCohortConnection extends CohortConnectionBase
{
    /**
       Information for other side to connect to.
     */
    protected final CohortInfo cohort_info;

    public TCPCohortConnection(
        CohortInfo cohort_info,int heartbeat_timeout_period_ms,
        int heartbeat_send_period_ms,
        ILastViewNumberSupplier view_number_supplier)
    {
        super(
            heartbeat_timeout_period_ms,heartbeat_send_period_ms,
            view_number_supplier);
        this.cohort_info = cohort_info;
    }

    /**
       @param cohort_message_to_send --- The message to send to other
       side.
       
       @returns true if the message has been queued to be sent and
       will definitely be sent as soon as can make connection.  false
       if application itself should handle retrying.
     */
    @Override
    protected boolean send_message(CohortMessage cohort_message_to_send)
    {
        // FIXME: Must fill in
        Util.force_assert(
            "FIXME: Must fill in send_message of TCPCohortConnection.");
        return false;
    }
    
    /************************ ICohortConnection overrides ***********/
    @Override
    public void start_service()
    {
        start_heartbeat_services();
        
        // FIXME: Must actually generate connection
        Util.force_assert(
            "FIXME: Must fill in start_service of TCPCohortConnection.");
    }
}