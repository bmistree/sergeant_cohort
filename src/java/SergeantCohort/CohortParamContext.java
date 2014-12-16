package SergeantCohort;

public class CohortParamContext
{
    public final long base_max_time_to_wait_for_reelection_ms;
    
    /**
       If we do not receive a heartbeat message in this period of ms,
       then we determine that the connection is dead and notify
       connection listeners.
     */
    public final int heartbeat_timeout_period_ms;
    /**
       TCPCohortConnection should send a heartbeat this frequently.
     */
    public final int heartbeat_send_period_ms;

    /**
       Force sending an append entries before timeout if we've
       received this many entries.
     */
    public final int max_batch_size;

    public CohortParamContext(
        long base_max_time_to_wait_for_reelection_ms,
        int heartbeat_timeout_period_ms, int heartbeat_send_period_ms,
        int max_batch_size)
    {
        this.base_max_time_to_wait_for_reelection_ms =
            base_max_time_to_wait_for_reelection_ms;
        this.heartbeat_timeout_period_ms = heartbeat_timeout_period_ms;
        this.heartbeat_send_period_ms = heartbeat_send_period_ms;
        this.max_batch_size = max_batch_size;
    }
}