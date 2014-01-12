package happy.research.utils;

import happy.research.cf.TrustRating;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TidalTrust
{

	/**
	 * TidalTrust algorithm to infer trust
	 * 
	 * @param userTNsMap
	 *            {user, {trustors}} map
	 * @param userTrustorsMap
	 * @param userRatingsMap
	 *            {user, {trust ratings}} map
	 * @param source
	 *            source user
	 * @param sink
	 *            target user
	 * @param maxDepth
	 *            maximum length of the searching path
	 * @return
	 */

	@SuppressWarnings({ "unchecked" })
	public static Map<String, Double> runAlgorithm(Map<String, Map<String, Double>> userTrusteesMap,
			Map<String, Map<String, Double>> userTrustorsMap, Map<String, List<TrustRating>> userRatingsMap,
			String source, int maxDepth)
	{
		Map<String, Double> trustees = userTrusteesMap.get(source);
		if (trustees == null) return null; // no out-link from source node

		LinkedList<String> toVisitNodes = new LinkedList<>();
		for (String tee : trustees.keySet())
			toVisitNodes.push(tee);

		List<String> visitedNodes = new ArrayList<>();
		visitedNodes.add(source);

		List<String> tempNodes = new ArrayList<>();
		List<String>[] data = new ArrayList[maxDepth + 1];
		for (int i = 0; i < data.length; i++)
			data[i] = new ArrayList<>();

		int currentDepth = 1;
		data[0].add(source);

		Map<String, Double> pathFlowMap = new HashMap<>();
		pathFlowMap.put(source, Double.MAX_VALUE);
		Map<String, List<String>> nodesChildren = new HashMap<>();

		Map<String, Double> trustors = null;
		List<TrustRating> ratings = null;
		while (!toVisitNodes.isEmpty() && currentDepth <= maxDepth)
		{
			String node = toVisitNodes.pop();
			data[currentDepth].add(node);

			if (visitedNodes.contains(node)) continue;
			else visitedNodes.add(node);

			trustees = userTrusteesMap.get(node);
			if (trustees != null)
			{
				for (String tee : trustees.keySet())
				{
					if (!tempNodes.contains(tee) && !visitedNodes.contains(tee)) tempNodes.add(tee);
				}
			}

			trustors = userTrustorsMap.get(node);
			ratings = userRatingsMap.get(node);
			for (String tor : trustors.keySet())
			{
				if (!visitedNodes.contains(tor)) continue;

				double flow = Math.min(pathFlowMap.get(tor), getTrustRating(ratings, tor, node));
				Double flow2 = pathFlowMap.get(node);

				double pathflow = Math.max(flow, flow2 == null ? 0 : flow2);
				pathFlowMap.put(node, pathflow);

				List<String> children = null;
				if (nodesChildren.containsKey(tor)) children = nodesChildren.get(tor);
				else children = new ArrayList<>();

				children.add(node);

				nodesChildren.put(tor, children);
			}

			if (toVisitNodes.isEmpty() && !tempNodes.isEmpty())
			{
				toVisitNodes.addAll(tempNodes);
				currentDepth++;
				tempNodes.clear();
			}
		}

		currentDepth = 1;
		visitedNodes.clear();
		visitedNodes.add(source);
		Map<String, Double> trustScores = new HashMap<>();
		trustScores.put(source, 1.0);
		while (currentDepth <= maxDepth)
		{
			for (String sink : data[currentDepth])
			{
				Double threshold = pathFlowMap.get(sink);
				if (threshold == null) continue;

				trustors = userTrustorsMap.get(sink);
				double numerator = 0;
				double denominator = 0;
				for (String tor : trustors.keySet())
				{
					if (!visitedNodes.contains(tor)) continue;
					double rating = getTrustRating(userRatingsMap, tor, sink);
					if (pathFlowMap.get(tor) >= threshold && rating >= 0)
					{
						numerator += trustScores.get(tor) * rating;
						denominator += trustScores.get(tor);
					}
				}
				if (denominator > 0)
				{
					trustScores.put(sink, numerator / denominator);
					visitedNodes.add(sink);
				}
			}
			currentDepth++;
		}

		trustScores.remove(source);
		return trustScores;
	}

	private static double getTrustRating(List<TrustRating> ratings, String source, String sink)
	{
		if (ratings != null)
		{
			for (TrustRating r : ratings)
			{
				if (r.getTrustor().equals(source) && r.getTrustee().equals(sink)) return r.getRating();
			}
		}

		return Double.NaN;
	}

	private static double getTrustRating(Map<String, List<TrustRating>> userRatingsMap, String source, String sink)
	{
		return getTrustRating(userRatingsMap.get(sink), source, sink);
	}


}
