package SergeantCohort.CohortConnection;

import ProtocolLibs.LeaderCommandProto.LeaderCommand;
import ProtocolLibs.FollowerCommandAckProto.FollowerCommandAck;
import ProtocolLibs.ElectionProposalProto.ElectionProposal;
import ProtocolLibs.ElectionProposalResponseProto.ElectionProposalResponse;

public interface ICohortMessageListener
{
    public void leader_command(
        ICohortConnection cohort_connection,LeaderCommand leader_command);
    
    public void follower_command_ack(
        ICohortConnection cohort_connection,
        FollowerCommandAck follower_command_ack);
    
    public void election_proposal(
        ICohortConnection cohort_connection,
        ElectionProposal election_proposal);
    
    public void election_proposal_response(
        ICohortConnection cohort_connection,
        ElectionProposalResponse election_proposal_resp);
}