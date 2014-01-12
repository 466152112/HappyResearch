package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.math.Sims;
import happy.coding.system.Debug;
import happy.research.cf.ConfigParams.PredictMethod;
import happy.research.utils.SimUtils;
import happy.research.utils.TrustUtils;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author guoguibing
 */
public class Merge_tj extends Thread_t
{
	public static double		alpha			= 0;
	public static double		beta			= 0;
	public static double		lambda			= 0;

	private int					max_search_len	= params.TRUST_PROPERGATION_LENGTH;

	private Map<String, Double>	itemRatings		= null;
	private Map<String, Double>	itemConfids		= null;

	public Merge_tj(int id, String pattern)
	{
		super(id);
	}

	/**
	 * breadth first search for the shortest distance between two users in the WOT
	 */
	protected Map<String, Double> trust_len(String user)
	{
		Map<String, Double> tns = userTNsMap.get(user);
		if (tns == null) return null;

		List<String> visited_users = new ArrayList<>();
		List<String> current_users = new ArrayList<>(tns.keySet());

		Map<String, Double> tnScores = new HashMap<>();

		for (int len = 1; len <= max_search_len; len++)
		{
			List<String> next_users = new ArrayList<>();

			visited_users.addAll(current_users);

			for (String tn : current_users)
			{
				if (tn.equals(user)) continue;

				tnScores.put(tn, 1.0 / len);

				/* prepare next len */
				Map<String, Double> n_tns = userTNsMap.get(tn);
				if (n_tns != null && len < max_search_len)
				{
					for (String n_tn : n_tns.keySet())
					{
						if (n_tn.equals(user)) continue;

						if (!visited_users.contains(n_tn))
						{
							next_users.add(n_tn);
						}
					}
				}
			}
			if (next_users.size() > 0) current_users = next_users;
			else break;
		}

		return tnScores;
	}

	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		String user = testRating.getUserId();
		Map<String, Double> tnScores = null;
		try
		{
			if (Debug.OFF)
			{
				tnScores = FileIO.readAsIDMap(trustDirPath + user + ".txt");
			} else
			{
				tnScores = trust_len(user);
				if (Debug.OFF)
				{
					if (tnScores != null && tnScores.size() > 0)
					{
						FileIO.writeMap(trustDirPath + user + "-" + max_search_len + ".txt", tnScores);
					}
				}
			}

		} catch (FileNotFoundException e)
		{
			// logger.debug("No trusted neighbours for user {}", user);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		if (tnScores == null)
		{
			tnScores = new HashMap<>();
			tnScores.put(user, 1.0);
		} else
		{
			tnScores.put(user, 1.0);
			infer_trust(user, tnScores);
		}

		Map<String, Double>[] mergedMaps = mergeRatings(testRating, tnScores);
		Map<String, Double>[] nnData = findoutNNs(testRating, mergedMaps[0], mergedMaps[1], tnScores);

		itemRatings = mergedMaps[0];
		itemConfids = mergedMaps[1];

		return nnData;
	}

