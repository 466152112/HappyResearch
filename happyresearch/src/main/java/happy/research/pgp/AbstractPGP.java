package happy.research.pgp;

import happy.coding.math.Randoms;
import happy.research.pgp.PGPNode.SignerType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.MaxIterationsExceededException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.analysis.integration.SimpsonIntegrator;
import org.apache.log4j.Logger;

public abstract class AbstractPGP
{
	protected static Logger logger = Logger.getLogger(AbstractPGP.class);
	protected static int 	numNodes = 0;
	protected static int 	COMPLETES_NEEDED = 0;
	protected static int 	MARGINALS_NEEDED = 0;
	protected static double CONFIDENCE_THRESHOLD = 0.0;
	protected static double SIMILARITY_THRESHOLD = 0.0;
	protected static double CANDIDATE_VECTOR_RATIO = 0.0;
	protected static double COMPLETE_TRUST_THRESHOLD = 0.0;
	protected static double MARGINAL_TRUST_THRESHOLD = 0.0;
	protected static int 	runTimes = 0;
	protected static double CHAIN_TRUSTVALUE_THRESHOLD = 0.0;
	
	protected static String TRADITIONAL_PGP = "Traditional PGP";
	protected static String MODIFIED_PGP = "Modified PGP";
	protected static String OURMETHOD_PGP = "Our Method PGP";
	protected static String TRUSTCHAIN_PGP = "Trust Chain PGP";
	protected static String TRUSTCHAINWTHRESHOLD_PGP = "Trust Chain with threshold PGP";

	protected static String TRADITIONAL_CHAIN_PGP = "Tranditional with chain of trust PGP";
	protected static String OURMETHOD_CHAIN_PGP = "Our Method with chain of trust PGP";
	
	private static double MAX_DISTANCE = Math.abs(CertificateType.VALID.getInherentValue()
			- CertificateType.INVALID.getInherentValue());
	private static double ACCURACY_PERCENT = 0.2;
	protected static double ACCURACY_THRESHOLD = MAX_DISTANCE * ACCURACY_PERCENT;

	public static List<PGPNode> generateTestData() throws Exception
	{
		// load configuration files
		EnvironmentParams params = EnvironmentParams.createInstance();
		params.loadConfigFile("pgp.config.properties");

		numNodes = params.numNodes;
		MARGINALS_NEEDED = params.marginalsNeeded;
		COMPLETES_NEEDED = params.completesNeeded;
		CONFIDENCE_THRESHOLD = params.confidence_threshold;
		SIMILARITY_THRESHOLD = params.similarity_threshold;
		CANDIDATE_VECTOR_RATIO = params.ratCandidateVector;
		COMPLETE_TRUST_THRESHOLD = params.complete_trust_threshold;
		MARGINAL_TRUST_THRESHOLD = params.marginal_trust_threshold;
		runTimes = params.runTimes;
		CHAIN_TRUSTVALUE_THRESHOLD = params.chain_trust_value_threshold;
		
		// generate initial nodes
		List<PGPNode> nodes = generateNodes(params);
		
		// signing
		int[] xpSignerIndexArray = xpSign(nodes, params);
		int[] mdSignerIndexArray = mdSign(nodes, params, xpSignerIndexArray);
		int[] exceptions = new int[xpSignerIndexArray.length + mdSignerIndexArray.length];
		for (int i = 0; i < exceptions.length; i++)
		{
			if (i < xpSignerIndexArray.length)
				exceptions[i] = xpSignerIndexArray[i];
			else
				exceptions[i] = mdSignerIndexArray[i - xpSignerIndexArray.length];
		}
		Arrays.sort(exceptions);
		@SuppressWarnings("unused")
		int[] nbSignerIndexArray = nbSign(nodes, params, exceptions);
		
		// print out test data
		for (PGPNode node : nodes)
			logger.debug(node);
		
		return nodes;
	}
	
	protected abstract Performance firePerformanceTest(final List<PGPNode> nodes);
	
