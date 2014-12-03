package SergeantCohort;

import java.util.Set;

import ProtocolLibs.AppendEntriesProto.AppendEntries;
import ProtocolLibs.CohortMessageProto.CohortMessage;

import SergeantCohort.CohortConnection.ICohortConnection;

public class HeartbeatSendingService extends Thread
{
    /**
       TCPCohortConnection should send a heartbeat this frequently.
     */
    protected final int heartbeat_send_period_ms;
    protected final Set<ICohortConnection> connection_set;
    protected final IAppendEntriesSupplier append_entries_supplier;
    
    
    public HeartbeatSendingService(
        int heartbeat_send_period_ms, Set<ICohortConnection> connection_set,
        IAppendEntriesSupplier append_entries_supplier)
    {
        this.heartbeat_send_period_ms = heartbeat_send_period_ms;
        this.connection_set = connection_set;
        this.append_entries_supplier = append_entries_supplier;
        setDaemon(true);
    }
    
    /**
       Returns false if no longer leader (and therefore should stop
       sending heartbeats).
       
       Send a single heartbeat message to the other end of the
       connection.
     */
    protected boolean send_heartbeat()
    {        
        for (ICohortConnection connection : connection_set)
        {
            AppendEntries.Builder append_entries =
                append_entries_supplier.construct(
                    connection.remote_cohort_id());
            if (append_entries == null)
                return false;

            CohortMessage.Builder msg = CohortMessage.newBuilder();
            msg.setAppendEntries(append_entries);
            connection.send_message(msg);
        }
        return true;
    }

    /**
       Separate daemon thread that periodically sends heartbeats to
       other side.
     */
    @Override
    public void run()
    {
        while (true)
        {
            if (! send_heartbeat())
                break;
            
            try
            {
                Thread.sleep(heartbeat_send_period_ms);
            }
            catch (InterruptedException ex)
            {
                //// DEBUG
                ex.printStackTrace();
                Util.force_assert(
                    "Got an unexpected interrupted exception while " +
                    "sending heartbeats.");
                //// END DEBUG
            }
        }
    }
    
}