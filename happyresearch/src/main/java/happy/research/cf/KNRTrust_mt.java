package happy.research.cf;

import happy.research.utils.TrustUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implement the <em>kNR trust-based</em> method proposed in paper <em>Trust-Based Collaborative Filtering</em>
 * 
 * @author guoguibing
 */
public class KNRTrust_mt extends DefaultTrust_mt
{
	public KNRTrust_mt() throws Exception
	{
		methodId = "kNRTrust";
	}

	protected Map<String, Map<String, Double>> train()
	{
		Map<String, Map<String, Double>> trustMap = new HashMap<>();
		int count = 0;
		for (String trustor : testUserRatingsMap.keySet())
		{
			count++;
			if (count % 50 == 0) logger.debug("Training progresss: {}/{}", count, testUserRatingsMap.size());
			Map<String, Rating> asRatings = userRatingsMap.get(trustor);
			if (asRatings == null) continue;
			for (String trustee : userRatingsMap.keySet())
			{
				if (trustee.equals(trustor)) continue;
				Map<String, Rating> bsRatings = userRatingsMap.get(trustee);
				if (bsRatings == null) continue;

				List<Double> as = new ArrayList<>();
				List<Double> bs = new ArrayList<>();
				for (String itemId : asRatings.keySet())
				{
					if (bsRatings.containsKey(itemId))
					{
						as.add(asRatings.get(itemId).getRating());
						bs.add(bsRatings.get(itemId).getRating());
						break;
					}
				}
				double trust = TrustUtils.kNRTrust(as, bs);
				if (Double.isNaN(trust)) continue;

				Map<String, Double> trusteeMap = null;
				if (trustMap.containsKey(trustor)) trusteeMap = trustMap.get(trustor);
				else trusteeMap = new HashMap<>();

				trusteeMap.put(trustee, trust);
				trustMap.put(trustor, trusteeMap);
			}
		}

		return trustMap;
	}

}
