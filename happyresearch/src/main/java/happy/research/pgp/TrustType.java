package happy.research.pgp;

public enum TrustType
{
	COMPLETED_TRUST(1.0), MARGINALLY_TRUST(0.5), UNKNOWN_TRUST(0.0);

	private double trustValue;

	private TrustType(double trustValue)
	{
		this.trustValue = trustValue;
	}

	public double getTrustValue()
	{
		return trustValue;
	}
	
	@Override
	public String toString()
	{
		return this.name();
	}
}
