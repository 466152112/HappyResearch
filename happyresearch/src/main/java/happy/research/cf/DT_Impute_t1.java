package happy.research.cf;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 
 * @author guoguibing
 * 
 */
public class DT_Impute_t1 extends DT_Impute_mt implements Runnable
{

	public DT_Impute_t1(List<String> users, int id) throws Exception
	{
		this.threadUsers = users;
		this.id = id;
	}

	public void run()
	{
		Thread.currentThread().setName("[Build Middle Data] [Thread " + id + "]");
		Logs.debug("Start running {} ...", Thread.currentThread().getName());

		Set<String> items = itemRatingsMap.keySet();
		for (String user : threadUsers)
		{
			Map<String, Double> tns = userTNsMap.get(user);
			if (tns == null) continue;

			for (String tn : tns.keySet())
			{
				String ratingPath = trustDirPath + tn + ".txt";
				File ratingFile = new File(ratingPath);
				if (ratingFile.exists()) continue;

				/* predict item's rating for trusted neighbors */
				Map<String, Rating> ratings = userRatingsMap.get(tn);

				List<Rating> missingRatings = new ArrayList<>();
				for (String item : items)
				{
					if (ratings != null && !ratings.containsKey(item))
					{
						Map<String, Double> dns = userDNsMap.get(tn);
						if (dns == null) continue;

						double sum = 0.0;
						int count = 0;
						for (String dn : dns.keySet())
						{
							Map<String, Rating> nrs = userRatingsMap.get(dn);
							if (nrs != null && nrs.containsKey(item))
							{
								sum += nrs.get(item).getRating();
								count++;
							}
						}

						if (count > 0)
						{
							Rating r = new Rating();
							r.setUserId(tn);
							r.setItemId(item);
							r.setRating(Dataset.maxScale - sum / count + Dataset.minScale);

							missingRatings.add(r);
						}

					}
				}

				if (missingRatings.size() > 0) try
				{
					FileIO.writeListSyn(ratingPath, missingRatings);
				} catch (Exception e)
				{
					e.printStackTrace();
				}

			}

		}
		Logs.debug("Finish running {}", Thread.currentThread().getName());

	}

}
