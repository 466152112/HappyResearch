package happy.research.utils;

import happy.research.cf.Dataset;
import happy.research.cf.Rating;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.analysis.integration.TrapezoidIntegrator;

public class TrustUtils
{
	/**
	 * Calculate the confidence between positive feedbacks and negative feedbacks.<br/>
	 * This method is implemented right: according to paper <i>Formal Trust Model for Multiagent Systems</i>,
	 * <ul>
	 * <li>f(0, 4)=0.54</li>
	 * <li>f(1, 3)=0.35</li>
	 * <li>f(2, 2)=0.29</li>
	 * <li>f(3, 1)=0.35</li>
	 * <li>f(6, 2)=0.46</li>
	 * <li>f(69, 29)=0.75</li>
	 * <li>f(500, 100) = 0.87</li>
	 * </ul>
	 * 
	 * @param r
	 *            #positive feedbacks
	 * @param s
	 *            #negative feedbacks
	 * @return <b>confidence</b> between positive feedbacks and negative feedbacks
	 * @throws Exception
	 */
	public static double confidence(final double r, final double s) throws Exception
	{
		TrapezoidIntegrator integrator = new TrapezoidIntegrator();

		final double denominator = denominator(r, s);

		UnivariateRealFunction f = new UnivariateRealFunction() {

			public double value(double x) throws FunctionEvaluationException
			{
				double result = Math.pow(x, r) * Math.pow(1 - x, s) - denominator;
				return Math.abs(result);
			}
		};
		return 0.5 * integrator.integrate(f, 0.0, 1.0) / denominator;
	}

	/**
	 * Profile-level trust, refers to the paper <em>Trust in Recommender Systems</em><br/>
	 * Note that sets <em>a</em> and <em>b</em> are not ratings of the co-rated items, but ratings of all rated items by
	 * each user.
	 * 
	 * @param as
	 *            all ratings of the active user A
	 * @param bs
	 *            all ratings of the other user
	 * @param episilon
	 *            acceptable error/distance between two ratings
	 * @return List<Rating> correctRatings
	 */
	public static List<Rating> profileTrust(List<Rating> as, List<Rating> bs, double episilon)
	{
		List<Rating> corrects = new ArrayList<>();
		for (Rating ar : as)
		{
			String itemId = ar.getItemId();
			for (Rating br : bs)
			{
				if (itemId.equals(br.getItemId()))
				{
					double ai = ar.getRating();
					double bi = br.getRating();

					double error = Math.abs(ai - bi);
					if (error < episilon) corrects.add(br);

					break;
				}
			}
		}

		return corrects;
	}

	/**
	 * K-nearest recommender trust, refers to the paper <em>Trust-Based Collaborative Filtering</em><br/>
	 * Note that sets <em>a</em> and <em>b</em> are not ratings of the co-rated items, but ratings of all rated items by
	 * each user.
	 * 
	 * @param as
	 *            all ratings of the active user A
	 * @param bs
	 *            all ratings of the other user B
	 * @return B's trust score regarding to user A
	 */
	public static double kNRTrust(Map<String, Rating> as, Map<String, Rating> bs)
	{
		return kNRTrust(as, bs, null);
	}

	/**
	 * K-nearest recommender trust, refers to the paper <em>Trust-Based Collaborative Filtering</em><br/>
	 * Note that sets <em>a</em> and <em>b</em> are not ratings of the co-rated items, but ratings of all rated items by
	 * each user.
	 * 
	 * @param as
	 *            {item, rating} all ratings of the active user A
	 * @param bs
	 *            {item, rating} all ratings of the other user B
	 * @param testRating
	 *            A's test rating, needs to be excluded from train sets (learn B's trustworthiness)
	 * @return B's trust score regarding to user A
	 */
	public static double kNRTrust(Map<String, Rating> as, Map<String, Rating> bs, Rating testRating)
	{
		double sum = 0.0;
		int count = 0;
		for (Rating ar : as.values())
		{
			if (ar == testRating) continue;
			String itemId = ar.getItemId();
			count++;
			double trusti = 0;
			if (bs.containsKey(itemId))
			{
				Rating br = bs.get(itemId);
				trusti = 1 - Math.abs(ar.getRating() - br.getRating()) / Dataset.maxScale;
			}
			sum += trusti;
		}

		return sum / count;
	}

	public static double kNRTrust(List<Double> as, List<Double> bs)
	{
		double sum = 0.0;
		int count = 0;
		for (int i = 0; i < as.size(); i++)
		{
			count++;
			sum += 1 - Math.abs(as.get(i) - bs.get(i)) / Dataset.maxScale;
		}

		return sum / count;
	}

	private static double denominator(final double r, final double s) throws Exception
	{
		TrapezoidIntegrator integrator = new TrapezoidIntegrator();

		UnivariateRealFunction f = new UnivariateRealFunction() {

			public double value(double x) throws FunctionEvaluationException
			{
				return Math.pow(x, r) * Math.pow(1 - x, s);
			}
		};
		return integrator.integrate(f, 0.0, 1.0);
	}

	public static void main(String[] args) throws Exception
	{
		System.out.println(confidence(0, 1));
	}

}
