package happy.research.cf;

import happy.research.utils.SimUtils.SimMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Classic Collaborative Filtering
 * 
 * @author guoguibing
 */
public class ClassicCF_mt extends DefaultCF_mt
{

	public ClassicCF_mt()
	{
		methodId = params.SIMILARITY_METHOD.name() + "-CF";
	}

	private Map<Double, Integer> probeDistanceNums()
	{
		List<String> users = new ArrayList<>(userRatingsMap.keySet());
		Map<Double, Integer> distanceNumMap = new HashMap<>();
		for (int i = 0; i < Dataset.scaleSize; i++)
			distanceNumMap.put(i * Dataset.minScale, 0);

		for (int i = 0; i < users.size(); i++)
		{
			String userA = users.get(i);
			Map<String, Rating> asRatings = userRatingsMap.get(userA);
			if (asRatings == null || asRatings.size() < 1) continue;
			for (int j = i + 1; j < users.size(); j++)
			{
				String userB = users.get(j);
				Map<String, Rating> bsRatings = userRatingsMap.get(userB);
				if (bsRatings == null || bsRatings.size() < 1) continue;

				for (Entry<String, Rating> en : asRatings.entrySet())
				{
					String ai = en.getKey();
					Rating ar = en.getValue();

					if (bsRatings.containsKey(ai))
					{
						double asRating = ar.getRating();
						double bsRating = bsRatings.get(ai).getRating();
						double distance = Math.abs(asRating - bsRating);

						int num = distanceNumMap.get(distance);
						distanceNumMap.put(distance, num + 1);
					}

				}
			}
		}

		return distanceNumMap;
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		if (params.SIMILARITY_METHOD == SimMethod.BS) distanceNum = probeDistanceNums();

		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new ClassicCF_t(i));
			threads[i].start();
		}

		for (Thread tr : threads)
			tr.join();

		return pf;
	}

}
