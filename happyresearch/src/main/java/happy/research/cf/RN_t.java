package happy.research.cf;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class RN_t extends Thread_t
{
	Map<String, Map<String, Double>>	userTNsCorrMap	= null;

	public RN_t(int id, Map<String, Map<String, Double>> userTNsCorrMap)
	{
		super(id);
		this.userTNsCorrMap = userTNsCorrMap;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		String user = testRating.getUserId();
		String item = testRating.getItemId();
		if (!userTNsCorrMap.containsKey(user)) return null;

		Map<String, Double> tnsCorrMap = userTNsCorrMap.get(user);
		if (tnsCorrMap == null || tnsCorrMap.size() < 1) return null;

		Map<String, Double> tnsMap = new HashMap<>();
		Map<String, Double> ratingsMap = new HashMap<>();
		for (Entry<String, Double> entry : tnsCorrMap.entrySet())
		{
			String userB = entry.getKey();
			double similarity = entry.getValue();
			if (similarity < params.SIMILARITY_THRESHOLD) continue;

			double bsRating = 0.0;
			Map<String, Rating> bsRatings = userRatingsMap.get(userB);
			if (bsRatings != null && bsRatings.containsKey(item)) bsRating = bsRatings.get(item).getRating();
			if (bsRating <= 0.0) continue; // no rating on this item

			tnsMap.put(userB, similarity);
			ratingsMap.put(userB, bsRating);
		}

		return new Map[] { tnsMap, ratingsMap };
	}

}
