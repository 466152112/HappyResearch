package happy.research.tp;

import happy.coding.io.Logs;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Implement Nguyen et al.'s approach in ICDM 2009:
 * 
 * To Trust or Not to Trust? Predicting Online Trusts using Trust Antecedent
 * Framework
 * 
 * @author guoguibing
 * 
 */
public class TAF extends TrustModel {

	public TAF() {
		model = "TAF";
	}

	@Override
	protected void global() throws Exception {

		Logs.debug("Starting global evaluation ...");
		// benevolence
		for (String rv : rws)
			rqs.put(rv, 0.0f);

		// beta's value could significantly influence the convergence of digg's
		// algorithm
		// and the overall performance
		float beta = 0.5f;
		int iter = 0;
		while (true) {

			float err = 0f;
			int pg_rv = 0, pg_user = 0;
			// compute review quality
			for (String rv : rws) {
				if (pg_rv % 3000 == 0)
					Logs.debug(
							"Current progress: iteration {} with review: {}/{}",
							new Object[] { iter + 1, pg_rv, rws.size() });

				pg_rv++;

				Map<String, Float> urs = ratings.column(rv);
				String v = reviewUserMap.get(rv); // who write this review

				float sum = 0f;
				int cnt = 0;
				for (Entry<String, Float> en : urs.entrySet()) {
					String u = en.getKey();
					float rate = en.getValue();

					if (!lns.contains(u, v))
						lns.put(u, v, (float) Math.random()); // late
																// initialization

					float ln = lns.get(u, v);
					sum += rate * (1 - beta * ln);
					cnt++;
				}

				if (cnt > 0) {
					float rq = logic(cnt, 0.1f, 5) * (sum / cnt);
					float last_rq = rqs.get(rv);
					rqs.put(rv, rq);

					float e = last_rq - rq;
					err += e * e;
				}
			}

			// update local leniency
			for (String u : users) {
				if (pg_user % 3000 == 0)
					Logs.debug(
							"Current progress: iteration {} with user: {}/{}",
							new Object[] { iter + 1, pg_user, users.size() });
				pg_user++;

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
						float last_ln = lns.get(u, v);
						lns.put(u, v, ln);

						float e = last_ln - ln;
						err += e * e;
					}
				}
			}

			Logs.debug("Iteration: {}, Error: {}", ++iter, err);

			if (err < 1e-5)
				break;
		}

		// find out min, max of lns
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

		Logs.debug("Done!");
	}

	@Override
	protected float local(String u, String v) throws Exception {
		float ab = 0f, be = 0f, in = 0f, tp = 0f;
		Collection<String> rvs = reviews.get(v);
		Map<String, Float> rts = ratings.row(u);

		// ability
		float sum = 0f;
		int cnt = 0;
		for (String rv : rvs) {
			if (rts.containsKey(rv)) {
				sum += rts.get(rv);
				cnt++;
			}
		}

		if (cnt > 0)
			ab = (sum / cnt) * logic(cnt, 0.5f, 5);

		// benevolence
		if (lns.contains(u, v))
			be = (lns.get(u, v) - min_lns) / (max_lns - min_lns);

		// integrity: based on number of users who trust this user
		in = 1.0f; // no trust is assumed

		// trust propensity
		tp = gls.containsKey(u) ? gls.get(u) : 0f;

		return ab * be * in * tp;
	}
}
