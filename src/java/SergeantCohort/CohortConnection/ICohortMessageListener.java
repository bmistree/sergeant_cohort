package SergeantCohort.CohortConnection;

import ProtocolLibs.LeaderCommandProto.LeaderCommand;
import ProtocolLibs.FollowerCommandAckProto.FollowerCommandAck;
import ProtocolLibs.ElectionProposalProto.ElectionProposal;
import ProtocolLibs.ElectionProposalResponseProto.ElectionProposalResponse;

public interface ICohortMessageListener
{
    public void leader_command(LeaderCommand leader_command);
    public void follower_command_ack(FollowerCommandAck follower_command_ack);
    public void election_proposal(ElectionProposal election_proposal);
    public void election_proposal_response(
        ElectionProposalResponse election_proposal_resp);
}