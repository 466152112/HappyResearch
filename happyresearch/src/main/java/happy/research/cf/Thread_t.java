package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.system.Dates;
import happy.coding.system.Debug;
import happy.research.cf.ConfigParams.PredictMethod;
import happy.research.utils.SimUtils;
import happy.research.utils.SimUtils.SimMethod;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;

public abstract class Thread_t extends DefaultCF_mt implements Runnable {
	protected List<Rating> threadRatings = null;
	protected List<String>[] users;
	protected Map<String, Map<String, Rating>> threadMap;

	protected int id = 0;
	protected int progress = 0;
	protected int numRating = 0;
	protected int numUser = 0;

	protected boolean postProcessing = false;
	protected List<Rating> unPredictableRatings = null;
	private Stopwatch sw = Stopwatch.createUnstarted();

	public static List<Integer> cutOffs = new ArrayList<>();

	public Thread_t(int id) {
		this.threadRatings = ratingArrays[id];
		this.id = id + 1;

		switch (params.VALIDATE_METHOD) {
		case leave_one_out:
			this.threadMap = null;
			break;
		case cross_validation:
			this.threadMap = format(threadRatings);
			break;
		}
	}

	private Map<String, Map<String, Rating>> format(List<Rating> ratings) {
		if (ratings == null || ratings.size() < 1)
			return null;
		Map<String, Map<String, Rating>> map = new HashMap<>();

		for (Rating r : ratings) {
			String user = r.getUserId();
			String item = r.getItemId();

			Map<String, Rating> itemRatings = null;
			if (map.containsKey(user))
				itemRatings = map.get(user);
			else
				itemRatings = new HashMap<>();

			itemRatings.put(item, r);
			map.put(user, itemRatings);
		}

		return map;
	}

	/**
	 * @return A map includes all pairs e.g. {<tnScores, tnRatings>, <nnScores,
	 *         nnRatings>}
	 */
	protected abstract Map<String, Double>[] buildModel(Rating testRating);

	protected Map<String, Double> updateNNRatings(Map<String, Double> nnScores, Rating testRating) {
		return null;
	}

