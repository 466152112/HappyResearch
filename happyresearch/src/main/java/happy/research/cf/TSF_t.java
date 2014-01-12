package happy.research.cf;

import happy.coding.math.Sims;
import happy.coding.system.Debug;
import happy.research.cf.ConfigParams.PredictMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class TSF_t extends Thread_t
{

	public TSF_t(int id)
	{
		super(id);
	}

	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		return null;
	}

	@Override
	protected void runLeaveOneOut()
	{
		for (Rating testRating : threadRatings)
		{
			reportProgress(numRating);

			String user = testRating.getUserId();
			String item = testRating.getItemId();

			double meanA = 0.0;
			Map<String, Rating> asRatings = userRatingsMap.get(user);
			if (params.PREDICT_METHOD == PredictMethod.resnick_formula)
			{
				meanA = RatingUtils.mean(asRatings, testRating);
				if (Double.isNaN(meanA)) continue;
			}

			/* user-based collaborative filtering */
			Map<String, Double>[] nnScoresMap = buildUserNNs(testRating);
			double sum = 0.0;
			double weights = 0.0;

			if (nnScoresMap != null)
			{
				Map<String, Double> nnSims = nnScoresMap[0];
				Map<String, Double> nnTrusts = nnScoresMap[1];
				Map<String, Double> nnRatings = nnScoresMap[2];

				// nearest neighbors
				if (nnSims != null && nnSims.size() > 0)
				{
					for (Entry<String, Double> entry : nnSims.entrySet())
					{
						String nn = entry.getKey();
						if (nn.equals(user)) continue;

						double bsRating = nnRatings.get(nn);
						double meanB = 0.0;
						if (params.PREDICT_METHOD == PredictMethod.resnick_formula)
						{
							Map<String, Rating> bsRatings = userRatingsMap.get(nn);
							meanB = RatingUtils.mean(bsRatings, null);
							if (Double.isNaN(meanB)) continue;
						}

						double score = entry.getValue();

						sum += score * (bsRating - meanB);
						weights += Math.abs(score);
					}

				}

				// trusted neighbors
				if (nnTrusts != null && nnTrusts.size() > 0)
				{
					for (Entry<String, Double> entry : nnTrusts.entrySet())
					{
						String nn = entry.getKey();
						if (nn.equals(user)) continue;

						double bsRating = nnRatings.get(nn);
						double meanB = 0.0;
						if (params.PREDICT_METHOD == PredictMethod.resnick_formula)
						{
							Map<String, Rating> bsRatings = userRatingsMap.get(nn);
							meanB = RatingUtils.mean(bsRatings, null);
							if (Double.isNaN(meanB)) continue;
						}

						double score = entry.getValue();

						sum += score * (bsRating - meanB);
						weights += Math.abs(score);
					}

				}
			}

			double pTeCF = 0;
			if (weights > 0.0) pTeCF = meanA + sum / weights;

			/* item-based collaborative filtering */
			double pSeCF = 0;
			if (Debug.OFF)
			{
				Map<String, Double>[] itemScoresMap = buildItemNNs(testRating);
				double sum_item = 0;
				double ws_item = 0;

				double ma = 0.0;
				Map<String, Rating> ar = itemRatingsMap.get(item);
				if (params.PREDICT_METHOD == PredictMethod.resnick_formula)
				{
					ma = RatingUtils.mean(ar, testRating);
					if (Double.isNaN(ma)) continue;
				}

				if (itemScoresMap != null)
				{
					Map<String, Double> nnSims = itemScoresMap[0];
					Map<String, Double> nnRatings = itemScoresMap[1];

					// item nearest neighbors
					if (nnSims != null && nnSims.size() > 0)
					{
						for (Entry<String, Double> entry : nnSims.entrySet())
						{
							String nn = entry.getKey();
							if (nn.equals(item)) continue;

							double bsRating = nnRatings.get(nn);
							double meanB = 0.0;
							if (params.PREDICT_METHOD == PredictMethod.resnick_formula)
							{
								Map<String, Rating> bsRatings = itemRatingsMap.get(nn);
								meanB = RatingUtils.mean(bsRatings, null);
								if (Double.isNaN(meanB)) continue;
							}

							double score = entry.getValue();

							sum_item += score * (bsRating - meanB);
							ws_item += Math.abs(score);
						}

					}
				}
				if (ws_item > 0.0) pSeCF = ma + sum_item / ws_item;
			}
			/* fusion of prediction */
			double pred = 0;
			if (pTeCF == 0 && pSeCF != 0) pred = pSeCF;
			else if (pTeCF != 0 && pSeCF == 0) pred = pTeCF;
			else pred = 2 * pTeCF * pSeCF / (pTeCF + pSeCF);

			if (pred > 0) pf.addPredicts(new Prediction(testRating, pred));
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Double>[] buildItemNNs(Rating testRating)
	{
		String testUser = testRating.getUserId();
		String testItem = testRating.getItemId();

		Map<String, Rating> asRatings = itemRatingsMap.get(testItem);
		if (asRatings == null) return null;

		Map<String, Double> nnSims = new HashMap<>();
		Map<String, Double> nnScores = new HashMap<>();

		for (Entry<String, Map<String, Rating>> entry : itemRatingsMap.entrySet())
		{
			String item = entry.getKey();
			if (item.equalsIgnoreCase(testItem)) continue;

			Map<String, Rating> bsRatings = entry.getValue();
			if (bsRatings == null) continue;

			if (!bsRatings.containsKey(testUser)) continue;
			double mu_b = RatingUtils.mean(bsRatings, null);

			List<Double> as = new ArrayList<>();
			List<Double> bs = new ArrayList<>();
			for (String user : asRatings.keySet())
			{
				if (user.equalsIgnoreCase(testUser)) continue;
				if (bsRatings.containsKey(user))
				{
					double ar = asRatings.get(user).getRating();
					double br = bsRatings.get(user).getRating();
					as.add(ar);
					bs.add(br);
				}
			}
			if (as.size() < 1) continue;

			double icos = Sims.pcc(as, bs);
			if (Double.isNaN(icos)) icos = 0;

			double ij = as.size() / (asRatings.size() - 1 + bsRatings.size() - as.size() + 0.0);
			double sim = icos * ij;

			if (sim > 0)
			{
				double ir = bsRatings.size() / (userRatingsMap.size() + 0.0) * mu_b;
				nnSims.put(item, sim * ir);
				nnScores.put(item, bsRatings.get(testUser).getRating());
			}
		}

		return new Map[] { nnSims, nnScores };
	}

	@SuppressWarnings("unchecked")
	private Map<String, Double>[] buildUserNNs(Rating testRating)
	{
		String testUser = testRating.getUserId();
		String testItem = testRating.getItemId();

		Map<String, Rating> asRatings = userRatingsMap.get(testUser);
		if (asRatings == null) return null;
		double mu_a = RatingUtils.mean(asRatings, testRating);
		if (Double.isNaN(mu_a)) mu_a = (Dataset.maxScale + Dataset.minScale) / 2.0;

		Map<String, Double> nnSims = new HashMap<>();
		Map<String, Double> nnTrusts = new HashMap<>();
		Map<String, Double> nnScores = new HashMap<>();

		for (Entry<String, Map<String, Rating>> entry : userRatingsMap.entrySet())
		{
			String user = entry.getKey();
			if (user.equals(testUser)) continue;

			Map<String, Rating> bsRatings = entry.getValue();
			if (bsRatings == null) continue;
			if (!bsRatings.containsKey(testItem)) continue;
			double mu_b = RatingUtils.mean(bsRatings, null);

			List<Double> as = new ArrayList<>();
			List<Double> bs = new ArrayList<>();
			double es = 0;
			for (String item : asRatings.keySet())
			{
				if (item.equalsIgnoreCase(testItem)) continue;
				if (bsRatings.containsKey(item))
				{
					double ar = asRatings.get(item).getRating();
					double br = bsRatings.get(item).getRating();
					as.add(ar);
					bs.add(br);

					double pr = mu_a + (br - mu_b);
					double e = pr - ar;

					es += e * e;
				}
			}
			if (as.size() < 1) continue;

			double upcc = Sims.pcc(as, bs);
			if (Double.isNaN(upcc)) upcc = 0;

			double uj = as.size() / (asRatings.size() - 1 + bsRatings.size() - as.size() + 0.0);
			double sim = upcc * uj;

			double msd = 1 - es / as.size();
			double trust = msd * uj;

			double lambda = 0.15;// [0.05, 0.15]

			if (sim > 0 || trust > lambda)
			{
				if (sim > 0) nnSims.put(user, sim);
				if (trust > lambda) nnTrusts.put(user, trust);

				nnScores.put(user, bsRatings.get(testItem).getRating());
			}

		}

		return new Map[] { nnSims, nnTrusts, nnScores };
	}

}
