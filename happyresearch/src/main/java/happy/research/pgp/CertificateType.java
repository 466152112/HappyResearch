package happy.research.pgp;

public enum CertificateType
{
	VALID(1), INVALID(-1);

	private int inherentValue;

	private CertificateType(int value)
	{
		this.inherentValue = value;
	}

	public int getInherentValue()
	{
		return this.inherentValue;
	}
	
	@Override
	public String toString()
	{
		return this.name();
	}
}