	@Override
	public void run() {
		startThread();

		switch (params.VALIDATE_METHOD) {
		case leave_one_out:
			runLeaveOneOut();
			break;
		case cross_validation:
			runCrossValidation();
			break;
		}

		endThread();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void runCrossValidation() {
		for (String testUser : threadMap.keySet()) {
			reportProgress(numUser);

			Map<String, Rating> asRatings = userRatingsMap.get(testUser);
			if (asRatings == null)
				continue;
			double meanA = 0.0;
			if (params.PREDICT_METHOD == PredictMethod.resnick_formula) {
				meanA = RatingUtils.mean(asRatings, null);
				if (Double.isNaN(meanA))
					continue;
			}

			Rating testRating = new Rating();
			testRating.setUserId(testUser);
			testRating.setItemId(0 + "");
			testRating.setRating(0);

			Map[] data = buildModel(testRating);
			if (data == null)
				continue;
			Map<String, Double> nnScores = data[0];
			if (nnScores.size() < 1)
				continue;

			if (params.TOP_N <= 0) {
				Map<String, Rating> itemRatings = threadMap.get(testUser);
				for (Entry<String, Rating> en : itemRatings.entrySet()) {
					String item = en.getKey();

					/* kNN */
					Rating test = new Rating();
					test.setUserId(testUser);
					test.setItemId(item);
					Map<String, Double> knn = knn(nnScores, test);

					/* predicate item's rating based on nearest neighbors */
					double sum = 0.0, weights = 0.0;
					for (Entry<String, Double> entry : knn.entrySet()) {
						String nn = entry.getKey();
						if (nn.equals(testUser))
							continue;

						Map<String, Rating> bsRatings = userRatingsMap.get(nn);
						if (bsRatings == null || bsRatings.size() < 1)
							continue;

						double bsRating = 0.0;
						if (bsRatings.containsKey(item))
							bsRating = bsRatings.get(item).getRating();

						if (bsRating <= 0.0)
							continue;
						double meanB = 0.0;
						if (params.PREDICT_METHOD == PredictMethod.resnick_formula) {
							meanB = RatingUtils.mean(bsRatings, null);
							if (Double.isNaN(meanB))
								continue;
						}

						double score = entry.getValue();

						sum += score * (bsRating - meanB);
						weights += Math.abs(score);
					}
					if (weights <= 0)
						continue;
					double prediction = meanA + sum / weights;

					//double rating = testItemRatingsMap.get(item).get(testUser).getRating();
					//test.setRating(rating);

					pf.addPredicts(new Prediction(en.getValue(), prediction));
				}

			} else {
				// in this case, the classification performance is concerned rather than prediction accuracy

				/*
				 * recommending possible items from test items, other options:
				 * train items; train+test items
				 */
				for (String item : testItemRatingsMap.keySet()) {
					if (asRatings.containsKey(item))
						continue;

					testRating.setItemId(item);
					Map<String, Double> knn = knn(nnScores, testRating);

					/* predicate item's rating based on nearest neighbors */
					double sum = 0.0, weights = 0.0;

					for (Entry<String, Double> entry : knn.entrySet()) {
						String nn = entry.getKey();
						if (nn.equals(testUser))
							continue;

						Map<String, Rating> bsRatings = userRatingsMap.get(nn);
						if (bsRatings == null)
							continue;

						double bsRating = 0.0;
						if (bsRatings.containsKey(item))
							bsRating = bsRatings.get(item).getRating();
						if (bsRating <= 0.0)
							bsRating = predictMissingRating(new Rating(nn, item, 0));
						if (bsRating <= 0.0)
							continue;

						double meanB = 0.0;
						if (params.PREDICT_METHOD == PredictMethod.resnick_formula) {
							meanB = RatingUtils.mean(bsRatings, null);
							if (Double.isNaN(meanB))
								continue;
						}

						double score = entry.getValue();

						sum += score * (bsRating - meanB);
						weights += Math.abs(score);
					}

					if (weights <= 0.0)
						continue;
					double prediction = meanA + sum / weights;

					Rating r = testItemRatingsMap.get(item).get(testUser);
					if (r != null) {
						testRating.setRating(r.getRating());
					}

					pf.addPredicts(new Prediction(testRating, prediction));

					// reset
					testRating.setRating(0);
				}
			}// end of top-n>0 
		} // end of all test users

	}

	private Map<String, Double> knn(Map<String, Double> nnSims, Rating testRating) {
		if (params.kNN > 0 && nnSims.size() > params.kNN) {
			String item = testRating.getItemId();

			List<Pair> list = new ArrayList<>();
			for (Entry<String, Double> en : nnSims.entrySet()) {
				Pair p = new Pair(en.getKey(), en.getValue());
				list.add(p);
			}
			Collections.sort(list);

			Map<String, Double> knn = new HashMap<>();
			for (int i = list.size() - 1; i >= 0; i--) {
				Pair p = list.get(i);
				String user = p.left;
				Double score = p.right;

				if (!knn.containsKey(user)) {
					Map<String, Rating> itemRatings = userRatingsMap.get(user);
					if (itemRatings != null && itemRatings.containsKey(item)) {
						knn.put(user, score);
						if (knn.size() >= params.kNN)
							break;
					}
				}
			}

			return knn;

		} else
			return nnSims;

	}

	protected Map<String, Double> knn_backup(Map<String, Double> nnSims, Rating testRating) {
		if (params.kNN > 0 && nnSims.size() > params.kNN) {
			String item = testRating.getItemId();
			Map<String, Double> knn = new HashMap<>();

			List<Double> similarities = new ArrayList<>();
			for (double score : nnSims.values())
				similarities.add(score);
			Collections.sort(similarities);

			int count = 0;
			for (int i = similarities.size() - 1; i >= 0; i--) {
				double sim = similarities.get(i);
				for (Entry<String, Double> en : nnSims.entrySet()) {
					String user = en.getKey();
					Double score = en.getValue();
					if (sim == score && !knn.containsKey(user)) {
						Map<String, Rating> itemRatings = userRatingsMap.get(user);
						if (itemRatings != null && itemRatings.containsKey(item)) {
							knn.put(user, score);
							count++;
							break;
						}
					}
				}
				if (count >= params.kNN)
					break;
			}

			return knn;

		} else
			return nnSims;

	}

	protected void runLeaveOneOut() {
		for (Rating testRating : threadRatings) {
			reportProgress(numRating);

			String user = testRating.getUserId();
			String item = testRating.getItemId();

			double meanA = 0.0;
			Map<String, Rating> asRatings = userRatingsMap.get(user);
			if (params.PREDICT_METHOD == PredictMethod.resnick_formula) {
				meanA = RatingUtils.mean(asRatings, testRating);
				if (Double.isNaN(meanA))
					continue;
			}

			if (params.TOP_N <= 0) {
				Map<String, Double>[] nnScoresMap = buildModel(testRating);
				if (nnScoresMap == null)
					continue;

				/* predicate item's rating using ratings of nearest neighbors */
				double sum = 0.0;
				double weights = 0.0;

				Map<String, Double> nnScores = nnScoresMap[0];
				Map<String, Double> nnRatings = nnScoresMap[1];

				// nearest neighbors
				if (nnScores != null && nnScores.size() > 0) {
					for (Entry<String, Double> entry : nnScores.entrySet()) {
						String nn = entry.getKey();
						if (nn.equals(user))
							continue;

						Double rb = nnRatings.get(nn);
						if (rb == null)
							continue;
						double bsRating = rb.doubleValue();
						if (Double.isNaN(bsRating))
							continue;
						if (bsRating <= 0.0)
							bsRating = predictMissingRating(new Rating(nn, item, 0));
						if (bsRating <= 0.0)
							continue;
						double meanB = 0.0;
						if (params.PREDICT_METHOD == PredictMethod.resnick_formula) {
							Map<String, Rating> bsRatings = userRatingsMap.get(nn);
							meanB = RatingUtils.mean(bsRatings, null);
							if (Double.isNaN(meanB))
								continue;
						}

						double score = entry.getValue();

						sum += score * (bsRating - meanB);
						weights += Math.abs(score);
					}

				}
				if (weights <= 0.0)
					continue;
				double prediction = meanA + sum / weights;

				pf.addPredicts(new Prediction(testRating, prediction));

				/**
				 * if running time is long, it is necessary to save middle
				 * results.
				 */
				if (Debug.ON) {
					int size = pf.size();
					if (size % 500 == 0) {
						if (!cutOffs.contains(size)) {
							cutOffs.add(size);
							printPerformance(pf);
						}
					}
				}
				if (postProcessing)
					unPredictableRatings.remove(testRating);
			} else {
				// top-n recommendations
				Rating probeRating = new Rating();
				probeRating.setUserId(user);
				probeRating.setItemId(item);
				probeRating.setRating(0); // only return nearest neighbors

				Map<String, Double> nnScores = buildModel(probeRating)[0];
				if (nnScores == null || nnScores.size() < 1)
					continue;

				/* predicting all tested items' ratings */
				for (String it : itemRatingsMap.keySet()) {
					if (it != item && asRatings.containsKey(it))
						continue;

					/* predicate item's rating based on nearest neighbors */
					double sum = 0.0;
					double weights = 0.0;

					for (Entry<String, Double> entry : nnScores.entrySet()) {
						String nn = entry.getKey();
						if (nn.equals(user))
							continue;

						Map<String, Rating> bsRatings = userRatingsMap.get(nn);
						if (bsRatings == null)
							continue;

						double bsRating = 0.0;
						if (bsRatings.containsKey(it))
							bsRating = bsRatings.get(it).getRating();
						if (bsRating <= 0.0)
							continue;

						double meanB = 0.0;
						if (params.PREDICT_METHOD == PredictMethod.resnick_formula) {
							meanB = RatingUtils.mean(bsRatings, null);
							if (Double.isNaN(meanB))
								continue;
						}

						double score = entry.getValue();

						sum += score * (bsRating - meanB);
						weights += Math.abs(score);
					}

					if (weights <= 0.0)
						continue;
					double prediction = meanA + sum / weights;

					pf.addPredicts(new Prediction(testRating, prediction));
				}
			}
		}
		/* post-processing for the unable to be predicted items */
		if (postProcessing)
			doPostProcessing(unPredictableRatings);
	}

	protected void doPostProcessing(List<Rating> unPredictableRatings) {
	}

	/**
	 * Stub method for methods to predict nearest neighbor's missing rating on
	 * an item
	 * 
	 * @param rating
	 *            Rating with unknown rating value
	 */
	protected double predictMissingRating(Rating rating) {
		return 0.0;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Double>[] useTrustRatings(Rating testRating) {
		String user = testRating.getUserId();
		String item = testRating.getItemId();
		double rating = testRating.getRating();

		Map<String, Double> trustScores = null;
		Map<String, Double> trustRatings = new HashMap<>();

		try {
			trustScores = FileIO.readAsIDMap(trustDirPath + user + ".txt");
		} catch (FileNotFoundException e) {
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (trustScores == null || trustScores.size() < 1)
			return null;

		if (rating > 0) {
			// rating <=0, trusted neighbors only;
			for (String tn : trustScores.keySet()) {
				if (tn.equals(user))
					continue;
				Map<String, Rating> tnRatings = userRatingsMap.get(tn);
				if (tnRatings == null)
					continue;

				double tnRating = 0.0;
				if (tnRatings.containsKey(item))
					tnRating = tnRatings.get(item).getRating();
				if (tnRating > 0)
					trustRatings.put(tn, tnRating);
			}
		}

		return new Map[] { trustScores, trustRatings };
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Double>[] useSimilarRatings(Rating test) {
		Map<String, Double> nnSims = new HashMap<>();
		Map<String, Double> nnRatings = new HashMap<>();

		String testUser = test.getUserId();
		String testItem = test.getItemId();
		double rating = test.getRating();

		Map<String, Rating> asRatings = userRatingsMap.get(testUser);
		if (asRatings == null)
			return null;

		for (Entry<String, Map<String, Rating>> entry : userRatingsMap.entrySet()) {
			String user = entry.getKey();
			if (user.equals(testUser))
				continue;

			Map<String, Rating> bsRatings = entry.getValue();
			double bsRating = 0.0;
			if (rating > 0) {
				// rating>0: nearest neighbors with similarities
				if (!bsRatings.containsKey(testItem))
					continue;
				else
					bsRating = bsRatings.get(testItem).getRating();
				if (bsRating <= 0)
					continue;
			} // rating<=0: nearest neighbors only

			List<Double> as = new ArrayList<>();
			List<Double> bs = new ArrayList<>();
			List<String> items = new ArrayList<>();
			for (Entry<String, Rating> ar : asRatings.entrySet()) {
				String item = ar.getKey();
				if (item.equals(testItem))
					continue;
				if (bsRatings.containsKey(item)) {
					as.add(ar.getValue().getRating());
					bs.add(bsRatings.get(item).getRating());

					items.add(item);
				}
			}

			if (items.size() < 1)
				continue; // no commonly rated items

			double similarity = 0.0;
			if (params.SIMILARITY_METHOD == SimMethod.SRC) {
				/* Spearman's Rank Correlation */
				List<Double> asRankedRatings = new ArrayList<>();
				for (Rating ar : asRatings.values())
					asRankedRatings.add(ar.getRating());
				Collections.sort(asRankedRatings);

				List<Double> bsRankedRatings = new ArrayList<>();
				for (Rating br : bsRatings.values())
					bsRankedRatings.add(br.getRating());
				Collections.sort(bsRankedRatings);

				List<Double> u = new ArrayList<>();
				for (int i = 0; i < as.size(); i++) {
					double asRating = as.get(i);

					/* tied ratings get the average rank of their spot */
					double sum = 0;
					int count = 0;
					for (int j = 0; j < asRankedRatings.size(); j++) {
						if (asRankedRatings.get(j) == asRating) {
							sum += (j + 1);
							count++;
						}
					}
					u.add(sum / count);
				}

				List<Double> v = new ArrayList<>();
				for (int i = 0; i < bs.size(); i++) {
					double bRating = bs.get(i);
					double sum = 0;
					int count = 0;
					for (int j = 0; j < bsRankedRatings.size(); j++) {
						if (bsRankedRatings.get(j) == bRating) {
							sum += (j + 1);
							count++;
						}
					}
					v.add(sum / count);
				}

				similarity = SimUtils.SRCSim(u, v);
				System.out.println("SRC sim = " + similarity);

			} else
				similarity = similarity(as, bs, items, test);

			if (Double.isNaN(similarity))
				continue;

			if (params.kNN > 0 && similarity > 0.0) {// kNN
				nnSims.put(user, similarity);
				nnRatings.put(user, bsRating);
			} else if (similarity > params.SIMILARITY_THRESHOLD) {// thresholding
				nnSims.put(user, similarity);
				nnRatings.put(user, bsRating);
			}
		}

		/* KNN method */
		if (params.kNN > 0 && nnSims.size() > params.kNN && rating > 0) {
			Map<String, Double> temp = new HashMap<>();
			List<Double> similarities = new ArrayList<>();
			for (double score : nnSims.values())
				similarities.add(score);
			Collections.sort(similarities);

			int count = 0;
			for (int i = similarities.size() - 1; i >= 0; i--) {
				double sim = similarities.get(i);
				for (Entry<String, Double> en : nnSims.entrySet()) {
					String user = en.getKey();
					Double score = en.getValue();
					if (sim == score && !temp.containsKey(user)) {
						temp.put(user, score);
						count++;
						break;
					}
				}
				if (count >= params.kNN) {
					nnSims = temp;
					break;
				}
			}
		}
		return new Map[] { nnSims, nnRatings };
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Double>[] useAllData(Rating testRating) {
		String user = testRating.getUserId();
		String item = testRating.getItemId();

		Map<String, Double> nnScores = new HashMap<>();
		Map<String, Double> nnRatings = new HashMap<>();

		for (Entry<String, Map<String, Rating>> en : userRatingsMap.entrySet()) {
			String nn = en.getKey();
			if (user.equals(nn))
				continue;
			Map<String, Rating> ratings = en.getValue();

			if (ratings != null && ratings.containsKey(item)) {
				double rating = ratings.get(item).getRating();

				nnScores.put(nn, 1.0);
				nnRatings.put(nn, rating);
			}
		}

		return new Map[] { nnScores, nnRatings };
	}

	protected void reportProgress() {
		reportProgress(numRating);
	}

	protected void reportProgress(int size) {
		int step = params.RUNTIME_PROGRESS_STEP;
		if (size < step)
			step = size / 5;
		++progress;
		if (progress % step == 0) {
			sw.stop();
			long remaining = (long) ((size - progress + 0.0) / step * sw.elapsed(TimeUnit.MILLISECONDS));
			Logs.debug("{} progress: {}/{}, remaining {}", new Object[] { Thread.currentThread().getName(), progress,
					size, Dates.parse(remaining) });
			sw.start();
		}
	}

	protected void startThread() {
		Thread.currentThread().setName("[" + methodId + "][" + params.DATASET_MODE + "]" + "[Thread " + id + "]");
		Logs.debug("Start running {} ...", Thread.currentThread().getName());

		progress = 0;
		numRating = threadRatings.size();
		numUser = threadMap == null ? 0 : threadMap.size();

		sw.start();

		if (postProcessing)
			unPredictableRatings = new ArrayList<>(threadRatings);
	}

	protected void endThread() {
		Logs.debug("Finish running {}", Thread.currentThread().getName());
	}

	@Override
	protected Performance runMultiThreads() throws Exception {
		return null;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Double>[] combineData(Map<String, Double>[] dataSource1, Map<String, Double>[] dataSource2) {
		if (dataSource1 == null)
			return null;
		if (dataSource2 == null)
			return dataSource1;

		int size = dataSource1.length + dataSource2.length;
		Map<String, Double>[] data = new HashMap[size];
		for (int i = 0; i < dataSource1.length; i++)
			data[i] = dataSource1[i];
		for (int i = 0; i < dataSource2.length; i++)
			data[dataSource1.length + i] = dataSource2[i];

		return data;
	}

}
