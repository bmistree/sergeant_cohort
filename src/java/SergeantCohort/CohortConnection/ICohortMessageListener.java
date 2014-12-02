package SergeantCohort.CohortConnection;

import ProtocolLibs.AppendEntriesProto.AppendEntries;
import ProtocolLibs.AppendEntriesResponseProto.AppendEntriesResponse;
import ProtocolLibs.ElectionProposalProto.ElectionProposal;
import ProtocolLibs.ElectionProposalResponseProto.ElectionProposalResponse;
import ProtocolLibs.NewLeaderProto.NewLeader;

public interface ICohortMessageListener
{
    public void append_entries(
        ICohortConnection cohort_connection,AppendEntries append_entries);
    
    public void append_entries_response(
        ICohortConnection cohort_connection,
        AppendEntriesResponse append_entries_response);
    
    public void election_proposal(
        ICohortConnection cohort_connection,
        ElectionProposal election_proposal);
    
    public void election_proposal_response(
        ICohortConnection cohort_connection,
        ElectionProposalResponse election_proposal_resp);

    public void new_leader(
        ICohortConnection cohort_connection, NewLeader new_leader);
}