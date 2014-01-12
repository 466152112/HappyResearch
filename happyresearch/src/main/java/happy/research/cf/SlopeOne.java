package happy.research.cf;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
* Daniel Lemire: A simple implementation of the weighted slope one
* algorithm in Java for item-based collaborative filtering. <br/>  
*
* See main function for example. June 1st 2006. <br/>
* Revised by Marco Ponzi on March 29th 2007. <br/>
* 
* Revised by Guibing Guo on June 7th, 2013.
*/

public class SlopeOne
{

	public static void main(String args[])
	{
		// this is my data base
		Map<String, Map<String, Double>> data = new HashMap<>();
		// items
		String item_candy = "candy";
		String item_dog = "dog";
		String item_cat = "cat";
		String item_war = "war";
		String item_food = "strange food";

		mAllItems = new String[] { item_candy, item_dog, item_cat, item_war, item_food };

		//I'm going to fill it in
		HashMap<String, Double> user1 = new HashMap<>();
		HashMap<String, Double> user2 = new HashMap<>();
		HashMap<String, Double> user3 = new HashMap<>();
		HashMap<String, Double> user4 = new HashMap<>();
		user1.put(item_candy, 1.0);
		user1.put(item_dog, 0.5);
		user1.put(item_war, 0.1);
		data.put("Bob", user1);
		user2.put(item_candy, 1.0);
		user2.put(item_cat, 0.5);
		user2.put(item_war, 0.2);
		data.put("Jane", user2);
		user3.put(item_candy, 0.9);
		user3.put(item_dog, 0.4);
		user3.put(item_cat, 0.5);
		user3.put(item_war, 0.1);
		data.put("Jo", user3);
		user4.put(item_candy, 0.1);
		user4.put(item_war, 1.0);
		user4.put(item_food, 0.4);
		data.put("StrangeJo", user4);

		// next, I create my predictor engine
		SlopeOne so = new SlopeOne(data);
		System.out.println("Here's the data I have accumulated...");
		so.printData();
		// then, I'm going to test it out...
		HashMap<String, Double> user = new HashMap<>();
		System.out.println("Ok, now we predict...");
		user.put(item_food, 0.4);
		System.out.println("Inputting...");
		SlopeOne.print(user);
		System.out.println("Getting...");
		SlopeOne.print(so.predict(user));
		//
		user.put(item_war, 0.2);
		System.out.println("Inputting...");
		SlopeOne.print(user);
		System.out.println("Getting...");
		SlopeOne.print(so.predict(user));
	}

	Map<String, Map<String, Double>>	mData;
	Map<String, Map<String, Double>>	diffMatrix;
	Map<String, Map<String, Integer>>	freqMatrix;

	static String[]						mAllItems;

	public SlopeOne(Map<String, Map<String, Double>> data)
	{
		mData = data;
		buildDiffMatrix();
	}

	/**
	* Based on existing data, and using weights,
	* try to predict all missing ratings.
	* The trick to make this more scalable is to consider
	* only mDiffMatrix entries having a large  (>1) mFreqMatrix
	* entry.
	*
	* It will output the prediction 0 when no prediction is possible.
	*/
	public Map<String, Double> predict(Map<String, Double> user)
	{
		HashMap<String, Double> predictions = new HashMap<>();
		HashMap<String, Integer> frequencies = new HashMap<>();
		for (String j : diffMatrix.keySet())
		{
			frequencies.put(j, 0);
			predictions.put(j, 0.0);
		}
		for (String j : user.keySet())
		{
			for (String k : diffMatrix.keySet())
			{
				try
				{
					Double newval = (diffMatrix.get(k).get(j) + user.get(j)) * freqMatrix.get(k).get(j).intValue();
					predictions.put(k, predictions.get(k) + newval);
					frequencies.put(k, frequencies.get(k) + freqMatrix.get(k).get(j).intValue());
				} catch (NullPointerException e)
				{}
			}
		}
		HashMap<String, Double> cleanpredictions = new HashMap<>();
		for (String j : predictions.keySet())
		{
			if (frequencies.get(j) > 0)
			{
				cleanpredictions.put(j, predictions.get(j) / frequencies.get(j).intValue());
			}
		}
		for (String j : user.keySet())
		{
			cleanpredictions.put(j, user.get(j));
		}
		return cleanpredictions;
	}

