package happy.research.cf;

/**
 * Implement Maria Chowdhury's TCF method
 * 
 * @author guoguibing
 */
public class TCF_mt extends DefaultCF_mt
{

	public TCF_mt()
	{
		methodId = "TCF" + params.TCF_ITERATION;
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new TCF_t(i));
			threads[i].start();
		}

		for (Thread tr : threads)
			tr.join();

		return pf;
	}

}
