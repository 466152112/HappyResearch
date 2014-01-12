package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.math.Sims;
import happy.coding.math.Stats;
import happy.coding.system.Debug;
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
 * All different Merge cases:
 * <ul>
 * <li>A: Decide which trusted neighbors to be involved.
 * <ul>
 * <li>A1: all trusted neighbors</li>
 * <li>A2: trusted neighbors whose similarity is greater than threshold</li>
 * <li>A3: trusted neighbors whose ratings are greater than threshold</li>
 * <li>A4: trusted neighbors whose reputation is greater than threshold</li>
 * <li>A5: top-k trusted neighbors</li>
 * </ul>
 * </li>
 * <li>B: Decide which ratings of trusted neighbors as candidates to be merged.
 * <ul>
 * <li>B1: keep active user's own ratings</li>
 * <li>B2: all ratings are equally</li>
 * </ul>
 * </li>
 * <li>C: Decide what weight values of trusted neighbors to be used for merging
 * ratings.
 * <ul>
 * <li>C1: trust weight</li>
 * <li>C2: similarity</li>
 * <li>C3: harmonic weight of trust and similarity</li>
 * </ul>
 * </li>
 * <li>D: Decide which (trusted or similar) neighbors to be used for predicting
 * item's rating.
 * <ul>
 * <li>D1: TNs + NNs</li>
 * <li>D2: NNs (including some trusted neighbors as similar users)</li>
 * <li>D3: NNs (excluding all trusted neighbors)</li>
 * </ul>
 * </li>
 * <li>E: Confidence calculation method
 * <ul>
 * <li>E1: ratings.std < threshold</li>
 * <li>E2: ratings.num > threshold</li>
 * <li>E3: ratings.certainty > threshold</li>
 * </ul>
 * </li>
 * <li>F: Other tricks we can try
 * <ul>
 * <li>F1: only trusted neighbors with many ratings are used.</li>
 * <li>F2: different confidence for cold or heavy users.</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * @author guoguibing
 */
public class Merge_t extends Thread_t {
	protected boolean aAllTNs = false;
	protected boolean bKeepOwnRatings = false;

	/* weight to be used for merging */
	protected boolean c1Trust = false;
	protected boolean c2Sim = false;
	protected boolean c3Harmonic = false;
	protected boolean c4Average = false;

	protected boolean d1TN_NN = false;
	protected boolean d2NN_TN = false;
	protected boolean d3NN = false;

	protected boolean e1Std = false;
	protected boolean e2Num = false;
	protected boolean e3Certainty = false;

	public static Thread_t newMergeCase(int id, String pc) {
		return new Merge_t(id, pc);
	}

	/**
	 * set the test case according to the input pattern
	 * 
	 * @param pattern
	 *            case pattern, such as A1B1C1D1
	 */
	protected void setCase(String pattern) {
		int a = Integer.parseInt(pattern.substring(1, 2));
		int b = Integer.parseInt(pattern.substring(3, 4));
		int c = Integer.parseInt(pattern.substring(5, 6));
		int d = Integer.parseInt(pattern.substring(7, 8));

		int e = 0;
		if (pattern.length() > 8)
			e = Integer.parseInt(pattern.substring(9, 10));

		if (a == 1)
			aAllTNs = true;
		else
			aAllTNs = false;

		if (b == 1)
			bKeepOwnRatings = true;
		else
			bKeepOwnRatings = false;

		switch (c) {
		case 1:
			c1Trust = true;
			c2Sim = false;
			c3Harmonic = false;
			c4Average = false;
			break;
		case 2:
			c1Trust = false;
			c2Sim = true;
			c3Harmonic = false;
			c4Average = false;
			break;
		case 3:
			c1Trust = false;
			c2Sim = false;
			c3Harmonic = true;
			c4Average = false;
			break;
		case 4:
			c1Trust = false;
			c2Sim = false;
			c3Harmonic = false;
			c4Average = true;
			break;
		}

		switch (d) {
		case 1:
			d1TN_NN = true;
			d2NN_TN = false;
			d3NN = false;
			break;
		case 2:
			d1TN_NN = false;
			d2NN_TN = true;
			d3NN = false;
			break;
		case 3:
			d1TN_NN = false;
			d2NN_TN = false;
			d3NN = true;
			break;
		}
		// logger.debug("a = {}, b = {}, c = {}, d = {}, e = {}", new Object[] {
		// a, b, c, d, e });

		switch (e) {
		case 1:
			e1Std = true;
			e2Num = false;
			e3Certainty = false;
			break;
		case 2:
			e1Std = false;
			e2Num = true;
			e3Certainty = false;
			break;
		case 3:
			e1Std = false;
			e2Num = false;
			e3Certainty = true;
			break;
		}

	}

	public Merge_t(int id, String pattern) {
		super(id);
		setCase(pattern);
	}

