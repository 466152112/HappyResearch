package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.FileIO.MapWriter;
import happy.coding.io.Logs;
import happy.coding.math.Maths;
import happy.coding.math.Sims;
import happy.coding.math.Stats;
import happy.coding.system.Systems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This approach is an implementation of our working paper: 
 * 
 *      From ratings to trust: an empirical study of implicit trust in recommender systems, ACM SAC, 2013. 
 *      
 * @author guoguibing
 *
 */
public class ITrust_mt extends DefaultCF_mt
{
	private String	trustMetric;
	private String	trustDir;

	public ITrust_mt() throws Exception
	{
		methodId = "iTrust";
		trustMetric = params.readParam("itrust.probe.method");
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		// probe trust from ratings
		probeITrust();

		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new ITrust_t(i));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}

	/**
	 * Probe implicit trust from user ratings
	 * @throws Exception 
	 */
	private void probeITrust() throws Exception
	{
		trustDir = Dataset.DIRECTORY + trustMetric + "-"
				+ params.TRAIN_SET.substring(0, params.TRAIN_SET.lastIndexOf(".base")) + Systems.FILE_SEPARATOR;

		if (FileIO.exist(trustDir)) return;
		else FileIO.makeDirectory(trustDir);

		switch (trustMetric)
		{
			case "TM1":
				TM1();
				break;
			case "TM2":
				TM2();
				break;
			case "TM3a":
				TM3a();
				break;
			case "TM3b":
				TM3b();
				break;
			case "TM4":
				TM4();
				break;
			case "TM5":
				TM5();
				break;
		}

	}

	/**
	 * Implementation of the <em>TM1</em> method.
	 * 
	 * @throws Exception
	 */
	private void TM1() throws Exception
	{
		List<Double> recalls = new ArrayList<>();
		List<Double> NDCGs = new ArrayList<>();

		for (final String u : userRatingsMap.keySet())
		{
			Map<String, Rating> usRatings = userRatingsMap.get(u);
			if (usRatings == null)
			{
				if (userTNsMap.containsKey(u)) recalls.add(0.0);
				continue;
			}

			/* variable for storing u's trusted neighbors */
			Map<String, Double> tls = new HashMap<>();

			for (String v : userRatingsMap.keySet())
			{
				if (u.equals(v)) continue;
				Map<String, Rating> vsRatings = userRatingsMap.get(v);
				if (vsRatings == null) continue;

				double sum = 0;
				int count = 0;
				for (String item : usRatings.keySet())
				{
					if (vsRatings.containsKey(item))
					{
						Rating ur = usRatings.get(item);
						Rating vr = vsRatings.get(item);

						double dis = Math.abs(ur.getRating() - vr.getRating());
						double val = 1 - dis / Dataset.maxScale;

						sum += val;
						count++;
					}
				}

				if (count > 0)
				{
					double trust = sum / count;
					if (trust > 0) tls.put(v, trust);
				}
			}

			saveTrust(u, tls);
			trustList(recalls, NDCGs, u, tls);
		}
		recordResults(recalls, NDCGs);

	} /* end of TM1() */

	/**
	 * record the results by NDCGS and Recalls
	 * 
	 * @param recalls
	 * @param NDCGs
	 */
	private void recordResults(List<Double> recalls, List<Double> NDCGs)
	{
		double NDCG = Stats.mean(NDCGs);
		double recall = Stats.mean(recalls);
		Logs.debug("NDCG = {}, recall = {}", NDCG, recall);

		methodSettings.add(NDCG + "");
		methodSettings.add(recall + "");
	}

	/**
	 * Compute the quality of trust list 
	 * 
	 * @param recalls
	 * @param NDCGs
	 * @param u
	 * @param tls
	 */
	private void trustList(List<Double> recalls, List<Double> NDCGs, final String u, Map<String, Double> tls)
	{
		Map<String, Double> tns = userTNsMap.get(u);
		if (tns != null && tns.size() > 0)
		{
			int cnt = 0;
			for (String v : tns.keySet())
			{
				if (tls.containsKey(v)) cnt++;
			}
			recalls.add((cnt + 0.0) / tns.size());

			/* ranking trust list */
			if (tls.size() > 0)
			{
				List<Pair> list = new ArrayList<>();
				for (Entry<String, Double> en : tls.entrySet())
				{
					Pair p = new Pair(en.getKey(), en.getValue());
					list.add(p);
				}
				Collections.sort(list);
				Collections.reverse(list);

				/* compute NDCG */
				double DCG = 0;
				double iDCG = 0;
				for (int i = 0; i < tns.size(); i++)
					iDCG += 1 / Maths.log(i + 2, 2);

				for (int i = 0; i < list.size(); i++)
				{
					String item = list.get(i).left;
					if (!tns.containsKey(item)) continue;

					int rank = i + 1;
					DCG += 1 / Maths.log(rank + 1, 2);
				}

				NDCGs.add(DCG / iDCG);
			}
		}
	}

	/**
	 * save the computed implicit trust to disk
	 * 
	 * @param u active user u
	 * @param tns u's trusted neighbors
	 * @throws Exception
	 */
	private void saveTrust(final String u, Map<String, Double> tns) throws Exception
	{
		if (tns.size() > 0)
		{
			String trustFile = trustDir + u + ".txt";

			FileIO.writeMap(trustFile, tns, new MapWriter<String, Double>() {

				@Override
				public String processEntry(String key, Double val)
				{
					return key + " " + val;
				}
			}, false);

		}
	}

	/**
	 * Implementation of the <em>TM2</em> method.
	 * 
	 * @throws Exception
	 */
	private void TM2() throws Exception
	{
		List<Double> recalls = new ArrayList<>();
		List<Double> NDCGs = new ArrayList<>();

		int thetaI = 2;
		double thetaS = 0.707;

		for (final String u : userRatingsMap.keySet())
		{
			Map<String, Rating> usRatings = userRatingsMap.get(u);
			if (usRatings == null)
			{
				if (userTNsMap.containsKey(u)) recalls.add(0.0);
				continue;
			}

			/* variable for storing u's trusted neighbors */
			Map<String, Double> tls = new HashMap<>();

			for (String v : userRatingsMap.keySet())
			{
				if (u.equals(v)) continue;
				Map<String, Rating> vsRatings = userRatingsMap.get(v);
				if (vsRatings == null) continue;

				List<Double> us = new ArrayList<>();
				List<Double> vs = new ArrayList<>();
				for (String item : usRatings.keySet())
				{
					if (vsRatings.containsKey(item))
					{
						Rating ur = usRatings.get(item);
						Rating vr = vsRatings.get(item);

						us.add(ur.getRating());
						vs.add(vr.getRating());
					}
				}
				int size = us.size();
				if (size < 2) continue;
				if (size <= thetaI) continue;

				double pcc = Sims.pcc(us, vs);
				if (Double.isNaN(pcc)) continue;
				if (pcc > thetaS)
				{
					double trust = pcc;
					if (trust > 0) tls.put(v, trust);
				}

			}

			saveTrust(u, tls);
			trustList(recalls, NDCGs, u, tls);
		}
		recordResults(recalls, NDCGs);

	} /* end of TM2() */

	/**
	 * Implementation of the <em>TM3a</em> method.
	 * 
	 * @throws Exception
	 */
	private void TM3a() throws Exception
	{
		List<Double> recalls = new ArrayList<>();
		List<Double> NDCGs = new ArrayList<>();

		for (final String u : userRatingsMap.keySet())
		{
			Map<String, Rating> usRatings = userRatingsMap.get(u);
			if (usRatings == null)
			{
				if (userTNsMap.containsKey(u)) recalls.add(0.0);
				continue;
			}
			double meanU = RatingUtils.mean(usRatings, null);

			/* variable for storing u's trusted neighbors */
			Map<String, Double> tls = new HashMap<>();

			for (String v : userRatingsMap.keySet())
			{
				if (u.equals(v)) continue;
				Map<String, Rating> vsRatings = userRatingsMap.get(v);
				if (vsRatings == null) continue;

				double meanV = RatingUtils.mean(vsRatings, null);

				double sum = 0;
				int count = 0;
				for (String item : usRatings.keySet())
				{
					if (vsRatings.containsKey(item))
					{
						Rating ur = usRatings.get(item);
						Rating vr = vsRatings.get(item);

						double pred = meanU + vr.getRating() - meanV;
						double dist = pred - ur.getRating();
						double val = 1 - dist / Dataset.maxScale;

						sum += val;
						count++;
					}
				}

				if (count > 0)
				{
					double trust = sum / count;
					if (trust > 0) tls.put(v, trust);
				}

			}
			saveTrust(u, tls);
			trustList(recalls, NDCGs, u, tls);
		}
		recordResults(recalls, NDCGs);

	} /* end of TM3a() */

	/**
	 * Implementation of the <em>TM3b</em> method.
	 * 
	 * @throws Exception
	 */
	private void TM3b() throws Exception
	{
		List<Double> recalls = new ArrayList<>();
		List<Double> NDCGs = new ArrayList<>();

		double lambda = 0.05;
		for (final String u : userRatingsMap.keySet())
		{
			Map<String, Rating> usRatings = userRatingsMap.get(u);
			if (usRatings == null)
			{
				if (userTNsMap.containsKey(u)) recalls.add(0.0);
				continue;
			}
			double meanU = RatingUtils.mean(usRatings, null);
			int sizeU = usRatings.size();

			/* variable for storing u's trusted neighbors */
			Map<String, Double> tls = new HashMap<>();

			for (String v : userRatingsMap.keySet())
			{
				if (u.equals(v)) continue;
				Map<String, Rating> vsRatings = userRatingsMap.get(v);
				if (vsRatings == null) continue;
				int sizeV = vsRatings.size();

				double meanV = RatingUtils.mean(vsRatings, null);

				double sum = 0;
				int count = 0;
				for (String item : usRatings.keySet())
				{
					if (vsRatings.containsKey(item))
					{
						Rating ur = usRatings.get(item);
						Rating vr = vsRatings.get(item);

						double pred = meanU + vr.getRating() - meanV;
						double dist = pred - ur.getRating();
						double val = Math.pow(dist / Dataset.maxScale, 2);

						sum += val;
						count++;
					}
				}

				if (count > 0)
				{
					double trust = 1 - sum / count;
					double factor = (count + 0.0) / (sizeU + sizeV - count);

					trust *= factor;

					if (trust > lambda) tls.put(v, trust);
				}

			}
			saveTrust(u, tls);
			trustList(recalls, NDCGs, u, tls);
		}
		recordResults(recalls, NDCGs);

	} /* end of TM3b() */

	/**
	 * Implementation of the <em>TM5</em> method.
	 * 
	 * @throws Exception
	 */
	private void TM5() throws Exception
	{
		List<Double> recalls = new ArrayList<>();
		List<Double> NDCGs = new ArrayList<>();

		for (final String u : userRatingsMap.keySet())
		{
			Map<String, Rating> usRatings = userRatingsMap.get(u);
			if (usRatings == null)
			{
				if (userTNsMap.containsKey(u)) recalls.add(0.0);
				continue;
			}
			double meanU = RatingUtils.mean(usRatings, null);

			/* variable for storing u's trusted neighbors */
			Map<String, Double> tls = new HashMap<>();

			for (String v : userRatingsMap.keySet())
			{
				if (u.equals(v)) continue;
				Map<String, Rating> vsRatings = userRatingsMap.get(v);
				if (vsRatings == null) continue;

				double meanV = RatingUtils.mean(vsRatings, null);

				double sum = 0;
				int count = 0;
				List<Double> us = new ArrayList<>();
				List<Double> vs = new ArrayList<>();
				for (String item : usRatings.keySet())
				{
					if (vsRatings.containsKey(item))
					{
						Rating ur = usRatings.get(item);
						Rating vr = vsRatings.get(item);

						double pred = meanU + vr.getRating() - meanV;
						double dist = Math.abs(pred - ur.getRating());
						double val = dist / Dataset.maxScale;

						sum += val;
						count++;

						us.add(ur.getRating());
						vs.add(vr.getRating());
					}
				}

				if (count > 0)
				{
					double uncertainty = sum / count;

					if (count < 2) continue;
					double pcc = Sims.pcc(us, vs);
					if (Double.isNaN(pcc)) continue;

					double belief = 0.5 * (1 - uncertainty) * (1 + pcc);
					double trust = belief;

					if (trust > 0) tls.put(v, trust);
				}

			}
			saveTrust(u, tls);
			trustList(recalls, NDCGs, u, tls);
		}
		recordResults(recalls, NDCGs);

	} /* end of TM5() */

	/**
	 * Implementation of the <em>TM4</em> method.
	 * 
	 * @throws Exception
	 */
	private void TM4() throws Exception
	{
		double epsilon = 0.0;
		switch (Dataset.dataset)
		{
			case FILMTRUST:
				epsilon = 0.8;
				break;
			case EPINIONS:
				epsilon = params.readDouble("itrust.tm4.epsilon");
				break;
			default:
				epsilon = 1.0;
				break;
		}

		List<Double> recalls = new ArrayList<>();
		List<Double> NDCGs = new ArrayList<>();

		for (final String u : userRatingsMap.keySet())
		{
			Map<String, Rating> usRatings = userRatingsMap.get(u);
			if (usRatings == null)
			{
				if (userTNsMap.containsKey(u)) recalls.add(0.0);
				continue;
			}
			double meanU = RatingUtils.mean(usRatings, null);

			/* variable for storing u's trusted neighbors */
			Map<String, Double> tls = new HashMap<>();

			for (String v : userRatingsMap.keySet())
			{
				if (u.equals(v)) continue;
				Map<String, Rating> vsRatings = userRatingsMap.get(v);
				if (vsRatings == null) continue;

				double meanV = RatingUtils.mean(vsRatings, null);

				int correct = 0;
				int involve = 0;
				for (String item : usRatings.keySet())
				{
					if (vsRatings.containsKey(item))
					{
						Rating ur = usRatings.get(item);
						Rating vr = vsRatings.get(item);

						double pred = meanU + vr.getRating() - meanV;
						double dist = Math.abs(pred - ur.getRating());

						if (dist <= epsilon) correct++;

						involve++;
					}
				}

				if (involve > 0)
				{
					double trust = (correct + 0.0) / involve;
					if (trust > 0) tls.put(v, trust);
				}

			}
			saveTrust(u, tls);
			trustList(recalls, NDCGs, u, tls);
		}
		recordResults(recalls, NDCGs);

	} /* end of TM4() */
}
