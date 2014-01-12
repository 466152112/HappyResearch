package happy.research.cf;

import java.util.Map;

/**
 * Implement the <em>trust-based</em> method proposed in the literature
 * 
 * @author guoguibing
 */
public abstract class DefaultTrust_mt extends DefaultCF_mt
{

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		Map<String, Map<String, Double>> trustMap = train();

		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new DefaultTrust_t(trustMap, i));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}

	protected abstract Map<String, Map<String, Double>> train();

}