	protected Map<String, Double>[] buildModel(Rating testRating) {
		String user = testRating.getUserId();

		Map<String, Double> tnScores = null;
		try {
			tnScores = FileIO.readAsIDMap(trustDirPath + user + ".txt");
		} catch (FileNotFoundException e) {
			// logger.debug("No trusted neighbours for user {}", user);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (tnScores == null)
			tnScores = new HashMap<>();
		tnScores.put(user, 1.0);

		Map<String, Double> proxyRatings = new HashMap<>();
		Map<String, Double> itemCons = mergeRatings(testRating, tnScores, proxyRatings);

		Map<String, Double>[] nnData = findoutNNs(testRating, proxyRatings, itemCons, tnScores);
		Map<String, Double>[] ttData = null;
		//d1TN_NN = false;
		if (d1TN_NN)
			ttData = useTrustRatings(testRating, nnData != null ? nnData[0] : null);

		return combineData(nnData, ttData);
	}

	protected Map<String, Double> mergeRatings(Rating testRating, Map<String, Double> tnScores,
			Map<String, Double> proxyRatings) {
		Map<String, List<Rating>> itemRatingsMap = new HashMap<>();
		Map<String, Double> itemRatings = new HashMap<>();
		Map<String, Double> itemMeans = new HashMap<>();
		Map<String, Double> itemConfidences = new HashMap<>();
		Map<String, Double> tnSims = new HashMap<>();

		/* only use for storing active user's own ratings */
		Map<String, Double> activeRatings = new HashMap<>();
		Map<String, Double> itemCons = new HashMap<>();

		int knn = params.kNN;
		String user = testRating.getUserId();
		if (bKeepOwnRatings) {
			Map<String, Rating> rs = userRatingsMap.get(user);
			if (rs != null) {
				for (Rating r : rs.values()) {
					if (r == testRating)
						continue;
					activeRatings.put(r.getItemId(), r.getRating());
					itemCons.put(r.getItemId(), 1.0);
				}
			}

			knn = knn - activeRatings.size();
			if (knn <= 0) {
				proxyRatings.putAll(activeRatings);
				return itemCons;
			}
		}

		Map<String, Rating> asRatings = userRatingsMap.get(user);

		for (String tn : tnScores.keySet()) {
			if (bKeepOwnRatings && tn.equals(user))
				continue;
			Map<String, Rating> tnsRatings = userRatingsMap.get(tn);
			if (tnsRatings == null)
				continue;

			/* A: determine which trusted neighbours to be used */
			double similarity = 1.0;
			if (aAllTNs) { // A1: use all trusted neighbours, no need to compute
							// similarity
				similarity = 1.0;

				if (tn != user) {
					List<Double> as = new ArrayList<>();
					List<Double> bs = new ArrayList<>();
					for (String item : asRatings.keySet()) {
						if (item.equals(testRating.getItemId()))
							continue;
						if (tnsRatings.containsKey(item)) {
							as.add(asRatings.get(item).getRating());
							bs.add(tnsRatings.get(item).getRating());
						}
					}
					double result = Sims.pcc(as, bs);
					if (!Double.isNaN(result))
						similarity = result;
				}
			} else { // A2: use only trusted neighbours with similarity
						// constraints
				if (tn.equals(user)) { // always similar to himself
					similarity = 1.0;
				} else { // similarity with active user
					if (asRatings.size() < 3)
						similarity = 1.0;
					else {
						similarity = 0.0;

						List<Double> as = new ArrayList<>();
						List<Double> bs = new ArrayList<>();
						for (String item : asRatings.keySet()) {
							if (item.equals(testRating.getItemId()))
								continue;
							if (tnsRatings.containsKey(item)) {
								as.add(asRatings.get(item).getRating());
								bs.add(tnsRatings.get(item).getRating());
							}
						}
						double result = Sims.pcc(as, bs);
						if (!Double.isNaN(result))
							similarity = result;

					}
				}
			}

			/* find out rated items of trusted neighbours */
			if (aAllTNs || similarity > params.SIMILARITY_THRESHOLD) {
				tnSims.put(tn, similarity);

				for (Rating r : tnsRatings.values()) {
					if (r == testRating)
						continue;

					String itemId = r.getItemId();
					if (bKeepOwnRatings && activeRatings.containsKey(itemId))
						continue;

					List<Rating> trs = null;
					if (itemRatingsMap.containsKey(itemId))
						trs = itemRatingsMap.get(itemId);
					else
						trs = new ArrayList<>();
					trs.add(r);

					itemRatingsMap.put(itemId, trs);
				}
			}
		}

		/* merge ratings of items together */
		if (bKeepOwnRatings)
			itemRatings.putAll(activeRatings);
		for (Entry<String, List<Rating>> en : itemRatingsMap.entrySet()) {
			String item = en.getKey();
			List<Rating> ratings = en.getValue();

			double sum = 0.0;
			double weights = 0.0;
			int positive = 0, negative = 0;
			for (Rating r : ratings) {
				String tn = r.getUserId();
				double similarity = 0.0;
				if (tnSims.containsKey(tn))
					similarity = tnSims.get(tn);
				double trust = tnScores.get(tn);
				double weight = 0;
				if (c3Harmonic)
					weight = Stats.hMean(trust, similarity);
				else if (c1Trust)
					weight = trust;
				else if (c2Sim)
					weight = similarity;
				else if (c4Average)
					weight = (trust + similarity) / 2.0;

				sum += weight * r.getRating();
				weights += Math.abs(weight);

				if (r.getRating() > Dataset.median)
					positive++;
				else
					negative++;
			}
			double mean = sum / weights;
			if (Double.isNaN(mean) || mean <= 0.0)
				continue;

			double certainty = 0.0;
			try {
				certainty = TrustUtils.confidence(positive, negative);
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (certainty > params.CONFIDENCE_THRESHOLD) {
				itemMeans.put(item, mean);
				itemConfidences.put(item, certainty);
			}
		}

		/* ranking confidences */
		if (itemConfidences.size() > 0) {
			List<Double> confidences = new ArrayList<>(itemConfidences.values());
			Collections.sort(confidences);
			int count = 0;
			for (int i = confidences.size() - 1; i >= 0; i--) {
				double confidence = confidences.get(i);
				for (Entry<String, Double> en : itemConfidences.entrySet()) {
					String itemId = en.getKey();
					double c = en.getValue();

					if (c == confidence && !itemRatings.containsKey(itemId)) {
						count++;
						itemRatings.put(itemId, itemMeans.get(itemId));
						itemCons.put(itemId, confidence);
						break;
					}
				}

				if (count >= knn)
					break;

			}
		}

		proxyRatings.putAll(itemRatings);
		return itemCons;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Double>[] findoutNNs(Rating testRating, Map<String, Double> proxyRatings,
			Map<String, Double> itemCons, Map<String, Double> tnScores) {
		if (proxyRatings == null || proxyRatings.size() < 1)
			return null;

		String user = testRating.getUserId();
		String item = testRating.getItemId();
		double rating = testRating.getRating();

		Map<String, Double> nnScores = new HashMap<>();
		Map<String, Double> nnRatings = new HashMap<>();
		for (Entry<String, Map<String, Rating>> en : userRatingsMap.entrySet()) {
			String userB = en.getKey();
			if (userB.equals(user))
				continue;
			// if (tnScores != null && tnScores.containsKey(userB)) continue;

			Map<String, Rating> bsRatings = en.getValue();
			if (bsRatings == null)
				continue;
			double bsRating = 0.0;
			if (rating > 0) {
				if (bsRatings.containsKey(item))
					bsRating = bsRatings.get(item).getRating();
				if (bsRating <= 0.0)
					continue;
			}

			List<Double> as = new ArrayList<>();
			List<Double> bs = new ArrayList<>();
			List<Double> cs = new ArrayList<>();

			for (String itemId : bsRatings.keySet()) {
				if (proxyRatings.containsKey(itemId)) {
					as.add(proxyRatings.get(itemId));
					bs.add(bsRatings.get(itemId).getRating());
					cs.add(itemCons.get(itemId));
				}
			}

			double weight = 1.0;
			if (Debug.ON) {
				double gamma = 10.0;
				if (as.size() > gamma)
					weight = 1.0;
				else
					weight = as.size() / gamma;
			}

			double similarity = weight * SimUtils.pearsonSim(as, bs, cs);
			if (Double.isNaN(similarity))
				continue;

			if (similarity > 0.0) {
				if (tnScores.containsKey(userB))
					similarity = Stats.hMean(similarity, 1.0);
				nnScores.put(userB, similarity);
				nnRatings.put(userB, bsRating);
			}
		}

		return new Map[] { nnScores, nnRatings };
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Double>[] useTrustRatings(Rating testRating, Map<String, Double> nnScores) {
		String user = testRating.getUserId();
		String item = testRating.getItemId();

		Map<String, Double> scores = null;

		Map<String, Double> trustScores = new HashMap<>();
		Map<String, Double> trustRatings = new HashMap<>();

		try {
			scores = FileIO.readAsIDMap(trustDirPath + user + ".txt");
		} catch (FileNotFoundException e) {
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (scores == null || scores.size() < 1)
			return null;

		for (String tn : scores.keySet()) {
			if (tn.equals(user))
				continue;
			if (nnScores != null && nnScores.containsKey(tn))
				continue;

			Map<String, Rating> tnRatings = userRatingsMap.get(tn);
			if (tnRatings == null)
				continue;

			double tnRating = 0.0;
			if (tnRatings.containsKey(item))
				tnRating = tnRatings.get(item).getRating();
			if (tnRating > 0) {
				int size = userTrustorsMap.get(tn).size();
				double sim = 2.0 / (1 + Math.exp(-size)) - 1;
				if (sim > 0.9) {
					trustScores.put(tn, scores.get(tn) * sim);
					trustRatings.put(tn, tnRating);
				}
			}
		}

		return new Map[] { trustScores, trustRatings };
	}
}
