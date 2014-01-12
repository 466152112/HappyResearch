package happy.research.cf;

import java.util.Map;

public class GlobalAve_t extends Thread_t
{
	private double	averageRating	= 0;

	public GlobalAve_t(int id, double averageRating)
	{
		super(id);
		this.averageRating = averageRating;
	}

	public void run()
	{
		startThread();

		for (Rating r : threadRatings)
		{
			pf.addPredicts(new Prediction(r, averageRating));
		}

		endThread();
	}

	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		return null;
	}

}
