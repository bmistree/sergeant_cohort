package SergeantCohort;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

import ProtocolLibs.CohortMessageProto.CohortMessage;

public class TCPCohortConnection extends CohortMessageSendingBase
{
    protected final static int CONNECTION_RETRY_WAIT_PERIOD_MS = 500;

    /**
       Information about local cohort.  This information contains a
       port that we can bind to to listen for connections on.  We
       listen for connections, instead of trying to make connections
       if our cohort id is less than that of the remote cohort.
     */
    protected final CohortInfo local_cohort_info;
    
    /**
       Information for other side to connect to.
     */
    protected final CohortInfo remote_cohort_info;

    protected Socket socket = null;
    
    public TCPCohortConnection(
        CohortInfo local_cohort_info, CohortInfo remote_cohort_info,
        int heartbeat_timeout_period_ms, int heartbeat_send_period_ms,
        ILastViewNumberSupplier view_number_supplier)
    {
        super(
            heartbeat_timeout_period_ms,heartbeat_send_period_ms,
            view_number_supplier);
        this.local_cohort_info = local_cohort_info;
        this.remote_cohort_info = remote_cohort_info;
    }


    /**
       Decides whether this cohort should be trying to connect to
       partner's ports or whether partner tries to connect to ours.
       (Lower cohort ids listen, highers connect.)  Then, we spanw a
       thread to either connect to other side or listen for
       connections.
     */
    protected void non_blocking_listen_or_connect()
    {
        final boolean should_listen =
            local_cohort_info.cohort_id < remote_cohort_info.cohort_id;

        final TCPCohortConnection this_ptr = this;
        Thread listen_or_connect_thread = new Thread()
        {
            @Override
            public void run()
            {
                if (should_listen)
                    this_ptr.listen_thread();
                else
                    this_ptr.connect_thread();
            }
        };
        listen_or_connect_thread.setDaemon(true);
        listen_or_connect_thread.start();
    }
    
    /**       
       Should only be called from non_blocking_listen_or_connect as a
       separate thread to try to listen for other cohort's
       connections.  Will keep retrying to accept connection until get
       a connection.

       Should only call if we are not already connected to other
       endpoint.
     */
    private void listen_thread()
    {
        //// DEBUG
        state_lock.lock();
        if (state != CohortConnectionState.CONNECTION_DOWN)
        {
            Util.force_assert(
                "Should only try connecting when connection is down.");
        }
        if (socket != null)
        {
            Util.force_assert("Expected socket to be null.");
        }
        state_lock.unlock();
        //// END DEBUG

        while (true)
        {
            ServerSocket server_socket = null;
            try
            {
                server_socket = new ServerSocket(local_cohort_info.port);
                
                try
                {
                    socket = server_socket.accept();
                    server_socket.close();
                    
                    // socket is up and connection has been made.
                    // notify any listeners.
                    state_lock.lock();
                    state = CohortConnectionState.CONNECTION_UP;
                    notify_connection_transition(false);
                    state_lock.unlock();
                    return;
                }
                catch(IOException ex)
                {
                    server_socket.close();
                }
            }
            catch (IOException ex)
            {
                // EMPTY: just wait some time and retry.
            }

            // wait some time before trying to connect again.
            try
            {
                Thread.sleep(CONNECTION_RETRY_WAIT_PERIOD_MS);
            }
            catch(InterruptedException ex)
            {
                Util.force_assert(
                    "Unexpected interruption of a listen retry.");
            }
        }
    }
    
    /**
       Should only be called from non_blocking_listen_or_connect as a
       separate thread to try to connect to other cohort host.  Will
       keep retrying to connect until the connection works.

       Should only call if we are not already connected to other
       endpoint.
     */
    private void connect_thread()
    {
        // breaks out when actually connects.
        while (true)
        {
            state_lock.lock();
            try
            {
                //// DEBUG
                if (state != CohortConnectionState.CONNECTION_DOWN)
                {
                    Util.force_assert(
                        "Should only try connecting when connection is down.");
                }
                if (socket != null)
                {
                    Util.force_assert("Expected socket to be null.");
                }
                //// END DEBUG
            
                try
                {
                    socket = new Socket(
                        remote_cohort_info.ip_addr_or_hostname,
                        remote_cohort_info.port);

                    // update state variable and notify all listeners
                    // that our connection is up again.
                    state = CohortConnectionState.CONNECTION_UP;
                    notify_connection_transition(false);
                    return;
                }
                catch (IOException ex)
                {
                    // wait a period and try to reconnect to other side
                    socket = null;
                }
            }
            finally
            {
                state_lock.unlock();
            }

            // wait some time before trying to connect again.
            try
            {
                Thread.sleep(CONNECTION_RETRY_WAIT_PERIOD_MS);
            }
            catch(InterruptedException ex)
            {
                Util.force_assert(
                    "Unexpected interruption of a connection retry.");
            }
        }
    }
    
    /**
       @param msg --- The message to send to other side.
     */
    @Override
    protected void connection_specific_send_message(
        CohortMessage.Builder msg)
    {
        // FIXME: Must fill in
        Util.force_assert(
            "FIXME: Must fill in send_message of TCPCohortConnection.");
    }
    
    /************************ ICohortConnection overrides ***********/
    @Override
    public void start_service()
    {
        start_heartbeat_services();
        // Try to initially connect to other side, or to start the
        // initial connection.
        non_blocking_listen_or_connect();
    }

    /************************ ICohortConnectionListener overrides ****/
    @Override
    public void handle_connection_timeout()
    {
        // Try to reconnect directly or listen for connections.  And
        // when connection comes up, set state to up.
        non_blocking_listen_or_connect();
    }
}