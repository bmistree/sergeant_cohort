package SergeantCohort;

public interface ICohortConnectionListener
{
    /**
       Gets executed when connection goes down.
     */
    public void handle_connection_timeout();
    /**
       Gets executed if connection goes from not set up to setup or we
       receive a notification that the connection is back up after the
       connection had timed out.
     */
    public void handle_connection_up();
}
