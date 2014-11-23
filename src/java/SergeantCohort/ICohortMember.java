package SergeantCohort;


public interface ICohortMember
{
    /**
       Received on the secondary when this command has been finalized
       by the leader.
       
       @param serialized_command --- The serialized version of the
       command.
     */
    public void secondary_finalize_command(byte[] serialized_command);
}