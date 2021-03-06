package SergeantCohort.CohortConnection;

public interface ICohortConnectionListener
{
    /**
       Gets executed when connection goes down.
     */
    public void handle_connection_timeout(
        ICohortConnection timed_out_connection);
    /**
       Gets executed if connection goes from not set up to setup or we
       receive a notification that the connection is back up after the
       connection had timed out.
     */
    public void handle_connection_up(
        ICohortConnection up_connection);
}
