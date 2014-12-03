package SergeantCohort;

import java.util.concurrent.atomic.AtomicBoolean;

public class HeartbeatListeningService
{
    /**
       If we do not receive a heartbeat message in this period of ms,
       then we determine that the connection is dead and notify
       connection listeners.
     */
    protected final int heartbeat_timeout_period_ms;

    protected final Thread watchdog_thread;

    protected final long leader_id;
    protected final long view_number;
    
    /**
       Gets set to true when cohort manager stops service.
     */
    protected AtomicBoolean stopped = new AtomicBoolean(false);

    protected ILeaderDownListener leader_down_listener;
    
    public HeartbeatListeningService(
        int heartbeat_timeout_period_ms,
        ILeaderDownListener leader_down_listener, long leader_id,
        long view_number)
    {
        this.heartbeat_timeout_period_ms = heartbeat_timeout_period_ms;
        this.leader_down_listener = leader_down_listener;
        this.leader_id = leader_id;
        this.view_number = view_number;

        
        final HeartbeatListeningService this_ptr = this;
        watchdog_thread = new Thread()
        {
            @Override 
            public void run()
            {
                this_ptr.watchdog_while_loop();
            }
        };
        watchdog_thread.setDaemon(true);
    }
    
    public void start_service()
    {
        watchdog_thread.start();
    }
    public void stop_service()
    {
        stopped.set(true);
    }

    /**
       Called whenever receive append_entries message.
     */
    public void append_entries_message()
    {
        watchdog_thread.interrupt();
    }

    protected void watchdog_while_loop()
    {
        while (true)
        {
            if (stopped.get())
                break;
            
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
            leader_down_listener.leader_down(leader_id,view_number);
        }
    }
}

