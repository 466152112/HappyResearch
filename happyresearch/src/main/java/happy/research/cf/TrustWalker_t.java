package happy.research.cf;

import java.util.HashMap;
import java.util.Map;

public class TrustWalker_t extends Thread_t
{

	public TrustWalker_t(int id)
	{
		super(id);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		Map<String, Double> weights = new HashMap<>();
		Map<String, Rating> ratings = new HashMap<>();

		randomTrustWalk(testRating, weights, ratings);
		return new Map[]{weights, ratings};
	}

}