	protected double evaluatePearsonSimilarity(double rx, double ry, int[] rxs, int[] rys, int dimention)
	{
		double denominator_x = 0.0, denominator_y = 0.0, numerator = 0.0;
		double tx = 0.0, ty = 0.0;
		for (int i = 0; i < dimention; i++)
		{
			tx = rxs[i] - rx;
			ty = rys[i] - ry;
			numerator += tx * ty;
			denominator_x += tx * tx;
			denominator_y += ty * ty;
		}
		if (denominator_x == 0 && denominator_y == 0 && rx == ry)
		{
			return 1.0;
		} else if (denominator_x == 0 && denominator_y == 0 && (rx + ry == 0))
		{
			return 0.0;
		}
		return numerator / Math.sqrt(denominator_x * denominator_y);
	}

	protected double evaluateCosineSimilarity(int[] rxs, int[] rys, int dimension)
	{
		double denominator_x = 0.0, denominator_y = 0.0, numerator = 0.0;
		for (int i = 0; i < dimension; i++)
		{
			numerator += rxs[i] * rys[i];
			denominator_x += rxs[i] * rxs[i];
			denominator_y += rys[i] * rys[i];
		}
		return numerator / (Math.sqrt(denominator_x) * Math.sqrt(denominator_y));
	}

	protected double calculateCertainty(final double r, final double s) throws MaxIterationsExceededException,
			FunctionEvaluationException, IllegalArgumentException
	{
		SimpsonIntegrator integrator = new SimpsonIntegrator();
		
		final double denominator = calculateDenominator(r, s);
	
		UnivariateRealFunction f = new UnivariateRealFunction() {
			
			public double value(double x) throws FunctionEvaluationException
			{
				double result = Math.pow(x, r) * Math.pow(1 - x, s) / denominator - 1;
				return Math.abs(result);
			}
		};
		return integrator.integrate(f, 0.0, 1.0);
	}

	private double calculateDenominator(final double r, final double s) throws MaxIterationsExceededException,
			FunctionEvaluationException, IllegalArgumentException
	{
		SimpsonIntegrator integrator = new SimpsonIntegrator();
		
		UnivariateRealFunction f = new UnivariateRealFunction() {
			
			public double value(double x) throws FunctionEvaluationException
			{
				return Math.pow(x, r) * Math.pow(1 - x, s);
			}
		};
		return integrator.integrate(f, 0.0, 1.0);
	}

	protected Performance calculatePerformance(String methodId, int[] coversCount, double[] coverages,
			double[] distances, double[] accuracies)
	{
		// overall performance
		Performance p = new Performance(methodId);
		
		// coverage
		double coverage_sum = 0.0;
		for (int i = 0; i < coverages.length; i++)
			coverage_sum += coverages[i];
		p.setCoverage(coverage_sum / coverages.length);
		
		// distance
		double distance_sum = 0.0;
		int count = 0;
		for (int i = 0; i < distances.length; i++)
			if (coversCount[i] > 0)
			{
				count++;
				distance_sum += distances[i];
			}
		p.setDistance(distance_sum / count);
		
		// accuracy
		double accuracy_sum = 0.0;
		for (double accuracy : accuracies)
			accuracy_sum += accuracy;
		p.setAccuracy(accuracy_sum / accuracies.length);
		
		return p;
	}

	private static List<PGPNode> generateNodes(EnvironmentParams params)
			throws Exception
	{
		List<PGPNode> nodes = new ArrayList<>(numNodes * 2);
		
		int numInvNodes = (int) (numNodes * params.ratInvalidCerts + 0.5);
		
		int numHonestIntros = (int) (numNodes * params.ratHonestIntros + 0.5);
		int numNeutralIntros = (int) (numNodes * params.ratNeutralIntros + 0.5);

		// generate random index of the invalid certificates
		int[] invalidIndexArray = Randoms.nextIntArray(numInvNodes, numNodes);
		
		// set introducer type
		int[] honestIndexArray = Randoms.nextIntArray(numHonestIntros, numNodes, null);
		int[] neutralIndexArray = Randoms.nextIntArray(numNeutralIntros, numNodes, honestIndexArray);

		int numLittleMistakeSigner = (int) (numNodes
				* params.ratLittleMistakeSigner + 0.5D);
		int numNeutralMistakeSigner = (int) (numNodes
				* params.ratNeutralMistakeSigner + 0.5D);

		int[] littleMistakeIndexArray = Randoms.nextIntArray(
				numLittleMistakeSigner, numNodes, null);
		int[] neutralMistakeIndexArray = Randoms.nextIntArray(
				numNeutralMistakeSigner, numNodes, littleMistakeIndexArray);

		// generate all the PGP nodes
		for (int i = 0; i < numNodes; i++)
		{
			PGPNode node;
			if (Arrays.binarySearch(invalidIndexArray, i) >= 0)
				node = new PGPNode(i, CertificateType.INVALID);
			else
				node = new PGPNode(i, CertificateType.VALID);
			
			if (Arrays.binarySearch(honestIndexArray, i) >= 0)
				node.setHonestType(PGPNode.HonestType.Honest);
			else if (Arrays.binarySearch(neutralIndexArray, i) >= 0)
				node.setHonestType(PGPNode.HonestType.Neutral);
			else {
				node.setHonestType(PGPNode.HonestType.Dishonest);
			}

			if (Arrays.binarySearch(littleMistakeIndexArray, i) >= 0)
				node.setMistakeType(PGPNode.MistakeSingerType.Little);
			else if (Arrays.binarySearch(neutralMistakeIndexArray, i) >= 0)
				node.setMistakeType(PGPNode.MistakeSingerType.Neutral);
			else
				node.setMistakeType(PGPNode.MistakeSingerType.Many);
			nodes.add(node);
		}

		return nodes;
	}
	
