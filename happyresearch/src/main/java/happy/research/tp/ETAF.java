package happy.research.tp;

import happy.coding.io.Logs;
import happy.coding.math.Sims;
import happy.coding.math.Stats;
import happy.coding.system.Debug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Implementation of our model: ETAF (Extended Trust Antecedents Framework)
 * 
 * @author guoguibing
 * 
 */
public class ETAF extends TrustModel {

	// whether use fixed alpha to combine local and global trust
	float alpha = 0.5f;

	// the extent to use ability as a rater
	float gamma = 0.5f;

	float eta = 0.5f;

	// is integrity is enabled
	boolean isIn = true;

	int disjoint = 0;

	public ETAF() {
		model = "ETAF";
	}

	protected float predict(String u, String v) throws Exception {

		float lt = local(u, v);
		float gt = gts.containsKey(v) ? gts.get(v) : 0f;
		float tp = gls.containsKey(u) ? gls.get(u) : 0f; // trust propensity

		if (lt == 0 && gt > 0) {
			disjoint++;
			//Logs.debug("Number of gt>0, lt==0: {}", disjoint);
		}

		return (alpha * lt + (1 - alpha) * gt) * tp;
	}

	/**
	 * Evaluate two users' local trustworthiness
	 */
	protected float local(String u, String v) throws Exception {
		float lt = 0.0f;

		Collection<String> rvs = reviews.get(v);
		if (rvs.size() == 0)
			return lt; // user v has not written any reviews

		float sum = 0.0f;
		int cnt = 0;
		Map<String, Float> rts = ratings.row(u);

		for (String rv : rvs) {
			if (rts.containsKey(rv)) {
				sum += rts.get(rv);
				cnt++;
			}
		}

		// local ability
		float ab = cnt > 0 ? logic(cnt, 0.1f, 5) * (sum / cnt) : 0f;

		// local benevolence
		float be = lns.contains(u, v) ? (lns.get(u, v) - min_lns) / (max_lns - min_lns) : 0f;

		lt = ab * be;

		if (lt > 0) {
			// integrity
			if (Debug.OFF) {
				Multiset<Float> uVals = HashMultiset.create(rts.values());
				Multiset<Float> vVals = HashMultiset.create(ratings.row(v).values());

				double dkl = 0;
				int cnt_k = 0;
				for (Float val : uVals.elementSet()) {
					if (vVals.contains(val)) {
						double pvk = (vVals.count(val) + 0.0) / vVals.size();
						double puk = (uVals.count(val) + 0.0) / uVals.size();

						cnt_k++;

						if (pvk > 0 && puk > 0)
							dkl += Math.log(pvk / puk) * pvk;
					}
				}

				//float in = ins.containsKey(v) ? ins.get(v) : 0f;
				//in = 1.0f;
				float in = cnt_k > 0 ? (float) Math.exp(-dkl) : 0f;

				lt *= in;
			} else if (Debug.ON) {
				lt *= 0.5;
			}
		}

		// local trustworthiness
		return lt;
	}

