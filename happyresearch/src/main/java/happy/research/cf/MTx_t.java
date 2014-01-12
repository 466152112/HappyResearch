package happy.research.cf;

import java.util.Map;

public class MTx_t extends Thread_t
{

	public MTx_t(int id)
	{
		super(id);
	}

	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		return useTrustRatings(testRating);
	}

}
