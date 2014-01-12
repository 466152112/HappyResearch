package happy.research.tp;

import happy.coding.io.Logs;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Implement Kim and Phalak's approach in "information sciences 2012":
 * 
 * A trust prediction framework in rating-based experience sharing social
 * networks without a web of trust
 * 
 * @author guoguibing
 * 
 */
public class EPT extends TrustModel {

	public int Nmin = 4;

	public EPT() {
		model = "EPT";
	}

	@Override
	protected void global() throws Exception {
		// compute global expertise

		// initialize review qualities
		for (String rw : rws)
			rqs.put(rw, 0.0f);

		// global ability using Digg's algorithm
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

				float den = 0.0f, num = 0.0f;
				for (Entry<String, Float> en : rts.entrySet()) {
					String u = en.getKey();
					float rate = en.getValue();

					if (!rbs.containsKey(u))
						rbs.put(u, (float) Math.random()); // lazy initialization
					float rb = rbs.get(u);

					num += rb * rate;
					den += rb;
				}

				float rq = num / den;
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

				float sum = 0.0f;
				int cnt = 0;
				for (Entry<String, Float> en : rts.entrySet()) {
					String rv = en.getKey();
					float rate = en.getValue();
					float diff = rqs.get(rv) - rate;

					sum += 1 - Math.abs(diff);
					cnt++;
				}

				float rb = weight(cnt) * (sum / cnt);
				float rb_last = rbs.containsKey(u) ? rbs.get(u) : 0f;
				float e = rb_last - rb;

				rbs.put(u, rb);
				err += e * e;
			}

			Logs.debug("Iteration: {}, errors: {}", ++iter, err);

			// check if converged
			if (err < 1e-5)
				break;

		}

		// global ability as a writer/i.e., expertise: wbs
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
	}

	@Override
	protected float local(String u, String v) throws Exception {
		Collection<String> rvs = reviews.get(v);
		Map<String, Float> rts = ratings.row(u);

		float sum = 0f;
		int cnt = 0;
		for (String rv : rvs) {
			if (rts.containsKey(rv)) {
				float rate = rts.get(rv);
				sum += rate;
				cnt++;
			}
		}

		return cnt > 0 ? sum / cnt : 0f;
	}

	@Override
	protected float predict(String u, String v) throws Exception {
		float lt = local(u, v);
		float gt = wbs.containsKey(v) ? wbs.get(v) : 0f;

		// int n = userWrites.contains(v, u) ? userWrites.get(v, u) : 0;

		Collection<String> rvs = reviews.get(v);
		Map<String, Float> rts = ratings.row(u);
		int n = 0;
		for (String rv : rvs) {
			if (rts.containsKey(rv))
				n++;
		}

		float a = alpha(n, Nmin);

		return a * lt + (1 - a) * gt;
	}

}
