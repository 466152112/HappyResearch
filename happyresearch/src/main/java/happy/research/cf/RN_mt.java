package happy.research.cf;


import happy.coding.math.Sims;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class RN_mt extends DefaultCF_mt
{
	private Map<String, Map<String, Double>>	userTNsCorrMap	= new HashMap<>();

	public RN_mt()
	{
		methodId = "Reconstructed Network";
	}

	@Override
	protected void preProcessing()
	{
		/* Reconstruct Trust Network */
		String path = Dataset.DIRECTORY + "Reconstruction\\network.txt";
		File file = new File(path);
		if (!file.exists())
		{
			try
			{
				logger.debug("Pre-Processing: Reconstruct Trust Network ...");
				sampleDataset(path);
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		} else
		{
			try
			{
				logger.debug("Pre-Processing: Loading Reconstructed Trust Network ...");
				loadTestSet(path);
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		logger.debug("Done!");
	}

	private void loadTestSet(String file) throws Exception
	{
		BufferedReader br = new BufferedReader(new FileReader(new File(file)));
		String line = null;
		while ((line = br.readLine()) != null)
		{
			String[] data = line.split(" ");
			String trustor = data[0];
			String trustee = data[1];
			double similarity = Double.parseDouble(data[2]);

			Map<String, Double> tnsCorrMap = null;
			if (userTNsCorrMap.containsKey(trustor)) tnsCorrMap = userTNsCorrMap.get(trustor);
			else tnsCorrMap = new HashMap<>();
			tnsCorrMap.put(trustee, similarity);

			userTNsCorrMap.put(trustor, tnsCorrMap);

		}
		br.close();
	}

	@Override
	protected Performance runMultiThreads() throws Exception
	{
		for (int i = 0; i < ratingArrays.length; i++)
		{
			threads[i] = new Thread(new RN_t(i, userTNsCorrMap));
			threads[i].start();
		}
		for (Thread tr : threads)
			tr.join();

		return pf;
	}

	private void sampleDataset(String path) throws Exception
	{
		File dir = new File(path.substring(0, 1 + path.lastIndexOf('\\')));
		if (!dir.exists()) dir.mkdirs();
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(path)));
		for (Entry<String, Map<String, Double>> entry : userTNsMap.entrySet())
		{
			String user = entry.getKey();
			Map<String, Double> tns = entry.getValue();
			Map<String, Rating> rs = userRatingsMap.get(user);
			if (rs == null || rs.size() <= 3) continue;
			if (tns.size() < 10) continue;

			int count = 0;
			Map<String, Double> tnsCorrMap = new HashMap<>();
			for (String tn : tns.keySet())
			{
				double similarity = computeSimilarity(user, tn, null);
				if (Double.isNaN(similarity)) continue;
				if (similarity >= 0.5) count++;

				tnsCorrMap.put(tn, similarity);
			}
			if (count >= 4)
			{
				userTNsCorrMap.put(user, tnsCorrMap);
				for (Entry<String, Double> en : tnsCorrMap.entrySet())
				{
					bw.write(user + " " + en.getKey() + " " + en.getValue() + "\n");
				}
			}
		}
		bw.flush();
		bw.close();
	}

	protected double computeSimilarity(String userA, String userB, Rating exception)
	{
		if (userA.equals(userB)) return Double.NaN;

		Map<String, Rating> asRatings = userRatingsMap.get(userA);
		Map<String, Rating> bsRatings = userRatingsMap.get(userB);
		if (asRatings == null || bsRatings == null || asRatings.size() < 1 || bsRatings.size() < 1) return Double.NaN;

		List<Double> a = new ArrayList<>();
		List<Double> b = new ArrayList<>();

		for (Rating ar : asRatings.values())
		{
			if (exception != null && ar.getItemId().equals(exception.getItemId())) continue;
			if (bsRatings.containsKey(ar.getItemId()))
			{
				a.add(ar.getRating());
				b.add(bsRatings.get(ar.getItemId()).getRating());
			}
		}

		return Sims.pcc(a, b);
	}

}
