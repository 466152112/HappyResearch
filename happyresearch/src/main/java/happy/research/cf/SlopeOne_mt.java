package happy.research.cf;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
* The implementation of (weighted) slope one by Guibing Guo on June 7th, 2013.
* <br/>
* This is a compact model-based approach, hence we should use k-fold cross validation. 
* <br/>
*/

public class SlopeOne_mt extends DefaultCF_mt
{

	public SlopeOne_mt()
	{
		methodId = "SlopeOne";
	}

	protected void train(Map<String, Map<String, Double>> diffMatrix, Map<String, Map<String, Integer>> freqMatrix)
	{
		// first iterate through users
		for (Map<String, Rating> itemRatings : userRatingsMap.values())
		{
			// then iterate through user data
			for (Entry<String, Rating> e1 : itemRatings.entrySet())
			{
				String i1 = e1.getKey();
				double r1 = e1.getValue().getRating();
				if (!diffMatrix.containsKey(i1))
				{
					diffMatrix.put(i1, new HashMap<String, Double>());
					freqMatrix.put(i1, new HashMap<String, Integer>());
				}
				for (Entry<String, Rating> e2 : itemRatings.entrySet())
				{
					String i2 = e2.getKey();
					double r2 = e2.getValue().getRating();

					int cnt = 0;
					if (freqMatrix.get(i1).containsKey(i2)) cnt = freqMatrix.get(i1).get(i2);

					double diff = 0.0;
					if (diffMatrix.get(i1).containsKey(i2)) diff = diffMatrix.get(i1).get(i2);

					double new_diff = r1 - r2;
					freqMatrix.get(i1).put(i2, cnt + 1);
					diffMatrix.get(i1).put(i2, diff + new_diff);
				}
			}
		}
		for (String i : diffMatrix.keySet())
		{
			for (String j : diffMatrix.get(i).keySet())
			{
				double diff = diffMatrix.get(i).get(j);
				int count = freqMatrix.get(i).get(j);
				diffMatrix.get(i).put(j, diff / count);
			}
		}
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		/* {item - item deviation matrix} */
		Map<String, Map<String, Double>> devMatrix = new HashMap<>();
		/* {item - item frequency matrix} */
		Map<String, Map<String, Integer>> freqMatrix = new HashMap<>();

		train(devMatrix, freqMatrix);

		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new SlopeOne_t(i, devMatrix, freqMatrix));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}
}
