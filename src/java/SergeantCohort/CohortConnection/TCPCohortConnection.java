package SergeantCohort.CohortConnection;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;
import java.io.IOException;

import ProtocolLibs.CohortMessageProto.CohortMessage;

import SergeantCohort.Util;
import SergeantCohort.CohortInfo;

public class TCPCohortConnection
    extends CohortMessageSendingBase implements ICohortConnectionListener
                                 
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
    protected final ReentrantLock socket_lock = new ReentrantLock();

    
    public TCPCohortConnection(
        CohortInfo local_cohort_info, CohortInfo remote_cohort_info)

    {
        this.local_cohort_info = local_cohort_info;
        this.remote_cohort_info = remote_cohort_info;
    }

    /**
       @returns The id of the remote cohort.
     */
    @Override
    public long remote_cohort_id()
    {
        return remote_cohort_info.cohort_id;
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
                Socket sock = null;
                if (should_listen)
                    sock = this_ptr.listen_thread();
                else
                    sock = this_ptr.connect_thread();

                if (sock != null)
                    read_from_socket(sock);
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
    private Socket listen_thread()
    {
        //// DEBUG
        state_lock.lock();
        socket_lock.lock();
        if (state != CohortConnectionState.CONNECTION_DOWN)
        {
            Util.force_assert(
                "Should only try connecting when connection is down.");
        }
        if (socket != null)
        {
            Util.force_assert("Expected socket to be null.");
        }
        socket_lock.unlock();
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
                    Socket new_socket = server_socket.accept();
                    server_socket.close();

                    socket_lock.lock();
                    socket = new_socket;
                    socket_lock.unlock();
                    // socket is up and connection has been made.
                    // notify any listeners.
                    state_lock.lock();
                    state = CohortConnectionState.CONNECTION_UP;
                    notify_connection_transition(false);
                    state_lock.unlock();
                    return socket;
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
       Infinitely tries to read from socket (until socket fails).
       Called after get a connection from listener thread or from
       connector thread.
     */
    private void read_from_socket(Socket to_read_from)
    {
        while (true)
        {
            try
            {
                CohortMessage cohort_message =
                    CohortMessage.parseDelimitedFrom(
                        to_read_from.getInputStream());
                handle_message(cohort_message);
            }
            catch (IOException ex)
            {
                // It's okay to break here: will try to reconnect
                // separately.
                break;
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
    private Socket connect_thread()
    {
        // breaks out when actually connects.
        while (true)
        {
            state_lock.lock();
            socket_lock.lock();
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
                    return socket;
                }
                catch (IOException ex)
                {
                    // wait a period and try to reconnect to other side
                    socket = null;
                }
            }
            finally
            {
                socket_lock.unlock();
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
        // note unlocking in two separate places because when get an
        // IOException, must do work afterwards that doesn't include
        // holding the socket lock.
        socket_lock.lock();
        try
        {
            // means can't send message.
            if (socket == null)
            {
                socket_lock.unlock();
                return;
            }
            
            CohortMessage built_message = msg.build();
            built_message.writeDelimitedTo(socket.getOutputStream());
            socket_lock.unlock();
        }
        catch (IOException ex)
        {
            // socket failed: transition into down state
            socket = null;
            socket_lock.unlock();
            connection_down();
        }
    }
    
    /************************ ICohortConnection overrides ***********/
    @Override
    public void start_service()
    {
        // Try to initially connect to other side, or to start the
        // initial connection.
        non_blocking_listen_or_connect();
    }

    /************************ ICohortConnectionListener overrides ****/
    /**
       @param connection --- this.
     */
    @Override
    public void handle_connection_timeout(ICohortConnection connection)
    {
        // Try to reconnect directly or listen for connections.  And
        // when connection comes up, set state to up.
        non_blocking_listen_or_connect();
    }

    @Override
    public void handle_connection_up(ICohortConnection up_connection)
    {}
    
    protected static class TCPCohortConnectionFactory
        implements ICohortConnectionFactory
    {
        public ICohortConnection construct(
            CohortInfo local_info, CohortInfo remote_info)
        {
            return new TCPCohortConnection(local_info,remote_info);
        }
    }

    public static final TCPCohortConnectionFactory CONNECTION_FACTORY =
        new TCPCohortConnectionFactory();
}