package happy.research.cf;

import java.util.Map;

/**
 * Leave-one-out method
 * 
 * @author guoguibing
 */
public class TrustAll_t extends Thread_t
{

	public TrustAll_t(int id)
	{
		super(id);
	}

	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		return useAllData(testRating);
	}
}
