package happy.research.cf;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class DefaultTrust_t extends Thread_t
{
	protected Map<String, Map<String, Double>>	trustMap	= null;

	public DefaultTrust_t(Map<String, Map<String, Double>> trustMap, int id)
	{
		super(id);
		this.trustMap = trustMap;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		String user = testRating.getUserId();
		String item = testRating.getItemId();

		Map<String, Double> trustScores = new HashMap<>();
		Map<String, Double> trustRatings = new HashMap<>();

		Map<String, Double> trusteeMap = trustMap.get(user);
		if (trusteeMap == null) return null;

		for (Entry<String, Double> en : trusteeMap.entrySet())
		{
			String trustee = en.getKey();
			double trust = en.getValue();

			Map<String, Rating> bsRatings = userRatingsMap.get(trustee);
			if (bsRatings == null) continue;

			double bsRating = 0.0;
			if (bsRatings.containsKey(item)) bsRating = bsRatings.get(item).getRating();
			if (bsRating <= 0.0 || Double.isNaN(bsRating)) continue;
			if (trust > params.TRUST_THRESHOLD && bsRating > 0.0)
			{
				trustScores.put(trustee, trust);
				trustRatings.put(trustee, bsRating);
			}
		}

		if (trustScores == null || trustScores.size() < 1) return null;

		return new Map[] { trustScores, trustRatings };
	}

}
