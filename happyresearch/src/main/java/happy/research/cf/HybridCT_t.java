package happy.research.cf;

import java.util.Map;

/**
 * This class implements PCC-based CF
 * 
 * @author guoguibing
 */
public class HybridCT_t extends Thread_t
{

	public HybridCT_t(int id)
	{
		super(id);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		Map<String, Double>[] simData = useSimilarRatings(testRating);
		Map<String, Double>[] trustData = useTrustRatings(testRating);
		Map<String, Double>[] data = new Map[(simData.length + trustData.length)];

		int i=0;
		for (; i < simData.length; i++)
			data[i] = simData[i];
		for (; i < data.length; i++)
			data[i] = trustData[i - simData.length];

		return data;
	}
}