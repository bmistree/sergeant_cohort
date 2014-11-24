package SergeantCohort;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

import java.io.IOException;

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
    
    /************************ ICohortConnection overrides *****************/
    public abstract void start_service();
    public abstract void send_heartbeat(long view_number) throws IOException;
    
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