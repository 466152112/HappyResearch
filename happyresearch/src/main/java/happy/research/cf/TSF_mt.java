package happy.research.cf;


/**
 * This approach is an implementation of paper: 
 * 
 *      A trust-semantic fusion based recommendation approach for e-business applications,
 *      Qusai Shambour and Jie Lu, Decision support Systems, 2012. 
 *      
 * @author guoguibing
 *
 */
public class TSF_mt extends DefaultCF_mt
{

	@Override
	public String toString()
	{
		return "This approach is an implementation of paper: A trust-semantic fusion based recommendation approach for e-business applications,"
				+ " Qusai Shambour and Jie Lu, Decision support Systems, 2012. ";
	}

	public TSF_mt() throws Exception
	{
		methodId = "TSF";
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new TSF_t(i));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}

}