	private static int[] xpSign(List<PGPNode> nodes, EnvironmentParams params) throws Exception
	{
		int numSigners = (int) (numNodes * params.ratExperiencedSigner + 0.5);
		int[] signerIndexArray = Randoms.nextIntArray(numSigners, numNodes, null);
		
		for (int i = 0; i < signerIndexArray.length; i++)
		{
			// signer
			int signerIndex = signerIndexArray[i];
			PGPNode signer = nodes.get(signerIndex);
			signer.setSignerType(SignerType.ExperiencedSigner);
			
			/* part 1: directly signing */
			// targets: for different signer, the number of targets varies
			int numTargets = (int) (numNodes
					* Randoms.uniform(params.ratMinManySigning, params.ratMaxManySigning) + 0.5);
			int[] targetsIndexArray = Randoms.nextIntArray(numTargets, numNodes, new int[] { signerIndex });
			
			// specifies which nodes will be signed correctly
			int numHonestSigning;
			int[] honestIndexArray = null;
			switch (signer.getHonestType())
			{
				case Honest:
					numHonestSigning = (int) (numTargets
							* Randoms.uniform(params.ratMinHonestSigning, params.ratMaxHonestSigning) + 0.5);
					honestIndexArray = Randoms.nextIntArray(numHonestSigning, numTargets, null);
					break;
				case Neutral:
					numHonestSigning = (int) (numTargets
							* Randoms.uniform(params.ratMinNeutralSigning, params.ratMaxNeutralSigning) + 0.5);
					honestIndexArray = Randoms.nextIntArray(numHonestSigning, numTargets, null);
					break;
				case Dishonest:
					numHonestSigning = (int) (numTargets
							* Randoms.uniform(params.ratMinDishonestSigning, params.ratMaxDishonestSigning) + 0.5);
					honestIndexArray = Randoms.nextIntArray(numHonestSigning, numTargets, null);
					break;
				default:
					break;
			}

			int numMistakeTargets = 0;
			int[] mistakeIndexArray = (int[]) null;
			switch (signer.getMistakeType()) {
			case Little:
				numMistakeTargets = (int) (numTargets
							* Randoms
									.uniform(
								params.ratMinLittleMistakeHappen,
								params.ratMaxLittleMistakeHappen) + 0.5);
					mistakeIndexArray = Randoms.nextIntArray(
						numMistakeTargets, numTargets, null);
				break;
			case Neutral:
				numMistakeTargets = (int) (numTargets
							* Randoms.uniform(
								params.ratMinNeutralMistakeHappen,
								params.ratMaxNeutralMistakeHappen) + 0.5);
					mistakeIndexArray = Randoms.nextIntArray(
						numMistakeTargets, numTargets, null);
				break;
			case Many:
				numMistakeTargets = (int) (numTargets
							* Randoms.uniform(params.ratMinManyMistakeHappen,
								params.ratMaxManyMistakeHappen) + 0.5);
					mistakeIndexArray = Randoms.nextIntArray(
						numMistakeTargets, numTargets, null);
				break;
			}
			
			// sign targets
			for (int j = 0; j < numTargets; j++)
			{
				PGPNode target = nodes.get(targetsIndexArray[j]);
				sign(signer, target, honestIndexArray, mistakeIndexArray, j);
			}
			
			/* part 2: specify trusted neighbours */
			int numSpecifiedTargets = (int) (numTargets
					* Randoms.uniform(params.ratMinDirectlySpecified, params.ratMaxDirectlySpecified) + 0.5);
			if (numSpecifiedTargets <= 0)
				continue;

			/**
			 * rule out: 1) already singed targets 2) the current target itself
			 */
			int[] expts = new int[targetsIndexArray.length + 1];
			for (int k = 0; k < expts.length; k++) {
				if (k == expts.length - 1) {
					expts[k] = signerIndex;
				} else {
					if (k >= targetsIndexArray.length)
						continue;
					expts[k] = targetsIndexArray[k];
				}
			}
			Arrays.sort(expts);
			int[] specifiedTargetsIndexArray = Randoms.nextIntArray(
					numSpecifiedTargets, numNodes, expts);
			for (int k = 0; k < specifiedTargetsIndexArray.length; k++) {
				PGPNode target = nodes.get(specifiedTargetsIndexArray[k]);

				int t = Randoms.uniform(numNodes);
				TrustType trustness = t % 2 == 0 ? TrustType.MARGINALLY_TRUST
						: TrustType.COMPLETED_TRUST;
				signer.addSpecifiedTarget(target, trustness);
			}

		}
		
		return signerIndexArray;
	}
	