	/**
	 * Evaluate users' global trustworthiness
	 */
	protected void global() throws Exception {
		Logs.debug("Execute global evaluation of trustworthiness ...");

		// initialize review qualities
		for (String rw : rws)
			rqs.put(rw, 0.0f);

		// global ability using Digg's algorithm
		float beta = 0.5f;
		int iter = 0;
		while (true) {
			float err = 0;

			// compute review's quality
			for (String rv : rws) {

				Map<String, Float> rts = ratings.column(rv);
				if (rts.size() == 0) {
					// if no one rated this review
					continue;
				}
				// writer
				String v = reviewUserMap.get(rv);

				float den = 0.0f, num = 0.0f;
				int cnt = 0;
				for (Entry<String, Float> en : rts.entrySet()) {
					String u = en.getKey(); // rater
					float rate = en.getValue();

					if (!rbs.containsKey(u))
						rbs.put(u, (float) Math.random()); // lazy initialization
					float rb = rbs.get(u);

					if (!lns.contains(u, v))
						lns.put(u, v, (float) Math.random());
					float ln = lns.get(u, v);

					num += rb * rate * (1 - beta * ln);
					den += rb;
					cnt++;
				}

				float rq = weight(cnt) * (num / den);
				float rq_last = rqs.get(rv);
				float e = rq_last - rq;

				rqs.put(rv, rq);

				err += e * e;
			}

			// update users' rbs
			for (String u : users) {
				Map<String, Float> rts = ratings.row(u);
				if (rts.size() == 0)
					continue; // if user has rated nothing

				int n_rates = rts.size();
				float w = weight(n_rates);

				float sum = 0;
				for (Entry<String, Float> en : rts.entrySet()) {
					String rv = en.getKey();
					float rate = en.getValue();

					float diff = rate - rqs.get(rv);

					sum += Math.abs(diff);
				}

				float rb = w * (1 - sum / n_rates);
				float rb_last = rbs.containsKey(u) ? rbs.get(u) : 0f;
				float e = rb_last - rb;

				rbs.put(u, rb);
				err += e * e;
			}

			// update users' lns
			for (String u : users) {
				for (String v : users) {
					if (u.equals(v))
						continue;
					Collection<String> rvs = reviews.get(v);
					Map<String, Float> rts = ratings.row(u);

					float sum = 0f;
					int cnt = 0;
					for (String rv : rvs) {
						if (rts.containsKey(rv)) {
							float rt = rts.get(rv);
							float rq = rqs.get(rv);

							sum += (rt - rq) / rt;
							cnt++;
						}
					}
					if (cnt > 0) {
						float ln = sum / cnt;
						float ln_last = lns.get(u, v);

						lns.put(u, v, ln);

						float e = ln_last - ln;
						err += e * e;
					}
				}
			}

			Logs.debug("Iteration: {}, errors: {}", ++iter, err);

			// check if converged
			if (err < 1e-5)
				break;
		}

		// global ability as a writer: wbs
		for (String u : users) {
			Collection<String> rvs = reviews.get(u);
			if (rvs.size() == 0)
				continue;

			float sum = 0f;
			int cnt = rvs.size();
			for (String rv : rvs)
				sum += rqs.get(rv);

			float wb = weight(cnt) * (sum / cnt);
			wbs.put(u, wb);
		}

		// 4. global benevolence
		float[] mms = minMax(lns.values());
		min_lns = mms[0];
		max_lns = mms[1];

		// compute gls
		for (String u : users) {
			float sum = 0;
			int cnt = 0;

			for (String v : users) {
				if (u.equals(v))
					continue;

				if (lns.contains(u, v)) {
					float ln = lns.get(u, v);

					sum += (ln - min_lns) / (max_lns - min_lns);
					cnt++;
				}
			}

			if (cnt > 0)
				gls.put(u, sum / cnt);
		}

		// compute gbs
		mms = minMax(gls.values());
		float min = mms[0], max = mms[1];
		for (Entry<String, Float> en : gls.entrySet()) {
			String u = en.getKey();
			float gl = en.getValue();

			float gb = (gl - min) / (max - min);
			gbs.put(u, gb);
		}

		if (Debug.ON) {
			for (String u : users) {
				// writer integrity
				Collection<String> rvs = reviews.get(u);
				float inw = 0f;
				if (rvs.size() > 0) {
					List<Float> qs = new ArrayList<>();
					for (String rv : rvs)
						qs.add(rqs.get(rv));

					double mean = Stats.mean(qs);
					double std = Stats.sd(qs, mean);
					inw = (float) (weight(rvs.size()) * mean * (1 - std));
				}

				// rater integrity
				Map<String, Float> rts = ratings.row(u);
				float inr = 0f;

				if (rts.size() > 0) {
					List<Float> us = new ArrayList<>();
					List<Float> vs = new ArrayList<>();

					for (String rv : rts.keySet()) {
						if (rqs.containsKey(rv)) {
							us.add(rts.get(rv));
							vs.add(rqs.get(rv));
						}
					}

					if (us.size() > 1) {
						double sim = Sims.pcc(us, vs);
						if (!Double.isNaN(sim)) {
							float w = weight(us.size());
							inr = (float) ((1 + sim) / 2.0);
							inr *= w;
						}
					}
				}

				float in = eta * inw + (1 - eta) * inr;
				if (in > 0)
					ins.put(u, in);

			}
		} else if (Debug.OFF) {
			// similarity between user's ratings w.r.t the real quality
			for (String u : users) {
				Map<String, Float> rts = ratings.row(u);
				List<Float> us = new ArrayList<>();
				List<Float> vs = new ArrayList<>();

				for (String rv : rts.keySet()) {
					if (rqs.containsKey(rv)) {
						us.add(rts.get(rv));
						vs.add(rqs.get(rv));
					}
				}

				if (us.size() > 1) {
					double sim = Sims.pcc(us, vs);
					if (!Double.isNaN(sim)) {
						float w = weight(us.size());
						float in = (float) ((1 + sim) / 2.0);
						ins.put(u, w * in);
					}
				}
			}
		} else if (Debug.OFF) {
			// {review: average-rating}
			Map<String, Float> rrs = new HashMap<>();
			for (String rv : rws) {
				Map<String, Float> rts = ratings.column(rv);
				if (rts.size() > 0) {
					float mean = (float) Stats.mean(rts.values());
					rrs.put(rv, mean);
				}
			}
			// similarity between user's ratings w.r.t the majority
			for (String u : users) {
				Map<String, Float> rts = ratings.row(u);
				List<Float> us = new ArrayList<>();
				List<Float> vs = new ArrayList<>();

				for (String rv : rts.keySet()) {
					if (rrs.containsKey(rv)) {
						us.add(rts.get(rv));
						vs.add(rrs.get(rv));
					}
				}

				if (us.size() > 1) {
					double sim = Sims.pcc(us, vs);
					if (!Double.isNaN(sim)) {
						float w = weight(us.size());
						float in = (float) ((1 + sim) / 2.0);
						ins.put(u, w * in);
					}
				}
			}

		} else if (Debug.OFF) {
			for (String u : users) {
				Map<String, Integer> uInters = userRates.row(u);// userInters.row(u);

				int tu = 0, nu = 0;
				for (Entry<String, Integer> en : uInters.entrySet()) {
					int n_inter = en.getValue();
					if (n_inter > 0) {
						tu += n_inter;
						nu++;
					}
				}
				if (nu == 0)
					continue;

				float x = (tu + 0.0f) / nu;
				float in = logic(x, 0.5f, 5); // try 0.1, 0.5

				ins.put(u, in);
			}
		}

		// 6. global trustworthiness
		for (String u : users) {
			float ab = (wbs.containsKey(u) ? wbs.get(u) : 0f) * gamma + (rbs.containsKey(u) ? rbs.get(u) : 0f)
					* (1 - gamma);
			float be = gbs.containsKey(u) ? gbs.get(u) : 0f;
			float in = ins.containsKey(u) ? ins.get(u) : 0f;
			if (!isIn)
				in = 1.0f;

			float gt = ab * be * in;

			if (gt > 0)
				gts.put(u, gt);
		}

		Logs.debug("Done!");
	}

}
