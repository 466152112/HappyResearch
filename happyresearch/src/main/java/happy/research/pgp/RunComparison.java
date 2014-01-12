package happy.research.pgp;

import java.util.List;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

public class RunComparison
{
	protected final static Logger logger = Logger.getLogger(RunComparison.class);
	
	private enum TIME_STATE
	{
		ms, second, minute, hour
	};
	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args)
	{
		try
		{
			DOMConfigurator.configure("log4j.xml");
			boolean test_single = false;
			
			if (test_single)
				testSingle(new TraditionalWChainPGP());
			else
				testAll();
		} catch (Exception e)
		{
			logger.error("Exceptions occured in main program: ");
			logger.error(e.getMessage());
		}
	}
	
	private static void testSingle(AbstractPGP pgp) throws Exception
	{
		List<PGPNode> nodes = AbstractPGP.generateTestData();
		Performance p = pgp.firePerformanceTest(nodes);
		logger.info(p);
	}

	private static void testAll() throws Exception
	{
		int numMethods = 6;
		Performance[] pfs = new Performance[numMethods];
		List<PGPNode> nodes = AbstractPGP.generateTestData();
		int runTimes = AbstractPGP.runTimes;

		long begin = System.currentTimeMillis();
		for (int i = 0; i < runTimes; i++)
		{
			long start_time = System.currentTimeMillis();
			if (i == 0)
			{
				pfs[0] = new Performance(AbstractPGP.TRADITIONAL_PGP);
				pfs[1] = new Performance(AbstractPGP.MODIFIED_PGP);
				pfs[2] = new Performance(AbstractPGP.OURMETHOD_PGP);
				pfs[3] = new Performance(AbstractPGP.TRUSTCHAIN_PGP);
				pfs[4] = new Performance(AbstractPGP.TRADITIONAL_CHAIN_PGP);
				pfs[5] = new Performance(AbstractPGP.OURMETHOD_CHAIN_PGP);
			}

			AbstractPGP[] pgps = new AbstractPGP[numMethods];
			pgps[0] = new TraditionalPGP();
			pgps[1] = new ModifiedPGP();
			pgps[2] = new OurMethodPGP();
			pgps[3] = new TrustChainPGP();
			pgps[4] = new TraditionalWChainPGP();
			pgps[5] = new OurMethodWChain();
			
			logger.info(">>> The " + (i + 1) + " th run time performance is as foloows: \n");

			if (nodes == null) nodes = AbstractPGP.generateTestData();
			for (int j = 0; j < pgps.length; j++)
			{
				if (pgps[j] == null) continue;
				Performance pf = pgps[j].firePerformanceTest(nodes);
				logger.info(pf);
				pfs[j].add(pf);
			}

			loggingConsumedTime(System.currentTimeMillis() - start_time, false);
			logger.info("-------------------------------------------------\n");
			
			nodes = null;
		}
		
		if (runTimes > 1)
		{
			for (Performance pf : pfs)
			{
				if (pf == null) continue;
				logger.info(pf.getMethodId());
				logger.info("After " + runTimes + " times running: overall coverage = " + pf.getCoverage()
						/ runTimes);
				logger.info("After " + runTimes + " times running: overall distance = " + pf.getDistance()
						/ runTimes);
				logger.info(null);
			}

			loggingConsumedTime(System.currentTimeMillis() - begin, true);
		}
	}
	
	private static void loggingConsumedTime(double consumedTime, boolean testAll)
	{
		TIME_STATE state = TIME_STATE.ms;
		if (consumedTime > 1000)
		{
			consumedTime /= 1000;
			state = TIME_STATE.second;
		}
		if (consumedTime > 60)
		{
			consumedTime /= 60;
			state = TIME_STATE.minute;
		}
		if (consumedTime > 60)
		{
			consumedTime /= 60;
			state = TIME_STATE.hour;
		}
		if (testAll)
			logger.info("Total time consumed: " + consumedTime + " " + state.name());
		else
			logger.info("This iteration consumes: " + consumedTime + " " + state.name());
	}
}
