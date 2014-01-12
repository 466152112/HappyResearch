package happy.research.cf;

import happy.coding.math.Maths;
import happy.coding.math.Sims;
import happy.coding.math.Stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Performance
{

	private String									method		= null;
	private Measures								ms			= new Measures();
	public final static int[]						cutoffs		= { 5, 10, 15, 20 };

	/* predictable relevant ratings: {user, {item, rating}} */
	private Map<String, Map<String, Prediction>>	userPreds	= new HashMap<>();

	public Performance(String method)
	{
		this.method = method;
	}

	public int size()
	{
		return userPreds.size();
	}

	/**
	 * performance of ranked recommendation list
	 * 
	 * @param topN the size of recommendation list
	 * @return the measurements for the ranking performance
	 */
	public Measures ranking(Map<String, Map<String, Rating>> test, boolean sort_by_prediction)
	{
		// cut-off at position N
		Map<String, List<Prediction>> ranked_preds = null;

		if (sort_by_prediction) ranked_preds = this.sortByPred();
		else ranked_preds = this.sortByConf(cutoffs[0]);

		// rated => relevant 
		for (int cutoff : cutoffs)
		{
			List<Double> precisions = new ArrayList<>();
			List<Double> recalls = new ArrayList<>();
			List<Double> RRs = new ArrayList<>();
			List<Double> APs = new ArrayList<>();
			List<Double> nDCGs = new ArrayList<>();

			for (Entry<String, List<Prediction>> en : ranked_preds.entrySet())
			{
				String user = en.getKey();

				List<Prediction> rec_preds = en.getValue();
				Map<String, Rating> test_ratings = test.get(user);

				double iDCG = 0;
				int n_rated = 0;
				if (test_ratings.size() > cutoff) n_rated = cutoff;
				else n_rated = test_ratings.size();

				for (int i = 0; i < n_rated; i++)
				{
					iDCG += 1.0 / Maths.log(i + 2, 2);
				}

				int n_rec = rec_preds.size();
				if (n_rec > cutoff) rec_preds = rec_preds.subList(0, cutoff);

				double DCG = 0;
				int tp = 0;
				double RR = 0;
				boolean first_relevant = false;
				List<Double> pks = new ArrayList<>();

				for (int i = 0; i < rec_preds.size(); i++)
				{
					Prediction pred = rec_preds.get(i);
					double truth = pred.getTruth();
					if (truth > 0)
					{
						int rank = i + 1;
						DCG += 1.0 / Maths.log(rank + 1, 2);

						tp++;

						// precision at k
						pks.add((tp + 0.0) / rank);

						if (!first_relevant)
						{
							RR = 1.0 / rank;
							first_relevant = true;
						}
					}
				}

				precisions.add((tp + 0.0) / n_rec);
				recalls.add((tp + 0.0) / test_ratings.size());
				RRs.add(RR);
				APs.add(Stats.mean(pks));
				nDCGs.add(DCG / iDCG);
			}

			double precision = Stats.mean(precisions);
			double recall = Stats.mean(recalls);
			double F1 = Stats.hMean(precision, recall);

			ms.addPrecision(cutoff, precision);
			ms.addRecall(cutoff, recall);
			ms.addF1(cutoff, F1);
			ms.addNDCG(cutoff, Stats.mean(nDCGs));
			ms.addMAP(cutoff, Stats.mean(APs));
			ms.addMRR(cutoff, Stats.mean(RRs));

		}

		return ms;
	}

	/**
	 * performance of predicting items' ratings
	 * 
	 * @return the measurements for the predictive performance
	 */
	public Measures prediction(Map<String, Map<String, Rating>> test)
	{
		List<Double> AEs = new ArrayList<>();
		List<Double> SEs = new ArrayList<>();
		List<Double> AUEs = new ArrayList<>(); // absolute user errors

		List<Double> confs = new ArrayList<>(); // all the prediction confidence

		int covered_ratings = 0, covered_users = 0;
		int total_ratings = 0, total_users = 0;
		for (Entry<String, Map<String, Rating>> en : test.entrySet())
		{
			// test user
			String user = en.getKey();
			total_users++;

			// test ratings
			Map<String, Rating> trs = en.getValue();
			total_ratings += trs.size();

			// predicted ratings
			Map<String, Prediction> preds = userPreds.get(user);
			if (preds == null) continue;
			covered_users++;

			List<Double> UEs = new ArrayList<>();
			for (Prediction pred : preds.values())
			{
				double e = pred.error();
				AEs.add(e);
				confs.add(pred.getConf());

				SEs.add(e * e);
				UEs.add(e);

				covered_ratings++;
			}

			double aue = Stats.mean(UEs);
			AUEs.add(aue);
		}

		ms.setMAE(Stats.mean(AEs));
		ms.setMAUE(Stats.mean(AUEs));
		ms.setMACE(Stats.average(AEs, confs));
		ms.setUC(covered_users, total_users);
		ms.setRMSE(Math.sqrt(Stats.mean(SEs)));
		ms.setRC(covered_ratings, total_ratings);

		return ms;
	}

	/**
	 * diversity performance of ranked recommendation list
	 * 
	 * @param data: including both training and testing data: 
	 * 				0) training data: {user, {item, rating}}
	 * 				1) training data: {item, {user, rating}}
	 * 				2) testing  data: {item, {user, rating}}
	 * @param user_test:  testing data:  {user, {item, rating}}
	 * @param topN the size of recommendation list
	 * @return the measurements for the diversity performance of ranked recommendation list
	 */
	public Measures diversity(Map<String, Map<String, Rating>>[] data, int topN, boolean sort_by_prediction)
	{
		Map<String, Map<String, Rating>> trainUsers = data[0];
		Map<String, Map<String, Rating>> trainItems = data[1];
		Map<String, Map<String, Rating>> testItems = data[2];

		Map<String, List<Prediction>> ranked_preds = null;
		if (sort_by_prediction) ranked_preds = this.sortByPred();
		else ranked_preds = this.sortByConf(topN);

		/* compute item dissimilarity */
		Map<String, Map<String, Double>> itemDisSims = new HashMap<>();
		List<String> items = new ArrayList<>(testItems.keySet());
		for (int i = 0; i < items.size(); i++)
		{
			String itemA = items.get(i);

			Map<String, Double> itemDiss = null;
			Map<String, Rating> trainRatingsA = trainItems.get(itemA);
			if (trainRatingsA == null || trainRatingsA.size() < 1) continue;

			if (itemDisSims.containsKey(itemA)) itemDiss = itemDisSims.get(itemA);
			else itemDiss = new HashMap<>();

			for (int j = i + 1; j < items.size(); j++)
			{
				String itemB = items.get(j);
				Map<String, Rating> trainRatingsB = trainItems.get(itemB);
				if (trainRatingsB == null || trainRatingsB.size() < 1) continue;

				List<Double> r1 = new ArrayList<>();
				List<Double> r2 = new ArrayList<>();

				for (String userA : trainRatingsA.keySet())
				{
					if (trainRatingsB.containsKey(userA))
					{
						r1.add(trainRatingsA.get(userA).getRating());
						r2.add(trainRatingsB.get(userA).getRating());
					}
				}

				double sim = Sims.pcc(r1, r2);
				if (!Double.isNaN(sim)) itemDiss.put(itemB, 1 - sim);
			}
			itemDisSims.put(itemA, itemDiss);
		}

		/* inter-user diversity */
		List<Double> UDs = new ArrayList<>();

		/* intra-user diversity (item novelty) */
		List<Double> INs = new ArrayList<>();

		/* set diversity */
		List<Double> SDs = new ArrayList<>();
		int n_train_users = trainUsers.size();

		List<String> users = new ArrayList<>(ranked_preds.keySet());

		for (int i = 0; i < users.size(); i++)
		{
			String userA = users.get(i);
			List<Prediction> asPreds = ranked_preds.get(userA);

			// inter-user diversity 
			for (int j = i + 1; j < users.size(); j++)
			{
				String userB = users.get(j);
				List<Prediction> bsPreds = ranked_preds.get(userB);

				Map<String, Prediction> item_preds = toItemPredMap(bsPreds);

				int count = 0;
				for (Prediction ap : asPreds)
				{
					String item = ap.getItemId();
					if (item_preds.containsKey(item)) count++;
				}
				double UD = 1 - (count + 0.0) / topN;
				UDs.add(UD);
			}

			// intra-user diversity 
			int n_items = asPreds.size();
			double ins = 0;
			for (Prediction pred : asPreds)
			{
				String item = pred.getItemId();
				Map<String, Rating> userRatings = trainItems.get(item);
				if (userRatings == null) continue;

				double in = 1 - Maths.log(userRatings.size(), n_train_users);
				ins += in;
			}
			INs.add(ins / n_items);

			// set diversity
			for (int j = 0; j < asPreds.size(); j++)
			{
				String item1 = asPreds.get(j).getItemId();

				for (int k = j + 1; k < asPreds.size(); k++)
				{
					String item2 = asPreds.get(k).getItemId();

					double dist = 0;

					if (itemDisSims.containsKey(item1))
					{
						Map<String, Double> distSims = itemDisSims.get(item1);
						if (distSims.containsKey(item2))
						{
							dist = distSims.get(item2);
							SDs.add(dist);
						}
					} else if (itemDisSims.containsKey(item2))
					{
						Map<String, Double> distSims = itemDisSims.get(item2);
						if (distSims.containsKey(item1))
						{
							dist = distSims.get(item1);
							SDs.add(dist);
						}
					}

				}
			}
		}
		ms.setUD(Stats.mean(UDs));
		ms.setIN(Stats.mean(INs));
		ms.setSD(Stats.mean(SDs));

		return ms;
	}

	private Map<String, Prediction> toItemPredMap(List<Prediction> preds)
	{
		Map<String, Prediction> item_preds = new HashMap<>();

		for (Prediction pred : preds)
		{
			String item = pred.getItemId();
			item_preds.put(item, pred);
		}

		return item_preds;
	}

	/**
	 * @return a sorted recommendation list sorted by predictions
	 */
	protected Map<String, List<Prediction>> sortByPred()
	{
		Map<String, List<Prediction>> recLists = new HashMap<>();

		for (Entry<String, Map<String, Prediction>> en : userPreds.entrySet())
		{
			String user = en.getKey();
			List<Prediction> preds = new ArrayList<>(en.getValue().values());

			Collections.sort(preds, new Comparator<Prediction>() {

				@Override
				public int compare(Prediction p1, Prediction p2)
				{
					double pred1 = p1.getPred();
					double pred2 = p2.getPred();

					if (pred1 > pred2) return 1;
					else if (pred1 == pred2) return 0;
					else return -1;
				}
			});

			Collections.reverse(preds);
			recLists.put(user, preds);
		}

		return recLists;
	}

	/**
	 * @return a sorted recommendation list sorted by predictions
	 */
	protected Map<String, List<Prediction>> sortByConf(int topN)
	{
		Map<String, List<Prediction>> recLists = new HashMap<>();

		for (Entry<String, Map<String, Prediction>> en : userPreds.entrySet())
		{
			String user = en.getKey();
			List<Prediction> preds = new ArrayList<>(en.getValue().values());

			Collections.sort(preds, new Comparator<Prediction>() {

				@Override
				public int compare(Prediction p1, Prediction p2)
				{
					double pred1 = p1.getConf();
					double pred2 = p2.getConf();

					if (pred1 > pred2) return 1;
					else if (pred1 == pred2) return 0;
					else return -1;
				}
			});

			Collections.reverse(preds);
			if (preds.size() > topN) preds = preds.subList(0, topN);
			recLists.put(user, preds);
		}

		return recLists;
	}

	/**
	 * @return a sorted recommendation list sorted by ground truth
	 */
	protected Map<String, List<Prediction>> sortByTruth(int topN)
	{
		Map<String, List<Prediction>> recLists = new HashMap<>();

		for (Entry<String, Map<String, Prediction>> en : userPreds.entrySet())
		{
			String user = en.getKey();
			List<Prediction> preds = new ArrayList<>(en.getValue().values());

			Collections.sort(preds, new Comparator<Prediction>() {

				@Override
				public int compare(Prediction p1, Prediction p2)
				{
					double truth1 = p1.getTruth();
					double truth2 = p2.getTruth();

					if (truth1 > truth2) return 1;
					else if (truth1 == truth2) return 0;
					else return -1;
				}
			});

			Collections.reverse(preds);
			if (preds.size() > topN) preds = preds.subList(0, topN);
			recLists.put(user, preds);
		}

		return recLists;
	}

	public synchronized void addPredicts(Prediction pred)
	{
		String user = pred.getUserId();
		String item = pred.getItemId();

		Map<String, Prediction> rs = null;
		if (userPreds.containsKey(user)) rs = userPreds.get(user);
		else rs = new HashMap<>();

		rs.put(item, pred);
		userPreds.put(user, rs);
	}

	public String getMethod()
	{
		return method;
	}

}
