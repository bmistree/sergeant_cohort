package SergeantCohort.CohortConnection;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

import ProtocolLibs.CohortMessageProto.CohortMessage;


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