	private static void sign(PGPNode signer, PGPNode target,
			int[] honestIndexArray, int[] mistakeIndexArray, int index)
	{
		CertificateType ct;
		TrustType tt = TrustType.UNKNOWN_TRUST;

		if (Arrays.binarySearch(honestIndexArray, index) >= 0)
			ct = target.getCertificate() == CertificateType.VALID ? CertificateType.VALID : CertificateType.INVALID;
		else
			ct = target.getCertificate() == CertificateType.VALID ? CertificateType.INVALID : CertificateType.VALID;

		if (ct == CertificateType.INVALID) {
			signer.signTarget(target, ct, TrustType.UNKNOWN_TRUST);
			return;
		}
		if (Arrays.binarySearch(mistakeIndexArray, index) >= 0) {
			switch (target.getHonestType()) {
			case Honest:
				tt = TrustType.MARGINALLY_TRUST;
				break;
			case Neutral:
					tt = Randoms.uniform(10) % 2 == 0 ? TrustType.COMPLETED_TRUST
						: TrustType.UNKNOWN_TRUST;
				break;
			case Dishonest:
				tt = TrustType.MARGINALLY_TRUST;
				break;
			default:
				break;
			}
		} else {
			switch (target.getHonestType()) {
			case Honest:
				tt = TrustType.COMPLETED_TRUST;
				break;
			case Neutral:
				tt = TrustType.MARGINALLY_TRUST;
				break;
			case Dishonest:
				tt = TrustType.UNKNOWN_TRUST;
				break;
			}

		}

		signer.signTarget(target, ct, tt);
	}
	
