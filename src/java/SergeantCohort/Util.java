package SergeantCohort;


public class Util
{
    public static void force_assert(String message)
    {
        System.out.println(message);
        assert(false);
        System.exit(-1);
    }


    public static class LongNonceGenerator
    {
        private long internal_val = 0;

        public synchronized long increment_and_get()
        {
            internal_val += 1;
            return internal_val;
        }
    }
}