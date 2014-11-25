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
     */
    @Override
    protected void send_message(CohortMessage cohort_message_to_send)
        throws IOException
    {
        // FIXME: Must fill in
        Util.force_assert(
            "FIXME: Must fill in send_message of TCPCohortConnection.");
    }

    
    /************************ ICohortConnection overrides ***********/
    @Override
    public void start_service()
    {
        // FIXME: Must fill in
        Util.force_assert(
            "FIXME: Must fill in start_service of TCPCohortConnection.");
    }
}