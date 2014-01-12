package happy.research.cf;

import happy.coding.io.Strings;
import happy.coding.math.Sims;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

/**
 * @author guoguibing
 */
public class TCF_t extends Thread_t
{
	public TCF_t(int id)
	{
		super(id);
	}

	@Override
	protected Map<String, Double> updateNNRatings(Map<String, Double> nnScores, Rating testRating)
	{
		/* build the original ratings of nearest neighbors on test item */
		Map<String, Double> nnRatings = new HashMap<>();
		String testItem = testRating.getItemId();

		for (Entry<String, Double> entry : nnScores.entrySet())
		{
			String nn = entry.getKey();
			Map<String, Rating> bsRatings = userRatingsMap.get(nn);
			if (bsRatings == null) continue;

			double bsRating = 0.0;
			if (bsRatings.containsKey(testItem)) bsRating = bsRatings.get(testItem).getRating();

			nnRatings.put(nn, bsRating);
		}

		/* use TCF to update ratings of nearest neighbors */
		predictByTCF(nnRatings, testRating);

		return nnRatings;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		String testUser = testRating.getUserId();
		String testItem = testRating.getItemId();
		double rating = testRating.getRating();

		Map<String, Double> nnScores = new HashMap<>();
		Map<String, Double> nnRatings = new HashMap<>();

		Map<String, Rating> asRatings = userRatingsMap.get(testUser);

		for (Entry<String, Map<String, Rating>> entry : userRatingsMap.entrySet())
		{
			String user = entry.getKey();
			if (user.equals(testUser)) continue;

			Map<String, Rating> bsRatings = entry.getValue();
			double bsRating = 0.0;
			if (bsRatings.containsKey(testItem)) bsRating = bsRatings.get(testItem).getRating();

			List<Double> as = new ArrayList<>();
			List<Double> bs = new ArrayList<>();
			for (Entry<String, Rating> ar : asRatings.entrySet())
			{
				String item = ar.getKey();
				if (item.equals(testItem)) continue;
				if (bsRatings.containsKey(item))
				{
					as.add(ar.getValue().getRating());
					bs.add(bsRatings.get(item).getRating());
				}
			}
			double similarity = Sims.pcc(as, bs);
			if (Double.isNaN(similarity)) continue;

			/* use trust network to predict a rating for this user */
			if (similarity > params.SIMILARITY_THRESHOLD)
			{
				nnScores.put(user, similarity);
				nnRatings.put(user, bsRating);
			}
		}

		if (rating > 0) predictByTCF(nnRatings, testRating);

		int kNN = params.kNN;
		if (kNN > 0 && nnScores.size() > kNN)
		{
			List<Pair> pairs = new ArrayList<>();

			for (Entry<String, Double> en : nnScores.entrySet())
				pairs.add(new Pair(en.getKey(), en.getValue()));
			Collections.sort(pairs);
			Collections.reverse(pairs);

			Map<String, Double> ratings = new HashMap<>();
			Map<String, Double> scores = new HashMap<>();
			for (int i = 0; i < kNN; i++)
			{
				Pair p = pairs.get(i);
				String item = p.left;
				double score = p.right;

				scores.put(item, score);
				ratings.put(item, nnRatings.get(item));
			}
			nnScores = scores;
			nnRatings = ratings;
		}

		return new Map[] { nnScores, nnRatings };
	}

