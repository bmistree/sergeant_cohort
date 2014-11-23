package SergeantCohort;

/**
   Basic interface for cohorts to submit jobs to system.
 */

public interface ICohortManager
{
    /**
       If we are leader, handles command directly.  Otherwise,
       forwards command to leader.
     */
    public void submit_command(byte[] serialized_command);
}