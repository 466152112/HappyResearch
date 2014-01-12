package happy.research.cf;

import java.util.Map;

public class TTx_t extends Thread_t
{

	public TTx_t(int id)
	{
		super(id);
	}

	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		return useTrustRatings(testRating);
	}
}