	// adjust ratings using TCF algorithm
	private void predictByTCF(Map<String, Double> nnRatings, Rating testRating)
	{
		String user = testRating.getUserId();
		String item = testRating.getItemId();

		/* if iteration is 2, then maximum is x^2: { tn, q = q0 + q1*x + q2*x^2} */
		Map<String, Double> q0s = new HashMap<>();
		Map<String, Double> q1s = new HashMap<>();
		Map<String, Double> q2s = new HashMap<>();

		Map<String, Integer> c0s = new HashMap<>();
		Map<String, Integer> c1s = new HashMap<>();
		Map<String, Integer> c2s = new HashMap<>();

		/* initial step */
		for (String tn : userTNsMap.keySet())
		{
			if (tn.equals(user))
			{
				q0s.put(user, 0.0);
				q1s.put(user, 0.0);
				q2s.put(user, 0.0);

				c0s.put(user, 0);
				c1s.put(user, 0);
				c2s.put(user, 0);
				continue;
			}

			double tnRating = 0.0;
			Map<String, Rating> tnRatings = userRatingsMap.get(tn);
			if (tnRatings != null && tnRatings.containsKey(item)) tnRating = tnRatings.get(item).getRating();

			q0s.put(tn, tnRating);
			q1s.put(tn, 0.0);
			q2s.put(tn, 0.0);

			c0s.put(tn, tnRating <= 0.0 ? 0 : 1);
			c1s.put(tn, 0);
			c2s.put(tn, 0);
		}

		/* adjust ratings in the trust network */
		for (int i = 0; i < params.TCF_ITERATION; i++)
		{
			Map<String, Double> q1s_old = new HashMap<>(q1s);
			Map<String, Integer> c1s_old = new HashMap<>(c1s);

			for (String u : q0s.keySet())
			{
				double q1_new = 0.0, q2_new = 0.0;
				int c1_new = 0, c2_new = 0;
				Map<String, Double> tns = userTNsMap.get(u);
				if (tns == null) continue;

				for (String tn : tns.keySet())
				{
					if (q0s.containsKey(tn)) q1_new += q0s.get(tn);
					if (q1s_old.containsKey(tn)) q2_new += q1s_old.get(tn);
					if (c0s.containsKey(tn)) c1_new += c0s.get(tn);
					if (c1s_old.containsKey(tn)) c2_new += c1s_old.get(tn);
				}
				q1s.put(u, q1_new);
				q2s.put(u, q2_new);

				c1s.put(u, c1_new);
				c2s.put(u, c2_new);
			}
		}

		/* update ratings */
		for (String nn : nnRatings.keySet())
		{
			double nnRating = nnRatings.get(nn);
			if (nnRating <= 0.0 && q0s.containsKey(nn))
			{
				double q1 = q1s.get(nn);
				double q2 = q2s.get(nn);
				int c1 = c1s.get(nn);
				int c2 = c2s.get(nn);

				nnRating = q1 / c1;
				if (Double.isNaN(nnRating))
				{
					nnRating = q2 / c2;
					if (Double.isNaN(nnRating)) nnRating = 0.0;
				}
			}

			nnRatings.put(nn, nnRating);
		}
	}

	/**
	 * This method is used to validate the implementation using the examples from the original paper
	 * <em>Trust-Based Infinitesimals for Enhanced Collaborative Filtering</em>
	 */
	@Test
	private void validate()
	{
		String user = "1";
		String item = "10";

		Rating testRating = new Rating();
		testRating.setUserId(user);
		testRating.setItemId(item);
		testRating.setRating(0.0);

		/* trust network */
		userTNsMap = new HashMap<>();
		Map<String, Double> ns = null;

		ns = new HashMap<>();
		ns.put("2", 1.0);
		ns.put("3", 1.0);
		userTNsMap.put("1", ns);

		ns = new HashMap<>();
		ns.put("1", 1.0);
		ns.put("3", 1.0);
		userTNsMap.put("2", ns);

		ns = new HashMap<>();
		ns.put("4", 1.0);
		userTNsMap.put("3", ns);

		ns = new HashMap<>();
		userTNsMap.put("4", ns);

		/* user ratings */
		userRatingsMap = new HashMap<>();
		Map<String, Rating> ratings = null;
		Rating rating = null;

		ratings = new HashMap<>();
		rating = new Rating();
		rating.setUserId("1");
		rating.setItemId(item);
		rating.setRating(0.0);
		ratings.put(item, rating);
		userRatingsMap.put("1", ratings);

		ratings = new HashMap<>();
		rating = new Rating();
		rating.setUserId("2");
		rating.setItemId(item);
		rating.setRating(3.0);
		ratings.put(item, rating);
		userRatingsMap.put("2", ratings);

		ratings = new HashMap<>();
		rating = new Rating();
		rating.setUserId("3");
		rating.setItemId(item);
		rating.setRating(4.0);
		ratings.put(item, rating);
		userRatingsMap.put("3", ratings);

		ratings = new HashMap<>();
		rating = new Rating();
		rating.setUserId("4");
		rating.setItemId(item);
		rating.setRating(5.0);
		ratings.put(item, rating);
		userRatingsMap.put("4", ratings);

		Map<String, Double> nnRatings = new HashMap<>();
		nnRatings.put("1", 0.0);
		nnRatings.put("2", 3.0);
		nnRatings.put("3", 4.0);
		nnRatings.put("4", 5.0);

		predictByTCF(nnRatings, testRating);

		String result = Strings.toString(nnRatings);
		logger.debug(result);

	}

}