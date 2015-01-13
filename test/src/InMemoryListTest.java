package test;

import java.nio.ByteBuffer;

import SergeantCohort.LogEntry;
import SergeantCohort.Storage.InMemoryList;

public class InMemoryListTest
{
    private static final int NUM_ENTRIES_TO_INSERT = 5;
    private static final int PREFIX_TO_REMOVE_FROM = 2;
    
    public static void main (String[] args)
    {
        if (run())
            System.out.println("\nInMemoryListTest SUCCEEDED\n");
        else
            System.out.println("\nInMemoryListTest FAILED\n");
    }

    public static boolean run()
    {
        // simply add bytes associated with number range 0,50.  Then
        // truncate and get to ensure that still get same expected
        // values.
        
        InMemoryList list = new InMemoryList();

        for (int i = 0; i < NUM_ENTRIES_TO_INSERT; ++i)
        {
            byte[] int_array = ByteBuffer.allocate(4).putInt(i).array();
            list.append_entry(int_array,1);
        }

        // check size
        if (list.log_size() != NUM_ENTRIES_TO_INSERT)
            return false;

        // check values
        for (int i = 0; i < NUM_ENTRIES_TO_INSERT; ++i)
        {
            LogEntry le = list.get_entry((long)i);
            ByteBuffer bb = ByteBuffer.wrap(le.contents);
            if (bb.getInt() != i)
                return false;
        }

        // remove prefix
        list.truncate_prefix(PREFIX_TO_REMOVE_FROM);
        
        // check size
        if (list.log_size() != NUM_ENTRIES_TO_INSERT)
            return false;
        
        // check values
        for (int i = PREFIX_TO_REMOVE_FROM; i < NUM_ENTRIES_TO_INSERT; ++i)
        {
            LogEntry le = list.get_entry((long)i);
            ByteBuffer bb = ByteBuffer.wrap(le.contents);
            if (bb.getInt() != i)
                return false;
        }
        
        return true;
    }
}