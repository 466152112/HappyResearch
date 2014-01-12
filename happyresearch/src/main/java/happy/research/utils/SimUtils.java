package happy.research.utils;

import happy.coding.io.FileIO;
import happy.coding.io.Lists;
import happy.coding.io.Logs;
import happy.coding.math.Gaussian;
import happy.coding.math.Randoms;
import happy.coding.math.Sims;
import happy.coding.math.Stats;
import happy.coding.system.Debug;
import happy.coding.system.Systems;
import happy.research.cf.ConfigParams;
import happy.research.cf.Dataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.stat.correlation.PearsonsCorrelation;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimUtils {
	private final static Logger log = LoggerFactory.getLogger(SimUtils.class);
	public final static PearsonsCorrelation pc = new PearsonsCorrelation();

	public static double alpha = 0.2, beta = 0.2;

	public enum SimMethod {
		COS, PCC, MSD, CPC, SRC, BS, PIP, SM
	};

	/**
	 * @param a
	 * @param b
	 * @param isRemoveChance
	 *            : whether to remove chance correlation
	 * @return
	 */
	private static double ijcai_bs_examples(List<Double> a, List<Double> b, boolean isRemoveChance) {
		if (a == null || b == null || a.size() < 1 || b.size() < 1 || a.size() != b.size())
			return Double.NaN;

		int N = a.size();
		int R = (int) (Dataset.maxScale / Dataset.minScale) - 1;
		// {diff level, count} map
		Map<Integer, Integer> diffMap = new HashMap<>();
		for (int i = 0; i < R + 1; i++)
			diffMap.put(i, 0);

		for (int i = 0; i < N; i++) {
			double ar = a.get(i);
			double br = b.get(i);
			double diff = Math.abs(ar - br);
			int level = (int) (diff / Dataset.minScale);

			int count = diffMap.get(level);
			diffMap.put(level, count + 1);
		}

		// System.out.println(PrintUtils.printMap(diffMap));
		double sum = 0.0, weights = 0.0;

		double chance = 1.0;
		for (int i = 0; i < R + 1; i++) {
			int count = diffMap.get(i);
			int alpha = i == 0 ? (R + 1) : 2 * (R - i + 1);
			int M2 = (R + 1) * (R + 1);
			double post = (count + alpha + 0.0) / (N + M2);
			double prio = (alpha + 0.0) / M2;
			double byChance = Math.pow(prio, count);
			chance *= byChance;

			double weight = post - prio;
			try {
				if (Debug.ON) {
					weights += post;
					sum += post * i * Dataset.minScale;
				} else {
					if (weight > 0) {
						weights += Math.abs(weight);
						sum += weight * i * Dataset.minScale;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		double d = sum / weights;
		double similarity = 1 - d / (Dataset.maxScale - Dataset.minScale);
		if (!isRemoveChance)
			chance = 0;

		double bias = 0.04;
		if (Debug.OFF)
			bias = 0;
		return Math.max(similarity * (1 - chance) - bias, 0.0);
	}

	public static boolean isPositive(double rate) {
		return rate >= Dataset.median;
	}

	public static boolean isConsistent(double r1, double r2) {
		return (r1 - Dataset.median) * (r2 - Dataset.median) >= 0;
	}

	/**
	 * Calculate the Bayesian Similarity, referring to
	 * "A Novel Bayesian Similairty for Recommender Systems" (IJCAI 2013)
	 * 
	 * @param a
	 *            user a's rating vector
	 * @param b
	 *            user b's rating vector
	 * @param priors
	 *            the prior value of each rating scales
	 * @param sd
	 *            standard deviation of ratings given on each item
	 * @param mu
	 *            mean of ratings given on each item
	 * @return Bayesian similarity
	 */
	public static double bsSim(List<Double> a, List<Double> b, List<Double> priors, List<Double> sd, List<Double> mu,
			Map<Integer, Map<Double, Double>> histos, List<Double> cf) throws Exception {
		if (a == null || b == null || a.size() < 1 || b.size() < 1 || a.size() != b.size())
			return Double.NaN;

		int N = a.size();
		int R = Dataset.scaleSize;
		// {distance level, count} map
		Map<Double, Double> evidences = new HashMap<>();
		for (int i = 0; i < R; i++)
			evidences.put(i * Dataset.minScale, 0.0);

		double numEvidence = 0;
		for (int i = 0; i < N; i++) {
			double ar = a.get(i);
			double br = b.get(i);
			double di = Math.abs(ar - br);

			double sigma = sd.get(i);
			double mean = mu.get(i);
			double conf = cf.get(i);

			double evidence = evidences.get(di);
			double ei = 0; // evidence weight

			// ours
			double x = ConfigParams.defaultInstance().X_SIGMA;
			if (sigma <= 0 || x <= 0)
				ei = 1;
			else
				ei = 1 - di / (x * sigma);

			// new factor
			double singu = 0.0;
			if (Debug.OFF) {
				// Singularity 
				double asin = 0, bsin = 0;
				if (ar > Dataset.median)
					asin = 1 - conf;
				else
					asin = conf;

				if (br > Dataset.median)
					bsin = 1 - conf;
				else
					bsin = conf;

				singu = asin * bsin;
			} else if (Debug.ON) {
				// Gaussian
				double pa = Gaussian.pdf(ar, mean, sigma);
				double pb = Gaussian.pdf(br, mean, sigma);

				singu = (1 - pa) * (1 - pb);
			} else if (Debug.OFF) {
				// Discrete
				Map<Double, Double> hist = histos.get(i);
				if (hist.containsKey(di))
					singu = 1 - hist.get(di);
				else
					singu = 1;
			}

			double semantic = 0.0;
			if (Debug.ON) {
				// Semantics

				// factor 1: proximity
				double pr = 0;
				double range = Dataset.range;
				if (isConsistent(ar, br))
					pr = 1 - di / range;
				else
					pr = -di / range;

				// factor 2: impact
				double im = 0.0;
				double s = 0.5 * (ar + br);
				if (isPositive(ar) && isPositive(br))
					im = s / Dataset.maxScale;
				else if (!isPositive(ar) && !isPositive(br))
					im = 1 - s / Dataset.maxScale;
				else
					im = -s / Dataset.maxScale;

				// factor 3: popularity
				double po = 0;
				double dist = Math.abs(s - mean);
				boolean consistent = (ar - mean) * (br - mean) >= 0;
				if (consistent)
					po = dist / range;
				else
					po = -dist / range;

				semantic = pr * im * po;
			}

			ei = alpha * ei + beta * singu + (1 - alpha - beta) * semantic;

			if (ei < -1)
				ei = -1;

			numEvidence += ei;
			evidences.put(di, evidence + ei);
		}

		double sum = 0.0, weights = 0.0;

		double chance = 1.0;
		double[] x = Lists.toArray(priors);

		for (int i = 0; i < R; i++) {
			double num = evidences.get(i * Dataset.minScale);
			double alpha = prior(x, i);

			int M2 = R * R;
			double post = (num + alpha + 0.0) / (numEvidence + M2);
			double prio = (alpha + 0.0) / M2;
			double byChance = Math.pow(prio, num);
			chance *= byChance;

			double weight = post - prio;
			try {
				if (Debug.OFF) {
					weights += post;
					sum += post * i * Dataset.minScale;
				} else {
					if (weight > 0) {
						weights += Math.abs(weight);
						sum += weight * i * Dataset.minScale;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		double d = sum / weights;
		double similarity = 1 - d / (Dataset.maxScale - Dataset.minScale);
		if (Debug.OFF)
			chance = 0.0;

		double bias = 0.04;
		if (Debug.ON)
			bias = 0;

		if (Debug.OFF) {
			return Math.max(similarity * (1 - chance) - bias, 0.0);
		} else {
			return Math.max(similarity - chance - bias, 0.0);
		}
	}

	private static double prior(double[] priors, int i) {
		double evidence = 0;
		if (i == 0) {
			for (int j = 0; j < priors.length; j++)
				evidence += priors[j] * priors[j];
		} else {
			for (int j = 0; (i + j) < priors.length; j++)
				evidence += 2 * priors[j] * priors[j + i];
		}
		return evidence;
	}

	public static double pearsonSim(List<Double> a, List<Double> b, List<Double> ca) {
		if (a == null || b == null || a.size() < 2 || b.size() < 2 || a.size() != b.size())
			return Double.NaN;
		double[] as = Lists.toArray(a);
		double[] bs = Lists.toArray(b);
		double[] cs = Lists.toArray(ca);

		return pearsonSim(as, bs, cs);
	}

	/**
	 * This example is used in my merge journal paper
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void kbs_examples() {
		Map<String, Double> merged_rates = new HashMap<>();
		Map<String, Double> merged_confs = new HashMap<>();
		merged_rates.put("i1", 4.33);
		merged_confs.put("i1", 0.19);

		merged_rates.put("i2", 4.0);
		merged_confs.put("i2", 0.38);

		merged_rates.put("i3", 5.0);
		merged_confs.put("i3", 1.0);

		merged_rates.put("i4", 3.0);
		merged_confs.put("i4", 0.25);

		merged_rates.put("i5", 2.73);
		merged_confs.put("i5", 0.47);

		merged_rates.put("i8", 1.72);
		merged_confs.put("i8", 0.47);

		Map<String, Double>[] us = new HashMap[10];
		us[2] = new HashMap<>();
		us[2].put("i1", 5.0);
		us[2].put("i3", 4.0);
		us[2].put("i5", 3.0);
		us[2].put("i8", 2.0);

		us[3] = new HashMap<>();
		us[3].put("i2", 4.0);
		us[3].put("i4", 3.0);
		us[3].put("i8", 1.0);

		us[4] = new HashMap<>();
		us[4].put("i1", 3.0);
		us[4].put("i3", 5.0);
		us[4].put("i5", 2.0);

		us[5] = new HashMap<>();
		us[5].put("i2", 4.0);
		us[5].put("i3", 4.0);
		us[5].put("i5", 3.0);
		us[5].put("i8", 3.0);

		us[6] = new HashMap<>();
		us[6].put("i2", 3.0);
		us[6].put("i3", 3.0);
		us[6].put("i4", 5.0);
		us[6].put("i5", 5.0);

		us[7] = new HashMap<>();
		us[7].put("i7", 5.0);
		us[7].put("i9", 4.0);

		us[8] = new HashMap<>();
		us[8].put("i3", 4.0);
		us[8].put("i5", 2.0);
		us[8].put("i8", 1.0);

		us[9] = new HashMap<>();
		us[9].put("i3", 4.0);
		us[9].put("i5", 5.0);
		us[9].put("i8", 5.0);

		for (int i = 2; i < us.length; i++) {
			List<Double> u = new ArrayList<>();
			List<Double> v = new ArrayList<>();
			List<Double> c = new ArrayList<>();

			Map<String, Double> ux = us[i];
			for (String item : ux.keySet()) {
				if (merged_rates.containsKey(item)) {
					u.add(merged_rates.get(item));
					c.add(merged_confs.get(item));
					v.add(ux.get(item));
				}
			}

			double cpcc = SimUtils.pearsonSim(u, v, c);
			double pcc = Sims.pcc(u, v);
			Logs.debug("u" + i + " cpcc = " + cpcc);
			Logs.debug("u" + i + "  pcc = " + pcc);
		}
	}

	/**
	 * Calculate the PIP similarity proposed by Hyung Jun Ahn [2008]:
	 * 
	 * <i>A new similarity measure for collaborative filtering to alleviate the
	 * new user cold-starting problem</i>
	 * 
	 * @param a
	 *            user a's ratings
	 * @param b
	 *            user b's ratings
	 * @param means
	 *            the average ratings of items by all users
	 * @return PIP similarity
	 */
	public static double PIPSim(List<Double> a, List<Double> b, List<Double> means) {
		double score = 0;

		/**
		 * to compute median rating
		 * 
		 * however, this is only useful for the number of rating scales is odd
		 * rather than even. But it is the way used in the paper.
		 */
		double r = (Dataset.maxScale + Dataset.minScale) / 2.0;

		for (int i = 0; i < a.size(); i++) {
			double r1 = a.get(i);
			double r2 = b.get(i);
			double ui = means.get(i);

			double agreement = (r1 - r) * (r2 - r);
			boolean agree = false;
			if (agreement < 0)
				agree = false;
			else
				agree = true;

			/* compute proximity */
			double D = 0;
			if (agree)
				D = Math.abs(r1 - r2);
			else
				D = 2 * Math.abs(r1 - r2);

			double prox = 2 * (Dataset.maxScale - Dataset.minScale) + 1 - D;
			double proximity = Math.pow(prox, 2);

			/* compute impact */
			double impact = (Math.abs(r1 - r) + 1) * (Math.abs(r2 - r) + 1);
			if (!agree)
				impact = 1.0 / impact;

			/* compute popularity */
			double val = (r1 - ui) * (r2 - ui);
			double popularity = 1.0;
			double pop = (r1 + r2) / 2.0 - ui;
			if (val > 0)
				popularity = 1 + Math.pow(pop, 2);

			/* aggregation PIP */
			score += proximity * impact * popularity;
		}

		return score;
	}

	/**
	 * Calculate confidence-aware PCC similarity
	 * 
	 * @param u
	 *            user u's ratings
	 * @param v
	 *            user v's ratings
	 * @param uc
	 *            user u's rating confidences
	 * @return confidence-aware PCC similarity
	 */
	public static double pearsonSim(double[] u, double[] v, double[] uc) {
		if (u == null || v == null || u.length < 2 || v.length < 2 || u.length != v.length)
			return Double.NaN;

		double meanA = Stats.weightedcMean(u, uc);
		double meanB = Stats.mean(v);
		double sumNum = 0.0, sumDen1 = 0.0, sumDen2 = 0.0;
		for (int i = 0; i < u.length; i++) {
			double ai = uc[i] * (u[i] - meanA);
			double bi = v[i] - meanB;
			sumNum += ai * bi;
			sumDen1 += ai * ai;
			// sumDen1 += uc[i] * (u[i] - meanA) * (u[i] - meanA);
			sumDen2 += bi * bi;
		}
		return sumNum / (Math.sqrt(sumDen1) * Math.sqrt(sumDen2));
	}

	/**
	 * Calculate Spearman's Rank Correlation (SRC)
	 * 
	 * @param u
	 *            user u's rating ranks (tied ratings get the average rank of
	 *            their spot)
	 * @param v
	 *            user v's rating ranks
	 * 
	 * @return Spearman's Rank Correlation (SRC)
	 */
	public static double SRCSim(List<Double> u, List<Double> v) {
		if (u == null || v == null)
			return Double.NaN;

		double meanU = Stats.mean(u);
		double meanV = Stats.mean(v);
		double sumNum = 0.0, sumDen1 = 0.0, sumDen2 = 0.0;

		for (int i = 0; i < u.size(); i++) {
			double ui = u.get(i) - meanU;
			double vi = v.get(i) - meanV;

			sumNum += ui * vi;
			sumDen1 += Math.pow(ui, 2);
			sumDen2 += Math.pow(vi, 2);
		}
		return sumNum / (Math.sqrt(sumDen1) * Math.sqrt(sumDen2));
	}

	/**
	 * Calculate Spearman's Rank Correlation (SRC)
	 * 
	 * @param a
	 *            user u's ratings
	 * @param b
	 *            user v's ratings
	 * @param ua
	 *            user u's all ratings
	 * @param va
	 *            user v's all ratings
	 * 
	 * @return Spearman's Rank Correlation (SRC)
	 */
	public static double SRCSim(List<Double> a, List<Double> b, List<Double> ua, List<Double> va) {
		if (a == null || b == null)
			return Double.NaN;

		List<Double> usRatings = new ArrayList<>(ua);
		List<Double> vsRatings = new ArrayList<>(va);

		Collections.sort(usRatings);
		Collections.sort(vsRatings);

		List<Double> u = new ArrayList<>();
		List<Double> v = new ArrayList<>();
		for (Double ar : a) {
			double sum = 0;
			int count = 0;
			for (int i = 0; i < usRatings.size(); i++) {
				double usRating = usRatings.get(i);
				if (usRating == ar) {
					sum += (i + 1);
					count++;
				}
			}
			u.add(sum / count);
		}
		for (Double br : b) {
			double sum = 0;
			int count = 0;
			for (int i = 0; i < vsRatings.size(); i++) {
				double vsRating = vsRatings.get(i);
				if (vsRating == br) {
					sum += (i + 1);
					count++;
				}
			}
			v.add(sum / count);
		}

		double meanU = Stats.mean(u);
		double meanV = Stats.mean(v);
		double sumNum = 0.0, sumDen1 = 0.0, sumDen2 = 0.0;

		for (int i = 0; i < u.size(); i++) {
			double ui = u.get(i) - meanU;
			double vi = v.get(i) - meanV;

			sumNum += ui * vi;
			sumDen1 += Math.pow(ui, 2);
			sumDen2 += Math.pow(vi, 2);
		}
		return sumNum / (Math.sqrt(sumDen1) * Math.sqrt(sumDen2));
	}

	public static double distanceSim(List<Double> a, List<Double> b) {
		if (a == null || b == null || a.size() < 1 || b.size() < 1 || a.size() != b.size())
			return Double.NaN;

		double maxRating = Dataset.maxScale, minRating = Dataset.minScale;
		double sumNum = 0.0, sumDen = 0.0;
		for (int i = 0; i < a.size(); i++) {
			sumNum += Math.abs(a.get(i) - b.get(i));
			sumDen += Math.abs(maxRating - minRating);
		}

		return 1 - sumNum / sumDen;
	}

	public static double distanceSim(List<Double> a, List<Double> b, List<Double> ac, List<Double> bc) {
		if (a == null || b == null || a.size() < 1 || b.size() < 1 || a.size() != b.size())
			return Double.NaN;

		double sumNum = 0.0, sumDen = 0.0;

		for (int i = 0; i < a.size(); i++) {
			double ai = a.get(i), bi = b.get(i);
			double ca = ac.get(i), cb = bc.get(i);
			double dr = Math.abs(ai - bi);
			double dc = Math.abs(ca - cb);
			double dm = Dataset.maxScale - Dataset.minScale;

			if (Debug.ON) {
				double d = (ai - Dataset.median) * (bi - Dataset.median);
				double w = 1.0;
				if (d >= 0)
					w = 1.0 / (d + 1); // agreement 
				else {
					d = -d;
					w = d / (d + 1);
				}

				sumNum += (w + dr / dm + dc / (dc + 1));
				sumDen += 3;
			} else {
				sumNum += dr / dm + dc / (dc + 1);
				sumDen += 2;
			}
		}

		return 1 - sumNum / sumDen;
	}

	public static double disValueSim(double[] a, double[] b) {
		if (a == null || b == null || a.length < 1 || b.length < 1 || a.length != b.length)
			return Double.NaN;

		double maxRating = Dataset.maxScale;
		double sum = 0.0;
		int count = 0;
		for (int i = 0; i < a.length; i++) {
			sum += 1 - Math.abs(a[i] - b[i]) / maxRating;
			count++;
		}

		return sum / count;
	}

	public static double disValueSim(List<Double> a, List<Double> b) {
		if (a == null || b == null || a.size() < 1 || b.size() < 1 || a.size() != b.size())
			return Double.NaN;

		double[] as = Lists.toArray(a);
		double[] bs = Lists.toArray(b);

		return disValueSim(as, bs);
	}

	/**
	 * Calculate the cosine similarity between two vectors
	 * 
	 * @param a
	 *            user a's ratings
	 * @param b
	 *            user b's ratings
	 * @return cosine similarity
	 */
	public static double cosineSim(double[] a, double[] b) {
		if (a == null || b == null || a.length < 1 || b.length < 1 || a.length != b.length)
			return Double.NaN;

		double sum = 0.0, sum_a = 0, sum_b = 0;
		for (int i = 0; i < a.length; i++) {
			sum += a[i] * b[i];
			sum_a += a[i] * a[i];
			sum_b += b[i] * b[i];
		}

		double val = Math.sqrt(sum_a) * Math.sqrt(sum_b);

		return sum / val;
	}

	/**
	 * Calculate the SM (singularities measure) similarity proposed by Bobadilla
	 * et al. [2012]: <i>A collaborative filtering similarity measure based on
	 * singularities</i>
	 * 
	 * @param a
	 *            user a's ratings
	 * @param b
	 *            user b's ratings
	 * @return SM similarity
	 */
	public static double SMSim(List<Double> a, List<Double> b, List<Double> sp, List<Double> sn) {
		double r = Dataset.median;

		double sumA = 0, sumB = 0, sumC = 0;
		int countA = 0, countB = 0, countC = 0;
		for (int i = 0; i < a.size(); i++) {
			double ai = a.get(i);
			double bi = b.get(i);
			double pi = sp.get(i);
			double ni = sn.get(i);

			double ri = ai / Dataset.maxScale;
			double rj = bi / Dataset.maxScale;

			if (ai > r && bi > r) {
				// A: positive agreement
				countA++;
				sumA += (1 - Math.pow(ri - rj, 2)) * Math.pow(pi, 2);
			} else if (ai <= r && bi <= r) {
				// B: negative agreement
				countB++;
				sumB += (1 - Math.pow(ri - rj, 2)) * Math.pow(ni, 2);
			} else {
				// C: disagreement
				countC++;
				sumC += (1 - Math.pow(ri - rj, 2)) * pi * ni;
			}
		}

		double score = 0;
		if (countA > 0)
			score += sumA / countA;
		if (countB > 0)
			score += sumB / countB;
		if (countC > 0)
			score += sumC / countC;

		if (countA + countB + countC == 0)
			return Double.NaN;
		else
			return score / 3.0;
	}

	/**
	 * Implement Weng's approach
	 * 
	 * @param a
	 * @param b
	 * @return
	 */
	public static double kappa2Similarity(List<Double> a, List<Double> b) {
		if (a == null || b == null || a.size() < 1 || b.size() < 1 || a.size() != b.size())
			return Double.NaN;

		double weight[][] = { { 1.00, 0.75, 0.50, 0.25, 0.00 }, { 0.75, 1.00, 0.75, 0.50, 0.25 },
				{ 0.50, 0.75, 1.00, 0.75, 0.50 }, { 0.25, 0.50, 0.75, 1.00, 0.75 }, { 0.00, 0.25, 0.50, 0.75, 1.00 } };
		double data[][] = { { 0.0, 0.0, 0.0, 0.0, 0.0 }, { 0.0, 0.0, 0.0, 0.0, 0.0 }, { 0.0, 0.0, 0.0, 0.0, 0.0 },
				{ 0.0, 0.0, 0.0, 0.0, 0.0 }, { 0.0, 0.0, 0.0, 0.0, 0.0 } };

		RealMatrix weightMatrix = new Array2DRowRealMatrix(weight);
		RealMatrix observationMatrix = new Array2DRowRealMatrix(data);

		int R[] = new int[5];
		int C[] = new int[5];

		int size = a.size();
		for (int i = 0; i < size; i++) {
			int aRating = a.get(i).intValue();
			int bRating = b.get(i).intValue();

			int idx = aRating - 1;
			int jdx = bRating - 1;
			double count = observationMatrix.getRow(idx)[jdx];

			count += 1;
			observationMatrix.setEntry(idx, jdx, count);
		}

		for (int i = 0; i < 5; i++) {
			R[i] = 0;
			for (int j = 0; j < 5; j++)
				R[i] += (int) observationMatrix.getRow(i)[j];
		}

		for (int j = 0; j < 5; j++) {
			C[j] = 0;
			for (int i = 0; i < 5; i++)
				C[j] += (int) observationMatrix.getColumn(j)[i];
		}

		double similarity = 0.0;
		double observation = 0.0, expectation = 0.0;
		for (int i = 0; i < 5; i++) {
			for (int j = 0; j < 5; j++) {
				observation += weightMatrix.getRow(i)[j] * observationMatrix.getRow(i)[j] / C[j];
			}
		}
		observation *= size;

		for (int i = 0; i < 5; i++) {
			expectation += R[i] * R[i];
		}

		similarity = (observation - expectation) / (size * size - expectation);

		return similarity;
	}

	/**
	 * Compare the trends of different similarity methods;
	 * 
	 * @throws Exception
	 */
	@Test
	// @Ignore
	public void ijcai_trends() throws Exception {
		Dataset.minScale = 1;
		Dataset.maxScale = 5;
		Dataset.scaleSize = 5;

		int size = 1_000_000;
		int items = 20;

		// double[] pccs = new double[size];
		// double[] coss = new double[size];
		List<Double> bys = new ArrayList<>();

		String dirPath = Systems.getDesktop();
		// List<Double> ps = new ArrayList<>();
		// List<Double> cs = new ArrayList<>();
		List<Double> bs = new ArrayList<>();

		// List<Double> pstd = new ArrayList<>();
		// List<Double> cstd = new ArrayList<>();
		List<Double> bstd = new ArrayList<>();

		/*
		 * FileUtils.deleteFile(dirPath + "pcc.txt");
		 * FileUtils.deleteFile(dirPath + "cos.txt");
		 * FileUtils.deleteFile(dirPath + "by.txt");
		 * FileUtils.deleteFile(dirPath + "pcc-std.txt");
		 * FileUtils.deleteFile(dirPath + "cos-std.txt");
		 * FileUtils.deleteFile(dirPath + "by-std.txt");
		 */

		for (int i = 1; i < items + 1; i++) {
			System.out.println("Progress = " + i + "/" + items);

			bys.clear();

			for (int j = 0; j < size; j++) {
				double[] a = Randoms.doubles(1, 6, i);
				double[] b = Randoms.doubles(1, 6, i);

				List<Double> u = Lists.toList(a);
				List<Double> v = Lists.toList(b);

				// double pcc = SimUtils.pearsonSim(a, b);
				// double cos = SimUtils.cosineSim(a, b);
				double by = SimUtils.ijcai_bs_examples(u, v, true);
				// double by = SimUtils.MSDSim(MathUtils.array2Col(a),
				// MathUtils.array2Col(b));
				// double by = SimUtils.CPCSim(u, v, 3);

				// pccs[j] = pcc;
				// coss[j] = cos;
				bys.add(by);

			}

			// double mp = MathUtils.mean(pccs);
			// mp = (1 + mp) / 2;
			// double mc = MathUtils.mean(coss);
			double mb = Stats.mean(bys);
			//mb = (1 + mb) / 2;

			// ps.add(mp);
			// cs.add(mc);
			bs.add(mb);

			for (int j = 0; j < size; j++) {
				// if (Double.isNaN(pccs[j])) pccs[j] = mp;
				if (Double.isNaN(bys.get(j)))
					bys.set(j, mb);
			}

			// double dp = MathUtils.sd(pccs);
			// double dc = MathUtils.sd(coss);
			double db = Stats.sd(bys);

			// pstd.add(dp);
			// cstd.add(dc);
			bstd.add(db);
		}

		// FileUtils.writeCollection(dirPath + "pcc.txt", ps, true);
		// FileUtils.writeCollection(dirPath + "cos.txt", cs, true);
		FileIO.writeList(dirPath + "cpc.txt", bs, null, true);

		// FileUtils.writeCollection(dirPath + "pcc-std.txt", pstd, true);
		// FileUtils.writeCollection(dirPath + "cos-std.txt", cstd, true);
		FileIO.writeList(dirPath + "cpc-std.txt", bstd, null, true);
	}

	@Test
	public void ijcai_examples() throws Exception {
		Dataset.minScale = 1;
		Dataset.maxScale = 5;
		Dataset.scaleSize = 5;

		double[] a, b;

		if (Debug.OFF) {
			int size = 1_00;
			int items = 10;

			for (int i = 1; i < items + 1; i++) {
				for (int j = 0; j < size; j++) {
					a = Randoms.doubles(1, 6, i);
					b = Randoms.doubles(1, 6, i);

					singleComparison(a, b);
				}

			}
		} else {
			a = new double[] { 1, 1, 1 };
			b = new double[] { 1, 1, 1 };
			singleComparison(a, b);

			a = new double[] { 1, 1, 1 };
			b = new double[] { 2, 2, 2 };
			singleComparison(a, b);

			a = new double[] { 1, 1, 1 };
			b = new double[] { 5, 5, 5 };
			singleComparison(a, b);

			a = new double[] { 1, 5, 1 };
			b = new double[] { 5, 1, 5 };
			singleComparison(a, b);

			a = new double[] { 2, 4, 4 };
			b = new double[] { 4, 2, 2 };
			singleComparison(a, b);

			a = new double[] { 2, 4, 4, 1 };
			b = new double[] { 4, 2, 2, 5 };
			singleComparison(a, b);

			a = new double[] { 1 };
			b = new double[] { 1 };
			singleComparison(a, b);

			a = new double[] { 1 };
			b = new double[] { 2 };
			singleComparison(a, b);

			a = new double[] { 1 };
			b = new double[] { 5 };
			singleComparison(a, b);

			a = new double[] { 1, 5 };
			b = new double[] { 5, 1 };
			singleComparison(a, b);

			a = new double[] { 1, 3 };
			b = new double[] { 4, 2 };
			singleComparison(a, b);

			a = new double[] { 5, 1 };
			b = new double[] { 5, 4 };
			singleComparison(a, b);

			a = new double[] { 4, 3 };
			b = new double[] { 3, 1 };
			singleComparison(a, b);
		}
	}

	private static void singleComparison(double[] a1, double[] b1) {
		List<Double> u = Lists.toList(a1);
		List<Double> v = Lists.toList(b1);
		float pcc = (float) Sims.pcc(u, v);
		float cos = (float) Sims.cos(u, v);
		float bs = (float) SimUtils.ijcai_bs_examples(u, v, true);
		float bs2 = (float) SimUtils.ijcai_bs_examples(u, v, false);
		log.info("u = {}, v = {}", u, v);
		log.info("PCC = {}, COS = {}, BS = {}, BS-1 = {}", new Object[] { pcc, cos, bs, bs2 });
	}

	public static double kappaSim(List<Double> a, List<Double> b) {
		if (a == null || b == null || a.size() < 1 || b.size() < 1 || a.size() != b.size())
			return Double.NaN;

		double weight[][] = { { 1.00, 0.75, 0.50, 0.25, 0.00 }, { 0.75, 1.00, 0.75, 0.50, 0.25 },
				{ 0.50, 0.75, 1.00, 0.75, 0.50 }, { 0.25, 0.50, 0.75, 1.00, 0.75 }, { 0.00, 0.25, 0.50, 0.75, 1.00 } };
		double data[][] = { { 0.0, 0.0, 0.0, 0.0, 0.0 }, { 0.0, 0.0, 0.0, 0.0, 0.0 }, { 0.0, 0.0, 0.0, 0.0, 0.0 },
				{ 0.0, 0.0, 0.0, 0.0, 0.0 }, { 0.0, 0.0, 0.0, 0.0, 0.0 } };

		RealMatrix weightMatrix = new Array2DRowRealMatrix(weight);
		RealMatrix observationMatrix = new Array2DRowRealMatrix(data);
		RealMatrix expectationMatrix = new Array2DRowRealMatrix(data);

		int R[] = new int[5];
		int C[] = new int[5];
		int size = a.size();
		for (int i = 0; i < size; i++) {
			int aRating = a.get(i).intValue();
			int bRating = b.get(i).intValue();

			int idx = aRating - 1;
			int jdx = bRating - 1;
			double count = observationMatrix.getRow(idx)[jdx];

			count += 1;
			observationMatrix.setEntry(idx, jdx, count);
		}

		for (int i = 0; i < 5; i++) {
			R[i] = 0;
			for (int j = 0; j < 5; j++)
				R[i] += (int) observationMatrix.getRow(i)[j];
		}

		for (int j = 0; j < 5; j++) {
			C[j] = 0;
			for (int i = 0; i < 5; i++)
				C[j] += (int) observationMatrix.getColumn(j)[i];
		}

		for (int i = 0; i < 5; i++) {
			for (int j = 0; j < 5; j++) {
				double value = R[i] * C[j] / (size + 0.0);
				expectationMatrix.setEntry(i, j, value);
			}
		}

		double similarity = 0.0;
		double observation = 0.0, expectation = 0.0;
		for (int i = 0; i < 5; i++) {
			for (int j = 0; j < 5; j++) {
				observation += weightMatrix.getRow(i)[j] * observationMatrix.getRow(i)[j];
				expectation += weightMatrix.getRow(i)[j] * expectationMatrix.getRow(i)[j];
			}
		}

		similarity = (observation - expectation) / (size - expectation);

		return similarity;
	}

	/*
	 * Below are a list of significance computation methods
	 */
	public static double overlapSig(int aSize, int uSize, int auCommon) {
		return auCommon / (Math.min(aSize, uSize) + 0.0);
	}

	public static double aSig(int size, int gamma) {
		return Math.min(size, gamma) / (gamma + 0.0);
	}

	public static double bSig(int size, int gamma) {
		return size / (size + gamma + 0.0);
	}

	public static double cSig(int size, int gamma, double similarity) {
		double SW = (size < gamma) ? size / (0.0 + gamma) : 1.0;

		return 2 * SW * similarity / (SW + similarity);
	}

	public static double dSig(double r, double s) throws Exception {
		return TrustUtils.confidence(r, s);
	}

	public static double eSig(int aSize, int uSize, int auCommon) {
		return overlapSig(aSize, uSize, auCommon);
	}

	public static double fSig(int aSize, int uSize, int auCommon, double similarity) {
		double os = overlapSig(aSize, uSize, auCommon);
		return 2 * os * similarity / (os + similarity);
	}

}
