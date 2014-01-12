package happy.research.pgp;

public class Performance
{
	private String methodId;
	private double accuracy;
	private double coverage;
	private double distance;
	
	public Performance()
	{
	}
	
	public Performance(String method_id)
	{
		this.methodId = method_id;
	}

	@Override
	public String toString()
	{
		return new StringBuilder()
					.append("-------------------------------------------------\n")
					.append(methodId)
					.append("'s Performance: \n")
					.append("The overall coverage = ")
					.append(coverage)
					.append("\nThe overall distance = ")
					.append(distance)
					.append("\nThe overall accuracy = ")
					.append(accuracy)
					.toString();
	}
	
	public Performance add(Performance p)
	{
		if (this.methodId.equals(p.getMethodId()))
		{
			this.accuracy += p.getAccuracy();
			this.coverage += p.getCoverage();
			this.distance += p.getDistance();
		} else
		{
			throw new RuntimeException("Cannot add perforamance for different method");
		}
		return this;
	}

	public double getAccuracy()
	{
		return accuracy;
	}
	
	public void setAccuracy(double accuracy)
	{
		this.accuracy = accuracy;
	}
	
	public double getCoverage()
	{
		return coverage;
	}
	
	public void setCoverage(double coverage)
	{
		this.coverage = coverage;
	}
	
	public double getDistance()
	{
		return distance;
	}
	
	public void setDistance(double distance)
	{
		this.distance = distance;
	}
	
	public String getMethodId()
	{
		return methodId;
	}
	
	public void setMethodId(String methodId)
	{
		this.methodId = methodId;
	}
}
