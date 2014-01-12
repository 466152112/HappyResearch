package happy.research.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author guoguibing
 * 
 */
public class MoleTrust
{
	/**
	 * Implementing MoleTrust Local Trust Metric: based on Epinions data set
	 * 
	 * @param trust_data
	 * @param sourceUser
	 * @param horizon
	 * @return map {trusted neighbour - trust score} , if no trusted neighbour, then return empty map
	 */
	@SuppressWarnings("unchecked")
	public static Map<Integer, Double> runAlgorithm(Map<Integer, Map<Integer, Double>> trust_data, Integer sourceUser,
			int horizon)
	{
		// all the visited nodes
		List<Integer> nodes = new ArrayList<>(40163);
		// source user - edges[target users - trust value]
		Map<Integer, Map<Integer, Double>> edges = new HashMap<>(40163);

		/* Step 1: construct directed graphic and remove cyclic */
		int dist = 0;
		List<Integer>[] users = new List[horizon + 1];
		users[dist] = new ArrayList<>();
		users[dist].add(sourceUser);
		nodes.add(sourceUser);

		// Denote su: source user; tu: target user
		while (dist < horizon)
		{
			dist++;
			users[dist] = new ArrayList<>();
			for (Integer su : users[dist - 1])
			{
				Map<Integer, Double> tns = trust_data.get(su);
				if (tns == null) continue; // no trusted neighbours
				for (Integer tn : tns.keySet())
				{
					if (!nodes.contains(tn) && !users[dist].contains(tn) && !users[dist - 1].contains(tn))
					{
						users[dist].add(tn);
					}
				}
			}

			for (Integer su : users[dist - 1])
			{
				Map<Integer, Double> tns = trust_data.get(su);
				if (tns == null) continue;
				for (Integer tu : tns.keySet())
				{
					if (!nodes.contains(tu) && users[dist].contains(tu))
					{
						Map<Integer, Double> tuTrusts;
						if (edges.containsKey(su)) tuTrusts = edges.get(su);
						else tuTrusts = new HashMap<>();

						double trustValue = tns.get(tu);
						tuTrusts.put(tu, trustValue);
						edges.put(su, tuTrusts);
					}
				}
			}
		}

		/* Step 2: Evaluate trust score */
		dist = 0;
		double threashold = 0.5;
		// trusted neighbours - trust score map
		Map<Integer, Double> trustScores = new HashMap<>();
		trustScores.put(sourceUser, 1.0);
		while (dist < horizon)
		{
			dist++;
			for (Integer tu : users[dist])
			{

				double sum = 0.0;
				double weights = 0.0;
				for (Integer su : users[dist - 1])
				{
					Map<Integer, Double> tuTrusts = edges.get(su);
					if (tuTrusts == null) continue; // no edges for user su
					if (tuTrusts.containsKey(tu))
					{
						double trust_edge = tuTrusts.get(tu);
						if (trust_edge > threashold)
						{
							sum += trust_edge * trustScores.get(su);
							weights += trustScores.get(su);
						}
					}
				}
				double score = sum / weights;
				trustScores.put(tu, score);
			}
		}

		trustScores.remove(sourceUser);
		return trustScores;
	}

	/**
	 * Implement MoleTrust on FilmTrust data set
	 * 
	 * @param trust_data
	 * @param sourceUser
	 * @param horizon
	 * @return map {trusted neighbour - trust score} , if no trusted neighbour, then return empty map
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Double> runAlgorithm(Map<String, Map<String, Double>> trust_data, String sourceUser,
			int horizon)
	{
		// all the visited nodes
		List<String> nodes = new ArrayList<>(1000);
		// source user - edges[target users - trust value]
		Map<String, Map<String, Double>> edges = new HashMap<>(1000);

		/* Step 1: construct directed graphic and remove cyclic */
		int dist = 0;
		List<String>[] users = new List[horizon + 1];
		users[dist] = new ArrayList<>();
		users[dist].add(sourceUser);
		nodes.add(sourceUser);

		// Denote su: source user; tu: target user
		while (dist < horizon)
		{
			dist++;
			users[dist] = new ArrayList<>();
			for (String su : users[dist - 1])
			{
				Map<String, Double> tns = trust_data.get(su);
				if (tns == null) continue; // no trusted neighbours
				for (String tn : tns.keySet())
				{
					if (!nodes.contains(tn) && !users[dist].contains(tn) && !users[dist - 1].contains(tn))
					{
						users[dist].add(tn);
					}
				}
			}

			for (String su : users[dist - 1])
			{
				Map<String, Double> tns = trust_data.get(su);
				if (tns == null) continue;
				for (String tu : tns.keySet())
				{
					if (!nodes.contains(tu) && users[dist].contains(tu))
					{
						Map<String, Double> tuTrusts;
						if (edges.containsKey(su)) tuTrusts = edges.get(su);
						else tuTrusts = new HashMap<>();

						double trustValue = trust_data.get(su).get(tu);
						tuTrusts.put(tu, trustValue);
						edges.put(su, tuTrusts);
					}
				}
			}
		}

		/* Step 2: Evaluate trust score */
		dist = 0;
		double threashold = 0.0;
		// trusted neighbours - trust score map
		Map<String, Double> trustScores = new HashMap<>();
		trustScores.put(sourceUser, 1.0);
		while (dist < horizon)
		{
			dist++;
			for (String tu : users[dist])
			{

				double sum = 0.0;
				double weights = 0.0;
				for (String su : users[dist - 1])
				{
					Map<String, Double> tuTrusts = edges.get(su);
					if (tuTrusts == null) continue; // no edges for user su
					if (tuTrusts.containsKey(tu))
					{
						double trust_edge = tuTrusts.get(tu);
						if (trust_edge > threashold)
						{
							sum += trust_edge * trustScores.get(su);
							weights += trustScores.get(su);
						}
					}
				}
				double score = sum / weights;
				trustScores.put(tu, score);
			}
		}

		trustScores.remove(sourceUser);
		return trustScores;
	}
}
