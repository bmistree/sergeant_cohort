package SergeantCohort;

import java.io.IOException;

public interface ICohortConnection
{
    public void add_connection_listener(ICohortConnectionListener listener);
    public void send_heartbeat(long view_number) throws IOException;
}