package SergeantCohort;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

import java.io.IOException;

import ProtocolLibs.CohortMessageProto.CohortMessage;
import ProtocolLibs.HeartbeatProto.Heartbeat;


public abstract class CohortConnectionBase implements ICohortConnection
{
    protected final Set<ICohortConnectionListener> connection_listener_set =
        new HashSet<ICohortConnectionListener>();
    protected final ReentrantLock connection_listener_lock =
        new ReentrantLock();
    
    protected final Set<ICohortMessageListener> message_listener_set =
        new HashSet<ICohortMessageListener>();
    protected final ReentrantLock message_listener_lock = new ReentrantLock();

    protected CohortConnectionState state =
        CohortConnectionState.CONNECTION_DOWN;
    protected final ReentrantLock state_lock = new ReentrantLock();
    protected enum CohortConnectionState
    {
        CONNECTION_DOWN, CONNECTION_UP;
    }

    /**
       Should get inerrupted whenever we receive a heartbeat message
       from alternate side.  Gets set in constructor and actually gets
       started when start method is called.
     */
    protected final Thread heartbeat_watchdog_thread;


    /**
       If we do not receive a heartbeat message in this period of ms,
       then we determine that the connection is dead and notify
       connection listeners.
     */
    protected final int heartbeat_timeout_period_ms;

    /**
       TCPCohortConnection should send a heartbeat this frequently.
     */
    protected final int heartbeat_send_period_ms;

    protected final ILastViewNumberSupplier view_number_supplier;
    
    /**
       @param heartbeat_timeout_period_ms --- {@link
       CohortConnectionBase#heartbeat_timeout_period_ms}

       @param heartbeat_send_period_ms --- {@link
       CohortConnectionBase#heartbeat_send_period_ms}
     */
    public CohortConnectionBase(
        int heartbeat_timeout_period_ms,int heartbeat_send_period_ms,
        ILastViewNumberSupplier view_number_supplier)
    {
        this.heartbeat_timeout_period_ms = heartbeat_timeout_period_ms;
        this.heartbeat_send_period_ms = heartbeat_send_period_ms;
        this.view_number_supplier = view_number_supplier;

        // Initializes heartbeat watchdog thread, but does not start
        // it.
        final CohortConnectionBase this_ptr = this;
        this.heartbeat_watchdog_thread = new Thread()
        {
            @Override
            public void run()
            {
                this_ptr.heartbeat_watchdog();
            }
        };
        this.heartbeat_watchdog_thread.setDaemon(true);
    }

    
    /**
       Override this method to send cohort message to other side.
     */
    protected abstract void send_message(CohortMessage msg) throws IOException;


    /**
       Message parser for received messages.
     */
    protected void handle_message(CohortMessage msg)
    {
        if (msg.hasHeartbeat())
            handle_heartbeat_message(msg.getHeartbeat());
        else
        {
            message_listener_lock.lock();
            for (ICohortMessageListener msg_listener : message_listener_set)
            {
                if (msg.hasLeaderCommand())
                {
                    msg_listener.leader_command(msg.getLeaderCommand());
                }
                else if (msg.hasFollowerCommandAck())
                {
                    msg_listener.follower_command_ack(
                        msg.getFollowerCommandAck());
                }
                else if (msg.hasElectionProposal())
                {
                    msg_listener.election_proposal(msg.getElectionProposal());
                }
                else if (msg.hasElectionProposalResponse())
                {
                    msg_listener.election_proposal_response(
                        msg.getElectionProposalResponse());
                }
                //// DEBUG
                else
                {
                    Util.force_assert(
                        "Unknown message type in connection base.");
                }
                //// END DEBUG
            }
            message_listener_lock.unlock();
        }
    }
    
    /**
       Should get called whenever we receive a heartbeat message from
       other side.
     */
    protected void handle_heartbeat_message(Heartbeat msg)
    {
        heartbeat_watchdog_thread.interrupt();
        state_lock.lock();

        if (state == CohortConnectionState.CONNECTION_DOWN)
        {
            state = CohortConnectionState.CONNECTION_UP;
            notify_connection_transition(false);
        }
        state_lock.unlock();
    }
    
    protected void start_heartbeat_services()
    {
        heartbeat_watchdog_thread.start();
    }
    
    /**
       Send a single heartbeat message to the other end of the
       connection.
     */
    protected void send_heartbeat() throws IOException
    {
        long view_number = view_number_supplier.last_view_number();
        Heartbeat.Builder heartbeat = Heartbeat.newBuilder();
        heartbeat.setViewNumber(view_number);

        CohortMessage.Builder msg = CohortMessage.newBuilder();
        msg.setHeartbeat(heartbeat);
        send_message(msg.build());
    }

    /**
       Should be run as separate thread that periodically gets
       interrupted.  If it doesn't get interrupted after a period of
       time, then 
     */
    protected void heartbeat_watchdog()
    {
        while (true)
        {
            try
            {
                Thread.sleep(heartbeat_timeout_period_ms);
            }
            catch(InterruptedException interrupted_exception)
            {
                // This thread gets interrupted whenever we receive a
                // heartbeat message from other side.  In that case,
                // do nothing more and just restart watchdog timer.
                continue;
            }

            // Did not get a heartbeat message for given period of
            // time: if we were already in state connection down, then
            // continue in that state.  Otherwise, execute call that
            // connection went down.
            connection_down();
        }
    }

    /**
       Transition into down state, if had not already been in down
       state.
     */
    protected void connection_down()
    {
        state_lock.lock();
        if (state == CohortConnectionState.CONNECTION_UP)
        {
            // transition into connection down state.
            state = CohortConnectionState.CONNECTION_DOWN;
            notify_connection_transition(true);
        }
        state_lock.unlock();
    }
    
    /**
       Called while holding state lock.

       Notifies all listeners that we've transitioned into down state
       or up state.
     */
    protected void notify_connection_transition(boolean transitioned_down)
    {
        connection_listener_lock.lock();        
        try
        {
            for (ICohortConnectionListener connection_listener :
                     connection_listener_set)
            {
                if (transitioned_down)
                    connection_listener.handle_connection_timeout();
                else
                    connection_listener.handle_connection_up();
            }
        }
        finally
        {
            connection_listener_lock.unlock();
        }
    }

    
    
    /************************ ICohortConnection overrides *****************/
    @Override
    public abstract void start_service();
    
    /**
       Listen for the connection's timing out.
     */
    @Override
    public void add_connection_listener(ICohortConnectionListener listener)
    {
        connection_listener_lock.lock();
        connection_listener_set.add(listener);
        connection_listener_lock.unlock();
    }
    
    /**
       Register for CohortMessage-s.  Guarantees that will receive
       messages in order that they arrive from transport layer.
     */
    @Override
    public void add_cohort_message_listener(ICohortMessageListener listener)
    {
        message_listener_lock.lock();
        message_listener_set.add(listener);
        message_listener_lock.unlock();
    }
}