package happy.research.cf;

import happy.coding.math.Sims;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Cognitive Trust Model for Recommender Systems Usage: only run under cross validation method
 * 
 * @author guoguibing
 */
public class CogTrust_mt extends DefaultTrust_mt
{
	public CogTrust_mt() throws Exception
	{
		methodId = "CogTrust";
	}

	protected Map<String, Map<String, Double>> train()
	{
		Map<String, Map<String, Double>> userTrustMap = new HashMap<>();
		Map<String, Double> userIntentions = calcUserIntentions();

		double bias = params.COGTRUST_BIAS;
		double alpha = params.COGTRUST_ALPHA;
		int count = 0;
		for (String trustor : testUserRatingsMap.keySet())
		{
			count++;
			if (count % 100 == 0) logger.debug("Training progresss: {}/{}", count, testUserRatingsMap.size());
			Map<String, Rating> asRatings = userRatingsMap.get(trustor);
			if (asRatings == null) continue;
			for (String trustee : userRatingsMap.keySet())
			{
				if (trustee.equals(trustor)) continue;
				double trust = bias;
				Map<String, Rating> bsRatings = userRatingsMap.get(trustee);
				if (asRatings == null || asRatings.size() < 1 || bsRatings == null || bsRatings.size() < 1) trust += 0;
				else
				{
					List<Double> as = new ArrayList<>();
					List<Double> bs = new ArrayList<>();
					for (Entry<String, Rating> en : asRatings.entrySet())
					{
						String item = en.getKey();
						if (bsRatings.containsKey(item))
						{
							as.add(en.getValue().getRating());
							bs.add(bsRatings.get(item).getRating());
						}
					}
					int size = as.size();
					if (size < 1) trust += 0.0;
					else
					{
						double similarity = Sims.pcc(as, bs);
						if (Double.isNaN(similarity)) similarity = 0.0;

						double significance = 1.0;
						// double significance = 1 - 1.0 / size;
						double capability = similarity * significance;

						double intention = 0.0;
						if (userIntentions.containsKey(trustee)) intention = userIntentions.get(trustee);

						/**
						 * Three methods can be used to predict trust value: (1) trust = capability * intention + bias ;
						 * (2) trust = alpha * capability + (1-alpha) * intention + bias; (3) trust =
						 * hamonicMean(capability, intention) + bias.
						 */
						trust = alpha * capability + (1 - alpha) * intention + bias;

						if (trust > 1) trust = 1.0;
						if (trust < -1) trust = -1.0;
					}
				}

				if (trust > params.TRUST_THRESHOLD)
				{
					Map<String, Double> trusteeMap = null;
					if (userTrustMap.containsKey(trustor)) trusteeMap = userTrustMap.get(trustor);
					else trusteeMap = new HashMap<>();
					trusteeMap.put(trustee, trust);
					userTrustMap.put(trustor, trusteeMap);
				}
			}
		}

		return userTrustMap;
	}

	private Map<String, Double> calcUserIntentions()
	{
		Map<String, Double> userIntensMap = new HashMap<>();
		Set<String> as = userRatingsMap.keySet();
		Set<String> bs = new HashSet<>(as);
		Set<String> correctItems = new HashSet<>();

		double epsilon = params.COGTRUST_EPSILON;
		for (String a : as)
		{
			Map<String, Rating> asRatings = userRatingsMap.get(a);
			if (asRatings == null) continue;

			int size = asRatings.size();
			for (String b : bs)
			{
				if (a.equals(b)) continue;
				Map<String, Rating> bsRatings = userRatingsMap.get(b);
				if (bsRatings == null) continue;
				for (Rating br : bsRatings.values())
				{
					String item = br.getItemId();
					double rating = br.getRating();

					if (correctItems.contains(item)) continue;
					if (asRatings.containsKey(item))
					{
						double diff = Math.abs(rating - asRatings.get(item).getRating());
						if (diff < epsilon) correctItems.add(item);
					}
				}
			}

			double intention = correctItems.size() / (size + 0.0);
			userIntensMap.put(a, intention);
		}

		return userIntensMap;
	}

}
