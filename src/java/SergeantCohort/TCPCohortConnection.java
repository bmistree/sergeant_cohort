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

    protected void non_blocking_connect()
    {
        final TCPCohortConnection this_ptr = this;
        Thread connect_thread = new Thread()
        {
            @Override
            public void run()
            {
                this_ptr.connect_thread();
            }
        };
        connect_thread.setDaemon(true);
    }


    protected void non_blocking_listen()
    {
        final TCPCohortConnection this_ptr = this;
        Thread listen_thread = new Thread()
        {
            @Override
            public void run()
            {
                this_ptr.listen_thread();
            }
        };
        listen_thread.setDaemon(true);
    }

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
       Should only be called from non_blocking_connect as a separate
       thread to try to connect to other endpoint.  Will keep retrying
       to connect until the connection works.

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
        
        // FIXME: Must actually generate connection
        Util.force_assert(
            "FIXME: Must fill in start_service of TCPCohortConnection.");
    }

    /************************ ICohortConnectionListener overrides ****/
    @Override
    public void handle_connection_timeout()
    {
        // FIXME: should eventually try to reconnect or listen for
        // connections.  And when reconnect and receive connections,
        // set to state up.
        Util.force_assert(
            "FIXME: Must fill in handle_connection_timeout " +
            "in TCPCohortConnection");
    }
}