	private static int[] mdSign(List<PGPNode> nodes, EnvironmentParams params, int[] exceptions)
			throws Exception
	{
		int numSigners = (int) (numNodes * params.ratMediumSigner + 0.5);
		int[] signerIndexArray = Randoms.nextIntArray(numSigners, numNodes, exceptions);
		
		int[] exceptionArray = new int[numNodes];
		for (int i = 0; i < exceptions.length; i++)
			exceptionArray[i] = exceptions[i];
		
		for (int i = 0; i < signerIndexArray.length; i++)
		{
			// signer
			int signerIndex = signerIndexArray[i];
			PGPNode signer = nodes.get(signerIndex);
			signer.setSignerType(SignerType.MediumSigner);
			
			// targets: for different signer, the number of targets varies
			int numTargets = (int) (numNodes
					* Randoms.uniform(params.ratMinNormalSigning, params.ratMaxNormalSigning) + 0.5);
			
			exceptionArray[exceptions.length] = signerIndex;
			int[] targetsIndexArray = Randoms.nextIntArray(numTargets, numNodes, exceptionArray);
			
			// specifies which nodes will be signed correctly
			int numHonestSigning;
			int[] honestIndexArray = null;
			switch (signer.getHonestType())
			{
				case Honest:
					numHonestSigning = (int) (numTargets
							* Randoms.uniform(params.ratMinHonestSigning, params.ratMaxHonestSigning) + 0.5);
					honestIndexArray = Randoms.nextIntArray(numHonestSigning, numTargets, null);
					break;
				case Neutral:
					numHonestSigning = (int) (numTargets
							* Randoms.uniform(params.ratMinNeutralSigning, params.ratMaxNeutralSigning) + 0.5);
					honestIndexArray = Randoms.nextIntArray(numHonestSigning, numTargets, null);
					break;
				case Dishonest:
					numHonestSigning = (int) (numTargets
							* Randoms.uniform(params.ratMinDishonestSigning, params.ratMaxDishonestSigning) + 0.5);
					honestIndexArray = Randoms.nextIntArray(numHonestSigning, numTargets, null);
					break;
				default:
					break;
			}

			int numMistakeTargets = 0;
			int[] mistakeIndexArray = null;
			switch (signer.getMistakeType()) {
			case Little:
				numMistakeTargets = (int) (numTargets
							* Randoms
									.uniform(
								params.ratMinLittleMistakeHappen,
								params.ratMaxLittleMistakeHappen) + 0.5);
					mistakeIndexArray = Randoms.nextIntArray(
						numMistakeTargets, numTargets, null);
				break;
			case Neutral:
				numMistakeTargets = (int) (numTargets
							* Randoms.uniform(
								params.ratMinNeutralMistakeHappen,
								params.ratMaxNeutralMistakeHappen) + 0.5);
					mistakeIndexArray = Randoms.nextIntArray(
						numMistakeTargets, numTargets, null);
				break;
			case Many:
				numMistakeTargets = (int) (numTargets
							* Randoms.uniform(params.ratMinManyMistakeHappen,
								params.ratMaxManyMistakeHappen) + 0.5);
					mistakeIndexArray = Randoms.nextIntArray(
						numMistakeTargets, numTargets, null);
				break;
			}
			
			// sign targets
			for (int j = 0; j < numTargets; j++) {
				PGPNode target = nodes.get(targetsIndexArray[j]);
				sign(signer, target, honestIndexArray, mistakeIndexArray, j);
			}

			/* part 2: specify trusted neighbours */
			int numSpecifiedTargets = (int) (numTargets
					* Randoms.uniform(params.ratMinDirectlySpecified, params.ratMaxDirectlySpecified) + 0.5);
			if (numSpecifiedTargets <= 0)
				continue;

			/**
			 * rule out:
			 * 
			 * 1) already singed targets, including xpSinged and mdSigned 2)
			 * targets with invalid certificate 3) the current target itself
			 */
			int[] expts = new int[exceptions.length + targetsIndexArray.length
					+ 1];
			for (int k = 0; k < expts.length; k++) {
				if (k < exceptions.length)
					expts[k] = exceptions[k];
				else if (k < expts.length - 1)
					expts[k] = targetsIndexArray[k - exceptions.length];
				else {
					if (k != expts.length - 1)
						continue;
					expts[k] = signerIndex;
				}
			}
			Arrays.sort(expts);
			int[] specifiedTargetsIndexArray = Randoms.nextIntArray(
					numSpecifiedTargets, numNodes, expts);
			for (int k = 0; k < specifiedTargetsIndexArray.length; k++)
			{
				PGPNode target = nodes.get(specifiedTargetsIndexArray[k]);

				int t = Randoms.uniform(numNodes);
				TrustType trustness = t % 2 == 0 ? TrustType.MARGINALLY_TRUST
						: TrustType.COMPLETED_TRUST;
				signer.addSpecifiedTarget(target, trustness);
			}

			/* end of part 2 */
		}
		return signerIndexArray;
	}
	
