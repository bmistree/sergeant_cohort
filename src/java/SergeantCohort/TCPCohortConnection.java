package SergeantCohort;

import java.io.IOException;

import ProtocolLibs.CohortMessageProto.CohortMessage;

public class TCPCohortConnection extends CohortMessageSendingBase
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
       @param msg --- The message to send to other side.
     */
    @Override
    protected void connection_specific_send_message(
        CohortMessage.Builder msg)
    {
        // FIXME: Must fill in
        Util.force_assert(
            "FIXME: Must fill in send_message of TCPCohortConnection.");
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

    /************************ ICohortConnectionListener overrides ****/
    @Override
    public void handle_connection_timeout()
    {
        // FIXME: should eventually try to reconnect.  And when
        // reconnect, set to state up.
        Util.force_assert(
            "FIXME: Must fill in handle_connection_timeout " +
            "in TCPCohortConnection");
    }
}