package happy.research.cf;

import java.util.List;
import java.util.Map;

public class TrustAllPlus_t extends Thread_t
{
	private double	averageRating	= 0.0;

	public TrustAllPlus_t(int id, double averageRating)
	{
		super(id);
		this.averageRating = averageRating;
		this.postProcessing = true;
	}

	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		return useAllData(testRating);
	}

	@Override
	protected void doPostProcessing(List<Rating> unPredictableRatings)
	{
		for (Rating r : unPredictableRatings)
		{
			pf.addPredicts(new Prediction(r, averageRating));
		}
	}

}
