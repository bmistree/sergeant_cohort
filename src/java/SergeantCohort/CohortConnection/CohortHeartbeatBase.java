package SergeantCohort.CohortConnection;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

import java.io.IOException;

import ProtocolLibs.CohortMessageProto.CohortMessage;
import ProtocolLibs.HeartbeatProto.Heartbeat;

import SergeantCohort.Util;
import SergeantCohort.ILastViewNumberSupplier;

public abstract class CohortHeartbeatBase implements ICohortConnection
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
       Periodically sends heartbeat to endpoint on other side of this
       connection.
     */
    protected final Thread heartbeat_sending_thread;

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
    public CohortHeartbeatBase(
        int heartbeat_timeout_period_ms,int heartbeat_send_period_ms,
        ILastViewNumberSupplier view_number_supplier)
    {
        this.heartbeat_timeout_period_ms = heartbeat_timeout_period_ms;
        this.heartbeat_send_period_ms = heartbeat_send_period_ms;
        this.view_number_supplier = view_number_supplier;

        // Initializes heartbeat watchdog thread, but does not start
        // it.
        final CohortHeartbeatBase this_ptr = this;
        this.heartbeat_watchdog_thread = new Thread()
        {
            @Override
            public void run()
            {
                this_ptr.heartbeat_watchdog();
            }
        };
        this.heartbeat_watchdog_thread.setDaemon(true);

        // Initializes heartbeat sending thread, but does not start
        // it.
        this.heartbeat_sending_thread = new Thread()
        {
            @Override
            public void run()
            {
                this_ptr.heartbeat_sender();
            }
        };
        this.heartbeat_sending_thread.setDaemon(true);
    }

        
    /**
       Should get called whenever we receive a heartbeat message from
       other side.
     */
    protected void handle_heartbeat_message(Heartbeat msg)
    {
        heartbeat_watchdog_thread.interrupt();
        state_lock.lock();

        // FIXME: should this condition ever happen???
        if (state == CohortConnectionState.CONNECTION_DOWN)
        {
            state = CohortConnectionState.CONNECTION_UP;
            notify_connection_transition(false);
        }
        state_lock.unlock();
    }

    /**
       Start threads that periodically send heartbeat to opposite side
       and expires connection if doesn't receive a heartbeat after a
       period has expired.
    */
    protected void start_heartbeat_services()
    {
        heartbeat_watchdog_thread.start();
        heartbeat_sending_thread.start();
    }

    /**
       @returns --- true if message has been enqueued for eventual
       delivery.  false otherwise.
     */
    public abstract boolean send_message (CohortMessage.Builder msg);

    /**
       @returns The id of the remote cohort.
     */
    @Override
    public abstract long remote_cohort_id();
    
    
    /**
       Send a single heartbeat message to the other end of the
       connection.
     */
    protected boolean send_heartbeat()
    {
        long view_number = view_number_supplier.last_view_number();
        Heartbeat.Builder heartbeat = Heartbeat.newBuilder();
        heartbeat.setViewNumber(view_number);

        CohortMessage.Builder msg = CohortMessage.newBuilder();
        msg.setHeartbeat(heartbeat);
        return send_message(msg);
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
       Separate daemon thread that periodically sends heartbeats to
       other side.
     */
    protected void heartbeat_sender()
    {
        while (true)
        {
            send_heartbeat();
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
                    connection_listener.handle_connection_timeout(this);
                else
                    connection_listener.handle_connection_up(this);
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