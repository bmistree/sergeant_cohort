package test;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import SergeantCohort.CohortInfo;


public class Util
{
    protected final static int BASE_TCP_PORT = 2222;


    /**
       @param mapping_string ---

       local_id: host,port,other_id,host,port;
                  host,port,other_id,host,port; ...  | 
       local_id: host,port,other_id,host,port;
                  host,port,other_id,host,port; ...  |
       ...
       
       where first set of host and port in a tuple is the local
       host-port pair, and second is the remote.
     */
    public static Map<Long,Set<CohortInfo.CohortInfoPair>>
        produce_cohort_mappings_from_string(String mapping_string)
    {
        Map<Long,Set<CohortInfo.CohortInfoPair>> to_return =
            new HashMap<Long,Set<CohortInfo.CohortInfoPair>>();

        
        String[] individual_cohort_strings = mapping_string.split("\\|");
        
        for (String cohort_string : individual_cohort_strings)
        {
            // handles case where last line is terminated with |
            if (cohort_string.trim().equals(""))
                continue;

            long cohort_id = parse_cohort_id_from_cohort_string(cohort_string);
            Set<CohortInfo.CohortInfoPair> connection_set =
                parse_connection_set_from_cohort_string(
                    cohort_string,cohort_id);

            to_return.put(cohort_id,connection_set);
        }
        return to_return;
    }

    /**
       @param cohort_string --- 
       local_id: host,port,other_id,host,port;
                  host,port,other_id,host,port;

       Would return local_id
    */
    protected static long parse_cohort_id_from_cohort_string(
        String cohort_string)
    {
        String[] parts = cohort_string.split(":");
        return Long.parseLong(parts[0]);
    }

    /**
       @param cohort_string --- 
       local_id: host,port,other_id,host,port;
                  host,port,other_id,host,port;

       @param cohort_id
     */
    protected static Set<CohortInfo.CohortInfoPair>
    parse_connection_set_from_cohort_string(
        String cohort_string, long local_cohort_id)
    {
        Set<CohortInfo.CohortInfoPair> to_return =
            new HashSet<CohortInfo.CohortInfoPair>();

        /*
          connection_set_string looks like this:
          host,port,other_id,host,port;
          host,port,other_id,host,port;

          where first set of hosts and ports on each line are the
          local ports and hosts.
        */
        String connection_set_string = cohort_string.split(":")[1];
        String[] connection_strings = connection_set_string.split(";");


        for (String single_connection_string : connection_strings)
        {
            //single_connection_string: host,port,other_id,host,port

            // allows us to not have to worry about whether or not
            // there's a ; on the last line.
            if (single_connection_string.trim().equals(""))
                continue;

            String[] separated_line = single_connection_string.split(",");

            String local_cohort_host = separated_line[0];
            int local_cohort_port = Integer.parseInt(separated_line[1]);
            long remote_cohort_id = Long.parseLong(separated_line[2]);
            String remote_cohort_host = separated_line[3];
            int remote_cohort_port = Integer.parseInt(separated_line[4]);

            // create cohort information paris and add them to set.
            CohortInfo local_info = new CohortInfo (
                local_cohort_host,local_cohort_port,local_cohort_id);

            CohortInfo remote_info = new CohortInfo (
                remote_cohort_host,remote_cohort_port,remote_cohort_id);

            CohortInfo.CohortInfoPair pair =
                new CohortInfo.CohortInfoPair(remote_info,local_info);

            to_return.add(pair);
        }

        return to_return;
    }
    
    
    /**
       Keys of return map are ids for target cohorts and values are
       sets that we should pass in to their CohortManagers to
       initialize them.
     */
    public static Map<Long,Set<CohortInfo.CohortInfoPair>>
        get_connection_info(long num_cohort_nodes)
    {
        Map<Long,Set<CohortInfo.CohortInfoPair>> to_return =
            new HashMap<Long,Set<CohortInfo.CohortInfoPair>>();
        for (long i = 0; i < num_cohort_nodes; ++i)
        {
            Set<CohortInfo.CohortInfoPair> cohort_info_set =
                new HashSet<CohortInfo.CohortInfoPair>();
            to_return.put(i,cohort_info_set);
            
            int local_cohort_tcp_port_base =
                BASE_TCP_PORT +  (int)((i) * num_cohort_nodes);
            

            for (long j = 0; j < num_cohort_nodes; ++j)
            {
                // don't add a connection for self.
                if (j == i)
                    continue;

                // from i and j, determine which tcp ports each
                // connection should be listening on.
                int remote_cohort_tcp_port_base =
                    BASE_TCP_PORT +  (int)((j) * num_cohort_nodes);

                int local_tcp_port = local_cohort_tcp_port_base + (int)(j);
                int remote_tcp_port = remote_cohort_tcp_port_base + (int)(i);

                
                long local_cohort_id = i;
                long remote_cohort_id = j;


                // create cohort information paris and add them to set.
                CohortInfo local_info = new CohortInfo (
                    "127.0.0.1",local_tcp_port,local_cohort_id);

                CohortInfo remote_info = new CohortInfo (
                    "127.0.0.1",remote_tcp_port,remote_cohort_id);

                CohortInfo.CohortInfoPair pair =
                    new CohortInfo.CohortInfoPair(remote_info,local_info);

                cohort_info_set.add(pair);
            }
        }
        return to_return;
    }
}