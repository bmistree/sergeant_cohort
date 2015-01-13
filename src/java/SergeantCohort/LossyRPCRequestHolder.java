package SergeantCohort;

import java.util.List;
import java.util.ArrayList;

/**
   Occasionally, when we receive an RPC response, we want to be able
   to retrieve the associated contents of the RPC request.  We use
   this class to handle this.  Any response

   To prevent memory leaks, however, occasionally, we may not be able
   to retrieve a request associated with a response.  In that case, we
   return null.

   Each rpc request contains a nonce, which the receiver puts in its
   RPC response.  We use these nonces to associate a request with a
   response.
 */
public class LossyRPCRequestHolder<RPCRequestType>
{
    final protected static int MAX_OUT_OF_DATE = 10;
    // front of list stores older requests.  tail of list stores more
    // recent: when garbage collect, gc from front, not from end.
    final List<ListContents> request_holder = new ArrayList<ListContents>();

    /**
       @returns --- Null if no longer have record of request
       associated with target reply.  Otherwise, remove and return
       request.  Note: we also do a little bookkeeping here.  If there
       are requests that are substantially older than the request that
       we are returning, we remove these to prevent a memory leak.
       (We define substantially older by MAX_OUT_OF_DATE parameter.)
     */
    public synchronized RPCRequestType get_request(long reply_nonce)
    {
        RPCRequestType to_return = null;

        int found_index = -1;
        for (int i = 0; i < request_holder.size(); ++i)
        {
            ListContents contents = request_holder.get(i);
            if (contents.nonce == reply_nonce)
            {
                to_return = contents.request;
                found_index = i;
                break;
            }
        }

        if (found_index != -1)
        {
            // first, remove found index
            request_holder.remove(found_index);
            // then, check if there are any garbage entries to
            // collect.
            int num_to_remove_from_front = found_index - MAX_OUT_OF_DATE;
            for (int i = 0; i < num_to_remove_from_front; ++i)
                request_holder.remove(0);
        }
        return to_return;
    }

    public synchronized void add_request(
        RPCRequestType request,long reply_nonce)
    {
        ListContents to_add = new ListContents(request,reply_nonce);
        request_holder.add(to_add);
    }
    

    protected class ListContents
    {
        public final RPCRequestType request;
        public final long nonce;
        
        public ListContents(
            RPCRequestType request,long nonce)
        {
            this.request = request;
            this.nonce = nonce;
        }
    }
}