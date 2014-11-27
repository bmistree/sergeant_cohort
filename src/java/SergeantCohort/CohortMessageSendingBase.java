package SergeantCohort;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import ProtocolLibs.CohortMessageProto.CohortMessage;


public abstract class CohortMessageSendingBase
    extends CohortHeartbeatBase
{
    private final static int MAX_NUM_OUTSTANDING_UNACKED_MESSAGES = 100;
    
    /**
       The last sequence number we sent to the other side.
     */
    protected long last_sequence_number_sent = 0;
    protected long last_sequence_number_received = 0;
    protected final List<CohortMessage.Builder> unacked_sent_messages =
        new ArrayList<CohortMessage.Builder>();
    protected final ReentrantLock msg_queue_lock = new ReentrantLock();


    public CohortMessageSendingBase(
        int heartbeat_timeout_period_ms,int heartbeat_send_period_ms,
        ILastViewNumberSupplier view_number_supplier)
    {
        super(
            heartbeat_timeout_period_ms,heartbeat_send_period_ms,
            view_number_supplier);
    }
    
    
    /**
       @returns the sequence number to acknowledge.
     */
    protected long get_sequence_number_to_ack()
    {
        try
        {
            msg_queue_lock.lock();
            return last_sequence_number_received;
        }
        finally
        {
            msg_queue_lock.unlock();
        }
    }

    /**
       Message parser for received messages.
     */
    protected void handle_message(CohortMessage msg)
    {
        // FIXME: ensure that only process messages in order of
        // sequence numbers

        msg_queue_lock.lock();
        // Can remove all messages that we have sequence numbers for.
        long last_acked_sequence_number = msg.getAckNumber();
        while (! unacked_sent_messages.isEmpty())
        {
            CohortMessage.Builder oldest_unacked =
                unacked_sent_messages.get(0);

            if (oldest_unacked.getSequenceNumber() >
                last_acked_sequence_number)
            {
                break;
            }
            // remove front element
            unacked_sent_messages.remove(0);
        }
        msg_queue_lock.unlock();
        
        // handle heartbeat messages immediately.
        if (msg.hasHeartbeat())
            handle_heartbeat_message(msg.getHeartbeat());
        else
        {
            message_listener_lock.lock();
            for (ICohortMessageListener msg_listener : message_listener_set)
            {
                if (msg.hasLeaderCommand())
                {
                    msg_listener.leader_command(msg.getLeaderCommand());
                }
                else if (msg.hasFollowerCommandAck())
                {
                    msg_listener.follower_command_ack(
                        msg.getFollowerCommandAck());
                }
                else if (msg.hasElectionProposal())
                {
                    msg_listener.election_proposal(msg.getElectionProposal());
                }
                else if (msg.hasElectionProposalResponse())
                {
                    msg_listener.election_proposal_response(
                        msg.getElectionProposalResponse());
                }
                //// DEBUG
                else
                {
                    Util.force_assert(
                        "Unknown message type in connection base.");
                }
                //// END DEBUG
            }
            message_listener_lock.unlock();
        }
    }
    
    
    /**
       Override this method to send cohort message to other side.

       @returns true if the message has been queued to be sent and
       will definitely be sent as soon as can make connection.  false
       if application itself should handle retrying.
     */
    protected abstract void connection_specific_send_message(
        CohortMessage.Builder msg);

    /**
       @returns --- true if message has been enqueued for eventual
       delivery.  false otherwise.
     */
    @Override
    protected boolean send_message (CohortMessage.Builder msg)
    {
        msg_queue_lock.lock();
        try
        {
            if (unacked_sent_messages.size() >=
                MAX_NUM_OUTSTANDING_UNACKED_MESSAGES)
            {
                return false;
            }

            ++last_sequence_number_sent;
            msg.setSequenceNumber(last_sequence_number_sent);
            unacked_sent_messages.add(msg);
            connection_specific_send_message(msg);
            return true;
        }
        finally
        {
            msg_queue_lock.unlock();
        }
    }
}
