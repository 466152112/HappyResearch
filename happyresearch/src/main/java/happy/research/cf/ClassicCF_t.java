package happy.research.cf;

import java.util.Map;

/**
 * This class implements PCC-based CF
 * 
 * @author guoguibing
 */
public class ClassicCF_t extends Thread_t
{

	public ClassicCF_t(int id)
	{
		super(id);
	}

	@Override
	protected Map<String, Double>[] buildModel(Rating testRating)
	{
		return useSimilarRatings(testRating);
	}

}