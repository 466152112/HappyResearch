package happy.research.cf;

/**
 * Hybrid methods of PCC-CF and MT1
 * 
 * @author guoguibing
 * 
 */
public class HybridCT_mt extends DefaultCF_mt
{

	public HybridCT_mt()
	{
		methodId = "HybridCT";
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new HybridCT_t(i));
			threads[i].start();
		}

		for (Thread tr : threads)
			tr.join();

		return pf;
	}

}
