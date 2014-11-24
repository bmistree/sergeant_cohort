package SergeantCohort;

import java.io.IOException;

import ProtocolLibs.CohortMessageProto.CohortMessage;
import ProtocolLibs.HeartbeatProto.Heartbeat;


public class TCPCohortConnection extends CohortConnectionBase
{
    /**
       Information for other side to connect to.
     */
    protected final CohortInfo cohort_info;

    /**
       If we do not receive a heartbeat message in this period of ms,
       then we determine that the connection is dead and notify
       connection listeners.
     */
    protected final int heartbeat_timeout_period_ms;

    /**
       @param heartbeat_timeout_period_ms --- {@link
       TCPCohortConnection#heartbeat_timeout_period_ms}
     */
    public TCPCohortConnection(
        CohortInfo cohort_info,int heartbeat_timeout_period_ms)
    {
        this.cohort_info = cohort_info;
        this.heartbeat_timeout_period_ms = heartbeat_timeout_period_ms;
    }

    /**
       @param cohort_message_to_send --- The message to send to other
       side.
     */
    protected void send_message(CohortMessage cohort_message_to_send)
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

    /**
       Send heartbeat message to other side.
     */
    @Override
    public void send_heartbeat(long view_number) throws IOException
    {
        Heartbeat.Builder heartbeat = Heartbeat.newBuilder();
        heartbeat.setViewNumber(view_number);

        CohortMessage.Builder msg = CohortMessage.newBuilder();
        msg.setHeartbeat(heartbeat);
        send_message(msg.build());
    }
}