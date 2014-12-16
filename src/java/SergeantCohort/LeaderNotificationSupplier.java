package SergeantCohort;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

public class LeaderNotificationSupplier
{
    final protected ReentrantLock leader_listeners_lock = new ReentrantLock();
    final protected Set<ILeaderElectedListener> leader_listeners =
        new HashSet<ILeaderElectedListener>();

    
    /************* Allow subscribing and removing leader listeners */
    public void add_leader_elected_listener(
        ILeaderElectedListener leader_elected_listener)
    {
        leader_listeners_lock.lock();
        try
        {
            leader_listeners.add(leader_elected_listener);
        }
        finally
        {
            leader_listeners_lock.unlock();
        }
    }

    public void remove_leader_elected_listener(
        ILeaderElectedListener leader_elected_listener)
    {
        leader_listeners_lock.lock();
        try
        {
            leader_listeners.remove(leader_elected_listener);
        }
        finally
        {
            leader_listeners_lock.unlock();
        }
    }

    /**
       Gets called whenever we get a new leader.  Notifies all
       listeners of new leader.
     */
    protected void notify_leader_listeners(
        long view_number,long current_leader_id,long local_cohort_id)
    {
        leader_listeners_lock.lock();
        try
        {
            for (ILeaderElectedListener leader_elected_listener :
                     leader_listeners)
            {
                leader_elected_listener.leader_elected(
                    view_number, current_leader_id, local_cohort_id);
            }
        }
        finally
        {
            leader_listeners_lock.unlock();
        }
    }

    /**
       Gets called wehenver we begin a new election on this node.
     */
    public void notify_election_started_listeners(
        long election_view_number,long local_cohort_id)
    {
        leader_listeners_lock.lock();
        try
        {
            for (ILeaderElectedListener leader_elected_listener :
                     leader_listeners)
            {
                leader_elected_listener.election_started(
                    election_view_number,local_cohort_id);
            }
        }
        finally
        {
            leader_listeners_lock.unlock();
        }
    }

}