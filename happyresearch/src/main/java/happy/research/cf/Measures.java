package happy.research.cf;

import java.util.HashMap;
import java.util.Map;

public class Measures
{
	/*
	 * prediction measures 
	 */
	private double					MAE;
	private double					MAUE;							// mean absolute user error
	private double					MACE;							// mean absolute conf error
	private double					RMSE;
	private double					RC;							// rating coverage
	private double					UC;							// user coverage

	private int						covered_users;
	private int						covered_ratings;
	private int						total_users;
	private int						total_ratings;

	/*
	 * ranking measures: {cutoff@n, value}
	 */
	private Map<Integer, Double>	MAP			= new HashMap<>();
	private Map<Integer, Double>	MRR			= new HashMap<>();
	private Map<Integer, Double>	NDCG		= new HashMap<>();
	private Map<Integer, Double>	Precision	= new HashMap<>();
	private Map<Integer, Double>	Recall		= new HashMap<>();
	private Map<Integer, Double>	F1			= new HashMap<>();

	/*
	 * diversity measures
	 */
	private double					UD;							// inter-user diversity
	private double					IN;							// intra-user diversity (item novelty)
	private double					SD;							// set diversity

	public double getMAE()
	{
		return MAE;
	}

	public void setMAE(double MAE)
	{
		this.MAE = MAE;
	}

	public double getRMSE()
	{
		return RMSE;
	}

	public void setRMSE(double RMSE)
	{
		this.RMSE = RMSE;
	}

	public double getRC()
	{
		return RC;
	}

	public void setRC(int covered_ratings, int total_ratings)
	{
		this.covered_ratings = covered_ratings;
		this.total_ratings = total_ratings;
		this.RC = (covered_ratings + 0.0) / total_ratings;
	}

	public Map<Integer, Double> getMAP()
	{
		return MAP;
	}

	public double getMAP(int n)
	{
		return MAP.get(n);
	}

	public void addMAP(int n, double MAP)
	{
		this.MAP.put(n, MAP);
	}

	public Map<Integer, Double> getMRR()
	{
		return MRR;
	}

	public double getMRR(int n)
	{
		return MRR.get(n);
	}

	public void addMRR(int n, double MRR)
	{
		this.MRR.put(n, MRR);
	}

	public Map<Integer, Double> getNDCG()
	{
		return NDCG;
	}

	public double getNDCG(int n)
	{
		return NDCG.get(n);
	}

	public void addNDCG(int n, double nDCG)
	{
		this.NDCG.put(n, nDCG);
	}

	public Map<Integer, Double> getRecall()
	{
		return Recall;
	}

	public double getRecall(int n)
	{
		return Recall.get(n);
	}

	public void addRecall(int n, double recall)
	{
		Recall.put(n, recall);
	}

	public Map<Integer, Double> getF1()
	{
		return F1;
	}

	public double getF1(int n)
	{
		return F1.get(n);
	}

	public void addF1(int n, double F1)
	{
		this.F1.put(n, F1);
	}

	public double getMAUE()
	{
		return MAUE;
	}

	public void setMAUE(double MAUE)
	{
		this.MAUE = MAUE;
	}

	public double getMACE()
	{
		return MACE;
	}

	public void setMACE(double MACE)
	{
		this.MACE = MACE;
	}

	public double getUD()
	{
		return UD;
	}

	public void setUD(double UD)
	{
		this.UD = UD;
	}

	public double getUC()
	{
		return UC;
	}

	public void setUC(int covered_users, int total_users)
	{
		this.covered_users = covered_users;
		this.total_users = total_users;
		this.UC = (covered_users + 0.0) / total_users;
	}

	public int getCoveredUsers()
	{
		return covered_users;
	}

	public int getCoveredRatings()
	{
		return covered_ratings;
	}

	public int getTotalUsers()
	{
		return total_users;
	}

	public int getTotalRatings()
	{
		return total_ratings;
	}

	public Map<Integer, Double> getPrecision()
	{
		return Precision;
	}

	public double getPrecision(int n)
	{
		return Precision.get(n);
	}

	public void addPrecision(int n, double precision)
	{
		Precision.put(n, precision);
	}

	public double getIN()
	{
		return IN;
	}

	public void setIN(double IN)
	{
		this.IN = IN;
	}

	public double getSD()
	{
		return SD;
	}

	public void setSD(double sD)
	{
		SD = sD;
	}

}
