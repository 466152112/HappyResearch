package happy.research.cf;


public class TrustRating
{
	private String	trustor;
	private String	trustee;
	private double rating;

	public String getTrustor()
	{
		return trustor;
	}

	public void setTrustor(String trustor)
	{
		this.trustor = trustor;
	}

	public String getTrustee()
	{
		return trustee;
	}

	public void setTrustee(String trustee)
	{
		this.trustee = trustee;
	}

	public double getRating()
	{
		return rating;
	}

	public void setRating(double rating)
	{
		this.rating = rating;
	}

	@Override
	public String toString()
	{
		return trustor + " " + trustee + " " + rating;
	}
	
}