	private void infer_trust(String user, Map<String, Double> tnScores)
	{
		/* a big step: infer more trust */
		int n_infer_trust = params.readInt("merge.infer.trust");
		if (n_infer_trust > 0)
		{
			// find neighbors with no direct trust relations but with commonly trusted neighbors;
			if (tnScores.size() > 1)
			{
				int tnSize = 0;
				Map<String, Double> infers = new HashMap<>();
				for (String u : userTNsMap.keySet())
				{
					if (u.equals(user)) continue;
					if (tnScores.containsKey(u)) continue;

					Map<String, Double> utns = userTNsMap.get(u);

					int count = 0;
					for (String v : tnScores.keySet())
					{
						if (utns.containsKey(v))
						{
							count++;
						}
					}
					if (count > 0)
					{
						//							int size = tnScores.size();
						//							double w = 0.0;
						//							if (count > 5) w = 1.0;
						//							else w = count / 5.0;
						//w = 1;

						//double trust = w * ((count + 0.0) / size);
						double trust = 2.0 / (1 + Math.exp(-count)) - 1.0;
						//Logs.debug("trust = " + trust);
						if (trust < 0.5) continue;
						//trust = 2.0 / (1 + Math.exp(-count * 0.5)) - 1.0;
						tnScores.put(u, trust);

						tnSize++;
						if (tnSize >= n_infer_trust) break;
					}
				}

				if (Debug.OFF)
				{
					int nSize = 10;
					if (tnSize > nSize)
					{
						List<Pair> pairs = new ArrayList<>();
						for (Entry<String, Double> en : infers.entrySet())
						{
							pairs.add(new Pair(en.getKey(), en.getValue()));
						}
						Collections.sort(pairs);

						int count = 0;
						for (int i = pairs.size() - 1; i >= 0; i--)
						{
							Pair p = pairs.get(i);
							tnScores.put(p.left, 0.7);

							count++;
							if (count > nSize) break;
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Double>[] mergeRatings(Rating testRating, Map<String, Double> tnScores)
	{
		Map<String, Double> item_rating_map = new HashMap<>();
		Map<String, Double> item_confid_map = new HashMap<>();

		String test_user = testRating.getUserId();
		Map<String, Rating> asRatings = userRatingsMap.get(test_user);
		if (asRatings != null)
		{
			for (Rating r : asRatings.values())
			{
				if (r.equals(testRating)) continue;

				item_rating_map.put(r.getItemId(), r.getRating());
				item_confid_map.put(r.getItemId(), 1.0);
			}
		}

		/* find items rated by other trusted neighbors */
		Map<String, List<Rating>> itemRatingsMap = new HashMap<>();
		for (String tn : tnScores.keySet())
		{
			if (tn.equals(test_user)) continue;

			Map<String, Rating> tnsRatings = userRatingsMap.get(tn);
			if (tnsRatings == null) continue;

			for (Rating r : tnsRatings.values())
			{
				if (r.equals(testRating)) continue;

				String itemId = r.getItemId();
				if (item_rating_map.containsKey(itemId)) continue;

				List<Rating> trs = null;
				if (itemRatingsMap.containsKey(itemId)) trs = itemRatingsMap.get(itemId);
				else trs = new ArrayList<>();
				trs.add(r);

				itemRatingsMap.put(itemId, trs);
			}
		}

		/* 
		 * merge ratings of the items rated by the trusted neighbors (self exclusive) together 
		 * 
		 * a step occurring small improvements, called a small step
		 * 
		 */
		for (Entry<String, List<Rating>> en : itemRatingsMap.entrySet())
		{
			String item = en.getKey();
			List<Rating> ratings = en.getValue();

			double sum = 0.0;
			double weights = 0.0;
			int positive = 0, negative = 0;
			for (Rating r : ratings)
			{
				String tn = r.getUserId();
				double trust = tnScores.get(tn);
				double weight = 0.0;

				/* a small step */
				if (Debug.ON)
				{
					/**
					 * the best performance is set alpha=1.0; otherwise the best performance is FilmTrust (0.5, 0.3, 0.2)
					 */
					double gamma = 1 - alpha - beta;

					double sim = 0.0;
					Map<String, Rating> bsRatings = userRatingsMap.get(tn);
					if (asRatings != null && bsRatings != null)
					{
						List<Double> as = new ArrayList<>();
						List<Double> bs = new ArrayList<>();
						for (Rating ar : asRatings.values())
						{
							if (ar.equals(testRating)) continue;
							if (bsRatings.containsKey(ar.getItemId()))
							{
								as.add(ar.getRating());
								bs.add(bsRatings.get(ar.getItemId()).getRating());
							}
						}

						sim = Sims.pcc(as, bs);
						//sim = (1 + sim) / 2;

						if (Double.isNaN(sim)) sim = 0.0;

					}

					List<String> asTns = new ArrayList<>(userTNsMap.get(test_user).keySet());
					List<String> bsTns = new ArrayList<>();
					if (userTNsMap.containsKey(tn)) bsTns.addAll(userTNsMap.get(tn).keySet());
					int size_tn = 0;
					if (bsTns.size() > 0) for (String at : asTns)
					{
						if (at.equals(test_user)) continue;
						if (bsTns.contains(at)) size_tn++;
					}
					double jc = (size_tn + 0.0) / (asTns.size() + bsTns.size() - size_tn);
					if (Double.isNaN(jc)) jc = 0.0;

					if (sim < 0) continue;
					weight = alpha * sim + beta * trust + gamma * jc;
					//weight = math.pow(alpha * sim + (1 - alpha) * trust, 1 - gamma * jc);
					if (weight <= 0) continue;
				} else
				{
					weight = trust;
				}

				sum += weight * r.getRating();
				weights += Math.abs(weight);

				if (r.getRating() > Dataset.median) positive++;
				else negative++;
			}
			double mergedRating = sum / weights;
			if (Double.isNaN(mergedRating)) continue;

			try
			{
				double confidence = TrustUtils.confidence(positive, negative);
				item_rating_map.put(item, mergedRating);
				item_confid_map.put(item, confidence);

			} catch (Exception e)
			{
				e.printStackTrace();
			}

		}

		/* Important step: choose the top-n confident ratings */
		int N = params.readInt("merge.num.confidence");
		if (N > 0 && item_rating_map.size() > N)
		{
			List<Pair> confs = new ArrayList<>();
			for (Entry<String, Double> en : item_confid_map.entrySet())
			{
				confs.add(new Pair(en.getKey(), en.getValue()));
			}
			Collections.sort(confs);

			Map<String, Double> rate_map = new HashMap<>();
			Map<String, Double> conf_map = new HashMap<>();
			int count = 0;
			for (int i = confs.size() - 1; i >= 0; i--)
			{
				Pair p = confs.get(i);
				rate_map.put(p.left, item_rating_map.get(p.left));
				conf_map.put(p.left, p.right);

				double conf = p.right;
				if (conf < 1.0) count++;
				if (count >= N) break;
			}

			item_rating_map = rate_map;
			item_confid_map = conf_map;
		}

		return new Map[] { item_rating_map, item_confid_map };
	}

	private double signficance(int size)
	{
		double w = 1.0;
		if (Debug.ON)
		{
			if (size > lambda) w = 1.0;
			else w = size / lambda;
		}
		return w;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Double>[] findoutNNs(Rating testRating, Map<String, Double> itemRatings,
			Map<String, Double> itemConfs, Map<String, Double> tnScores)
	{
		if (itemRatings == null || itemRatings.size() < 1) return null;

		String test_user = testRating.getUserId();
		String test_item = testRating.getItemId();

		Map<String, Double> nnScores = new HashMap<>();
		Map<String, Double> nnRatings = new HashMap<>();
		for (Entry<String, Map<String, Rating>> en : userRatingsMap.entrySet())
		{
			String userB = en.getKey();
			if (userB.equals(test_user)) continue;

			Map<String, Rating> bsRatings = en.getValue();
			if (bsRatings == null) continue;
			double bsRating = 0.0;

			if (testRating.getRating() > 0)
			{
				if (bsRatings.containsKey(test_item)) bsRating = bsRatings.get(test_item).getRating();
				if (bsRating <= 0.0) continue;
			}

			List<Double> as = new ArrayList<>();
			List<Double> bs = new ArrayList<>();
			List<Double> cs = new ArrayList<>();

			for (String itemId : bsRatings.keySet())
			{
				if (itemRatings.containsKey(itemId))
				{
					as.add(itemRatings.get(itemId));
					bs.add(bsRatings.get(itemId).getRating());
					cs.add(itemConfs.get(itemId));
				}
			}

			/*Important step to improve performance: incorporate confidence in PCC */
			double similarity = signficance(as.size()) * SimUtils.pearsonSim(as, bs, cs);
			if (Double.isNaN(similarity)) continue;

			if (similarity > params.SIMILARITY_THRESHOLD)
			{
				nnScores.put(userB, similarity);
				nnRatings.put(userB, bsRating);
			}
		}

		int N = params.kNN;
		if (N > 0 && nnScores.size() > N)
		{
			List<Pair> pairs = new ArrayList<>();
			for (Entry<String, Double> en : nnScores.entrySet())
				pairs.add(new Pair(en.getKey(), en.getValue()));
			Collections.sort(pairs);

			Map<String, Double> scores = new HashMap<>();
			Map<String, Double> ratings = new HashMap<>();

			int count = 0;
			for (int i = pairs.size() - 1; i >= 0; i--)
			{
				Pair p = pairs.get(i);
				String user = p.left;
				double score = p.right;

				count++;
				scores.put(user, score);
				ratings.put(user, nnRatings.get(user));

				if (count >= N)
				{
					nnScores = scores;
					nnRatings = ratings;
					break;
				}
			}
		}

		return new Map[] { nnScores, nnRatings };
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void runCrossValidation()
	{
		for (String testUser : threadMap.keySet())
		{
			reportProgress(numUser);

			Map<String, Rating> asRatings = userRatingsMap.get(testUser);
			if (asRatings == null) continue;
			double meanA = 0.0;
			if (params.PREDICT_METHOD == PredictMethod.resnick_formula)
			{
				meanA = RatingUtils.mean(asRatings, null);
				if (Double.isNaN(meanA)) continue;
			}

			Rating testRating = new Rating();
			testRating.setUserId(testUser);
			testRating.setItemId(0 + "");
			testRating.setRating(0);

			Map[] data = buildModel(testRating);
			if (data == null) continue;
			Map<String, Double> nnScores = data[0];
			if (nnScores.size() < 1) continue;

			if (params.TOP_N > 0)
			{
				// in this case, the classification performance is concerned rather than prediction accuracy

				/* recommending possible items */
				for (String item : testItemRatingsMap.keySet())
				{
					if (asRatings.containsKey(item)) continue;

					testRating.setItemId(item);

					/* predicate item's rating based on nearest neighbors */
					double sum = 0.0, weights = 0.0;

					for (Entry<String, Double> entry : nnScores.entrySet())
					{
						String nn = entry.getKey();
						if (nn.equals(testUser)) continue;

						Map<String, Rating> bsRatings = userRatingsMap.get(nn);
						if (bsRatings == null) continue;

						double bsRating = 0.0;
						if (bsRatings.containsKey(item)) bsRating = bsRatings.get(item).getRating();
						if (bsRating <= 0.0) bsRating = predictMissingRating(new Rating(nn, item, 0));
						if (bsRating <= 0.0) continue;

						double meanB = 0.0;
						if (params.PREDICT_METHOD == PredictMethod.resnick_formula)
						{
							meanB = RatingUtils.mean(bsRatings, null);
							if (Double.isNaN(meanB)) continue;
						}

						double score = entry.getValue();

						sum += score * (bsRating - meanB);
						weights += Math.abs(score);
					}

					if (weights <= 0.0) continue;
					double prediction = meanA + sum / weights;

					if (testItemRatingsMap.containsKey(item))
					{
						Rating r = testItemRatingsMap.get(item).get(testUser);
						if (r != null)
						{
							testRating.setRating(r.getRating());
						}
					}

					/* compute prediction confidence: prediction, distance with the merged value, merged confidence */
					double merged_rating = 0;
					double dist = 0;
					if (itemRatings.containsKey(item))
					{
						merged_rating = itemRatings.get(item);
						dist = 1 - Math.abs(prediction - merged_rating) / (Dataset.maxScale - Dataset.minScale);
					} else
					{
						dist = Math.abs(prediction - Dataset.minScale) / (Dataset.maxScale - Dataset.minScale);
						//dist = 0;
					}

					double merged_conf = 0;
					if (itemConfids.containsKey(item)) merged_conf = itemConfids.get(item);

					double conf = prediction * dist * (1 + merged_conf);

					Prediction pred = new Prediction(testRating, prediction);
					pred.setConf(conf);

					pf.addPredicts(pred);

					// reset
					testRating.setRating(0);
				}
			}// end of top-n>0 
		} // end of all test users

	}
}

class Pair implements Comparable<Pair>
{
	String	left;
	double	right;

	public Pair(String left, Double right)
	{
		this.left = left;
		this.right = right;
	}

	@Override
	public int compareTo(Pair o)
	{
		if (this.right > o.right) return 1;
		else if (this.right == o.right) return 0;
		else return -1;
	}

	@Override
	public String toString()
	{
		return left + "=" + right;
	}
}
