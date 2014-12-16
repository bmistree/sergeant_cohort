package test;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

import java.io.PrintWriter;
import java.io.IOException;

import SergeantCohort.CohortManager;
import SergeantCohort.CohortInfo;
import SergeantCohort.CohortParamContext;
import SergeantCohort.CohortConnection.TCPCohortConnection;
import SergeantCohort.IApplyEntryListener;


/**
   Start three nodes and check that one and only one becomes a leader
   after some point of time.
 */
public class EntriesThroughputTest
{
    public final static long COHORT_SIZE = 3L;

    public final static int NUM_ENTRIES_TO_REPLICATE_INDEX = 0;
    public final static int COHORT_PORTS_INDEX = 1;
    public final static int OUTPUT_FILENAME_INDEX = 2;
    public final static int HEARTBEAT_TIMEOUT_PERIOD_MS_INDEX = 3;
    public final static int ENTRY_SIZE_INDEX = 4;

    public final static long BASE_MAX_TIME_TO_WAIT_FOR_REELECTION_MS = 200L;
    public final static int HEARTBEAT_TIMEOUT_PERIOD_MS = 500;
    public final static int HEARTBEAT_SEND_PERIOD_MS = 150;
    public final static int MAX_BATCH_SIZE = 20;
    public final static CohortParamContext param_context =
        new CohortParamContext(
            BASE_MAX_TIME_TO_WAIT_FOR_REELECTION_MS,
            HEARTBEAT_TIMEOUT_PERIOD_MS,HEARTBEAT_SEND_PERIOD_MS,
            MAX_BATCH_SIZE);
    
    public static ExternalizedCounter externalized_counter = null;
    
    public static void main(String[] args)
    {
        int num_entries_to_replicate =
            Integer.parseInt(args[NUM_ENTRIES_TO_REPLICATE_INDEX]);
        String cohort_ports = args[COHORT_PORTS_INDEX];
        String output_filename = args[OUTPUT_FILENAME_INDEX];
        int heartbeat_timeout_period_ms =
            Integer.parseInt(args[HEARTBEAT_TIMEOUT_PERIOD_MS_INDEX]);
        int entry_size = Integer.parseInt(args[ENTRY_SIZE_INDEX]);
        
        externalized_counter =
            new ExternalizedCounter(num_entries_to_replicate);
        
        Map<Long,Set<CohortInfo.CohortInfoPair>> connection_map =
            Util.produce_cohort_mappings_from_string(cohort_ports);

        run(num_entries_to_replicate,connection_map,
            heartbeat_timeout_period_ms,entry_size);

        try
        {
            externalized_counter.write_output_file(output_filename);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            SergeantCohort.Util.force_assert("Unexpected ioexception");
        }
    }

    
    public static void run(
        int num_entries_to_replicate,
        Map<Long,Set<CohortInfo.CohortInfoPair>> cohort_map,
        int heartbeat_timeout_period_ms,int entry_size)
    {
        // create lots of managers
        Set<CohortManager> cohort_managers = new HashSet<CohortManager>();

        // create lots of managers
        for (Long cohort_id : cohort_map.keySet())
        {
            Set<CohortInfo.CohortInfoPair> connection_info =
                cohort_map.get(cohort_id);
            CohortManager cohort_manager =
                new CohortManager(
                    connection_info,TCPCohortConnection.CONNECTION_FACTORY,
                    cohort_id,param_context);
            
            cohort_managers.add(cohort_manager);
        }

        // start all the managers.
        for (CohortManager cohort_manager : cohort_managers)
            cohort_manager.start_manager();

        // wait for a while to get a leader
        System.out.println("Waiting for leader");
        try
        {
            // wait five seconds
            Thread.sleep(1000*10);
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
            SergeantCohort.Util.force_assert("Some error.");
        }

        // find leader and add externalized listener for it.
        CohortManager leader = null;
        for (CohortManager cohort_manager : cohort_managers)
        {
            if (cohort_manager.is_leader())
                leader = cohort_manager;
        }
        leader.add_apply_entry_listener(externalized_counter);
        
        // ask leader to add NUM_ENTRIES a bunch of times.
        // inserting random bytes so that ensure can't just compress.
        byte[] dummy_entry = new byte[entry_size];
        new Random().nextBytes(dummy_entry);
        System.out.println("\nStarting experiment\n");
        externalized_counter.starting_experiment();
        for (int i = 0; i < num_entries_to_replicate; ++i)
        {
            leader.add_entry_if_leader(dummy_entry);
        }

        System.out.println("\nWaiting to complete\n");
        externalized_counter.wait_to_complete();
    }

    public static class ExternalizedCounter implements IApplyEntryListener
    {
        protected int num_times_applied = 0;
        public final int expected_num_entries;

        protected final ReentrantLock lock = new ReentrantLock();
        protected final Condition cond = lock.newCondition();
        protected boolean has_received_expected_num_entries = false;
        protected long start_time,end_time;
        

        public ExternalizedCounter(int expected_num_entries)
        {
            this.expected_num_entries = expected_num_entries;
        }

        public void starting_experiment()
        {
            start_time = System.nanoTime();
        }

        public void write_output_file(String output_filename)
            throws IOException
        {
            long ns_taken = end_time - start_time;
            PrintWriter writer = new PrintWriter(output_filename, "UTF-8");
            writer.println(Integer.toString(expected_num_entries));
            writer.println(Long.toString(ns_taken));
            writer.close();
        }
        
        /**
           Wait until receive enough expected entries.
         */
        public void wait_to_complete()
        {
            lock.lock();
            while (! has_received_expected_num_entries)
            {
                try
                {
                    cond.await();
                }
                catch(InterruptedException ex)
                {
                    ex.printStackTrace();
                    SergeantCohort.Util.force_assert(
                        "Unexpected interrupt exception");
                }
            }
            lock.unlock();
        }

        
        @Override
        public synchronized void apply_entry (byte[] entry)
        {
            ++num_times_applied;
            if (num_times_applied == expected_num_entries)
            {
                lock.lock();
                end_time = System.nanoTime();
                has_received_expected_num_entries = true;
                cond.signal();
                lock.unlock();
            }
            if ((num_times_applied % 100) == 0)
                System.out.println(num_times_applied);
        }
    }
}
