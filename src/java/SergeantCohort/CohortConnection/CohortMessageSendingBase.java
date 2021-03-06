package SergeantCohort.CohortConnection;

import java.util.Map;
import java.util.HashMap;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import ProtocolLibs.CohortMessageProto.CohortMessage;

import SergeantCohort.Util;
import SergeantCohort.CohortInfo;

public abstract class CohortMessageSendingBase
    extends CohortHeartbeatBase
{
    /**
       Message parser for received messages.
     */
    protected void handle_message(CohortMessage msg)
    {
        if (msg == null)
            return;
        
        message_listener_lock.lock();
        for (ICohortMessageListener msg_listener : message_listener_set)
        {
            if (msg.hasAppendEntries())
            {
                msg_listener.append_entries(this,msg.getAppendEntries());
            }
            else if (msg.hasAppendEntriesResponse())
            {
                msg_listener.append_entries_response(
                    this,msg.getAppendEntriesResponse());
            }
            else if (msg.hasElectionProposal())
            {
                msg_listener.election_proposal(
                    this,msg.getElectionProposal());
            }
            else if (msg.hasElectionProposalResponse())
            {
                msg_listener.election_proposal_response(
                    this,msg.getElectionProposalResponse());
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
       delivery.  false otherwise.  Will always return true.
     */
    @Override
    public boolean send_message (CohortMessage.Builder msg)
    {
        connection_specific_send_message(msg);
        return true;
    }
}