	/**
	* Based on existing data, and not using weights,
	* try to predict all missing ratings.
	* The trick to make this more scalable is to consider
	* only mDiffMatrix entries having a large  (>1) mFreqMatrix
	* entry.
	*/
	public Map<String, Double> weightlesspredict(Map<String, Double> user)
	{
		HashMap<String, Double> predictions = new HashMap<>();
		HashMap<String, Integer> frequencies = new HashMap<>();
		for (String j : diffMatrix.keySet())
		{
			predictions.put(j, 0.0);
			frequencies.put(j, 0);
		}
		for (String j : user.keySet())
		{
			for (String k : diffMatrix.keySet())
			{
				//System.out.println("Average diff between "+j+" and "+ k + " is "+mDiffMatrix.get(k).get(j).floatValue()+" with n = "+mFreqMatrix.get(k).get(j).floatValue());
				Double newval = (diffMatrix.get(k).get(j) + user.get(j));
				predictions.put(k, predictions.get(k) + newval);
			}
		}
		for (String j : predictions.keySet())
		{
			predictions.put(j, predictions.get(j) / user.size());
		}
		for (String j : user.keySet())
		{
			predictions.put(j, user.get(j));
		}
		return predictions;
	}

	public void printData()
	{
		for (String user : mData.keySet())
		{
			System.out.println(user);
			print(mData.get(user));
		}
		for (int i = 0; i < mAllItems.length; i++)
		{
			System.out.print("\n" + mAllItems[i] + ":");
			printMatrixes(diffMatrix.get(mAllItems[i]), freqMatrix.get(mAllItems[i]));
		}
	}

	private void printMatrixes(Map<String, Double> ratings, Map<String, Integer> frequencies)
	{
		for (int j = 0; j < mAllItems.length; j++)
		{
			System.out.format("%10.3f", ratings.get(mAllItems[j]));
			System.out.print(" ");
			System.out.format("%10d", frequencies.get(mAllItems[j]));
		}
		System.out.println();
	}

	public static void print(Map<String, Double> user)
	{
		for (String j : user.keySet())
		{
			System.out.println(" " + j + " --> " + user.get(j).floatValue());
		}
	}

	public void buildDiffMatrix()
	{
		diffMatrix = new HashMap<>();
		freqMatrix = new HashMap<>();
		// first iterate through users
		for (Map<String, Double> user : mData.values())
		{
			// then iterate through user data
			for (Entry<String, Double> entry : user.entrySet())
			{
				String i1 = entry.getKey();
				double r1 = entry.getValue();

				if (!diffMatrix.containsKey(i1))
				{
					diffMatrix.put(i1, new HashMap<String, Double>());
					freqMatrix.put(i1, new HashMap<String, Integer>());
				}

				for (Entry<String, Double> entry2 : user.entrySet())
				{
					String i2 = entry2.getKey();
					double r2 = entry2.getValue();

					int cnt = 0;
					if (freqMatrix.get(i1).containsKey(i2)) cnt = freqMatrix.get(i1).get(i2);
					double diff = 0.0;
					if (diffMatrix.get(i1).containsKey(i2)) diff = diffMatrix.get(i1).get(i2);
					double new_diff = r1 - r2;

					freqMatrix.get(i1).put(i2, cnt + 1);
					diffMatrix.get(i1).put(i2, diff + new_diff);
				}
			}
		}
		for (String j : diffMatrix.keySet())
		{
			for (String i : diffMatrix.get(j).keySet())
			{
				Double oldvalue = diffMatrix.get(j).get(i);
				int count = freqMatrix.get(j).get(i).intValue();
				diffMatrix.get(j).put(i, oldvalue / count);
			}
		}
	}
}