	private static int[] nbSign(List<PGPNode> nodes, EnvironmentParams params, int[] exceptions)
			throws Exception
	{
		int numSigners = (int) (numNodes * params.ratNewbieSigner + 0.5);
		int[] signerIndexArray = Randoms.nextIntArray(numSigners, numNodes, exceptions);
		
		int[] exceptionsArray = new int[exceptions.length + 1];
		for (int i = 0; i < exceptions.length; i++)
			exceptionsArray[i] = exceptions[i];
		
		for (int i = 0; i < signerIndexArray.length; i++)
		{
			// signer
			int signerIndex = signerIndexArray[i];
			PGPNode signer = nodes.get(signerIndex);
			signer.setSignerType(SignerType.NewbieSigner);
			
			// targets: for different signer, the number of targets varies
			int numTargets = (int) (numNodes
					* Randoms.uniform(params.ratMinLittleSigning, params.ratMaxLittleSigning) + 0.5);
			
			// avoid signing itself
			exceptionsArray[exceptions.length] = signerIndex;
			int[] targetsIndexArray = Randoms.nextIntArray(numTargets, numNodes, exceptionsArray);
			
			// specifies which nodes will be signed correctly
			int numHonestSigning;
			int[] honestIndexArray = null;
			switch (signer.getHonestType())
			{
				case Honest:
					numHonestSigning = (int) (numTargets
							* Randoms.uniform(params.ratMinHonestSigning, params.ratMaxHonestSigning) + 0.5);
					honestIndexArray = Randoms.nextIntArray(numHonestSigning, numTargets, null);
					break;
				case Neutral:
					numHonestSigning = (int) (numTargets
							* Randoms.uniform(params.ratMinNeutralSigning, params.ratMaxNeutralSigning) + 0.5);
					honestIndexArray = Randoms.nextIntArray(numHonestSigning, numTargets, null);
					break;
				case Dishonest:
					numHonestSigning = (int) (numTargets
							* Randoms.uniform(params.ratMinDishonestSigning, params.ratMaxDishonestSigning) + 0.5);
					honestIndexArray = Randoms.nextIntArray(numHonestSigning, numTargets, null);
					break;
				default:
					break;
			}

			int numMistakeTargets = 0;
			int[] mistakeIndexArray = (int[]) null;
			switch (signer.getMistakeType()) {
			case Little:
				numMistakeTargets = (int) (numTargets
							* Randoms
									.uniform(
								params.ratMinLittleMistakeHappen,
								params.ratMaxLittleMistakeHappen) + 0.5);
					mistakeIndexArray = Randoms.nextIntArray(
						numMistakeTargets, numTargets, null);
				break;
			case Neutral:
				numMistakeTargets = (int) (numTargets
							* Randoms.uniform(
								params.ratMinNeutralMistakeHappen,
								params.ratMaxNeutralMistakeHappen) + 0.5);
					mistakeIndexArray = Randoms.nextIntArray(
						numMistakeTargets, numTargets, null);
				break;
			case Many:
				numMistakeTargets = (int) (numTargets
							* Randoms.uniform(params.ratMinManyMistakeHappen,
								params.ratMaxManyMistakeHappen) + 0.5);
					mistakeIndexArray = Randoms.nextIntArray(
						numMistakeTargets, numTargets, null);
				break;
			}
			
			// sign targets
			for (int j = 0; j < numTargets; j++)
			{
				PGPNode target = nodes.get(targetsIndexArray[j]);
				sign(signer, target, honestIndexArray, mistakeIndexArray, j);
			}
			
			/* part 2: specify trusted neighbours */
			int numSpecifiedTargets = (int) (numTargets
					* Randoms.uniform(params.ratMinDirectlySpecified, params.ratMaxDirectlySpecified) + 0.5);
			if (numSpecifiedTargets > 0)
			{
				int[] expts = new int[exceptions.length + targetsIndexArray.length + 1];
				for (int k = 0; k < expts.length; k++)
				{
					if (k < exceptions.length)
						expts[k] = exceptions[k];
					else if (k < expts.length - 1)
						expts[k] = targetsIndexArray[k - exceptions.length];
					else {
						if (k != expts.length - 1)
							continue;
						expts[k] = signerIndex;
					}
				}
				Arrays.sort(expts);
				int[] specifiedTargetsIndexArray = Randoms
						.nextIntArray(numSpecifiedTargets, numNodes, expts);
				for (int k = 0; k < specifiedTargetsIndexArray.length; k++)
				{
					PGPNode target = nodes.get(specifiedTargetsIndexArray[k]);
					int t = Randoms.uniform(numNodes);
					TrustType trustness = t % 2 == 0 ? TrustType.MARGINALLY_TRUST : TrustType.COMPLETED_TRUST;
					signer.addSpecifiedTarget(target, trustness);
				}
			}
			/* end of part 2 */
		}
		return signerIndexArray;
	}

}
