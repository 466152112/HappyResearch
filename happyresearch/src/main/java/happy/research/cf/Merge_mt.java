package happy.research.cf;

import happy.coding.system.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Merge: merge the ratings of trusted neighbours and form a new rating profile for active users
 * 
 * @author guoguibing
 */

public class Merge_mt extends DefaultCF_mt
{

	public Merge_mt() throws Exception
	{
		methodId = "Merge" + params.TRUST_PROPERGATION_LENGTH;
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		if (Debug.OFF)
		{
			boolean batchTest = true;
			List<String> cases = new ArrayList<>();
			if (batchTest)
			{
				for (int a = 1; a <= 2; a++)
				{
					for (int b = 1; b <= 2; b++)
					{
						for (int c = 1; c <= 3; c++)
						{
							for (int d = 1; d <= 3; d++)
							{
								String cs = "A" + a + "B" + b + "C" + c + "D" + d;
								cases.add(cs);
							}
						}
					}
				}
			} else
			{
				// the best pattern
				String pattern = "A2B1C3D3";
				// String pattern = "A2B2C1D3E3";
				cases.add(pattern);
			}

			for (String cs : cases)
			{
				printSettings.add("Pattern = " + cs);
				if (batchTest) pf = new Performance(methodId);
				for (int i = 0; i < ratingArrays.length; i++)
				{
					threads[i] = new Thread(Merge_t.newMergeCase(i, cs));
					threads[i].start();
				}
				for (Thread tr : threads)
					tr.join();

				if (batchTest) printPerformance(pf);
			}

			if (batchTest) pf = null;
			return pf;
		} else if (Debug.OFF)
		{
			/**
			 * considering the similarity of trusted neighbors here is very important to filter out some trusted
			 * neighbors with low similarity
			 */
			for (int i = 0; i < ratingArrays.length; i++)
			{
				threads[i] = new Thread(new Merge_t(i, "A2B1C3D3"));
				threads[i].start();
			}
			for (Thread tr : threads)
				tr.join();

			return pf;
		} else
		{
			List<Double> alphas = params.readDoubleList("merge.alpha");
			List<Double> betas = params.readDoubleList("merge.beta");

			double alpha = alphas.get(0);
			double beta = betas.get(0);
			double lambda = params.readDouble("merge.weight.lambda");

			// printSettings.add("merge.num.confidence = " + params.readInt("merge.num.confidence"));
			// printSettings.add("merge.infer.trust = " + params.readInt("merge.infer.trust"));

			if (params.readParam("merge.params.batch").equalsIgnoreCase("off"))
			{
				int size = alphas.size();
				for (int j = 0; j < size; j++)
				{
					if (size > 1) pf = new Performance(methodId);

					alpha = alphas.get(j);
					beta = betas.get(j);

					Merge_tj.alpha = alpha;
					Merge_tj.beta = beta <= 1 - alpha ? beta : 1 - alpha;
					Merge_tj.lambda = lambda;

					methodSettings.add("" + (float) alpha);
					methodSettings.add("" + (float) beta);
					methodSettings.add("" + (float) lambda);

					for (int i = 0; i < ratingArrays.length; i++)
					{
						threads[i] = new Thread(new Merge_tj(i, null));
						threads[i].start();
					}
					for (Thread tr : threads)
						tr.join();

					if (size > 1) printPerformance(pf);
				}

				if (size > 1) return null;
				else return pf;
			} else
			{
				for (int j = params.readInt("merge.alpha.start"); j <= 10; j++)
				{
					alpha = j * 0.1;

					Merge_tj.alpha = alpha;

					for (int k = 0; k <= 10 - j; k++)
					{
						beta = k * 0.1;
						Merge_tj.beta = beta;
						Merge_tj.lambda = lambda;

						methodSettings.add("" + (float) alpha);
						methodSettings.add("" + (float) beta);
						methodSettings.add("" + (float) lambda);

						pf = new Performance(methodId);

						for (int i = 0; i < ratingArrays.length; i++)
						{
							threads[i] = new Thread(new Merge_tj(i, null));
							threads[i].start();
						}
						for (Thread tr : threads)
							tr.join();

						printPerformance(pf);
					}

				}
				return null;
			}
		}
	}

	/**
	 * predict user B's rating on test item based on ratings of trusted neighbors
	 * 
	 * @param testRating
	 * @param userB
	 * @return
	 */
	protected double predictionByTNs(Rating testRating, String userB)
	{
		String user = testRating.getUserId();
		String item = testRating.getItemId();
		Map<String, Double> tns = userTNsMap.get(userB);
		if (tns == null) return 0.0;

		double sum = 0.0;
		double weights = 0.0;
		for (String tn : tns.keySet())
		{
			if (tn.equals(user) || tn.equals(userB)) continue;

			Map<String, Rating> tnRatings = userRatingsMap.get(tn);
			if (tnRatings != null && tnRatings.containsKey(item))
			{
				sum += tnRatings.get(item).getRating();
				weights += 1.0;
			}
		}
		if (weights <= 0.0) return 0.0;
		return sum / weights;
	}
}
