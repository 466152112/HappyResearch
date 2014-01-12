package happy.research.pgp;

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

public class EnvironmentParams
{
	private static EnvironmentParams params;
	
	public int 	  runTimes = 1;
	public int 	  numNodes;
	public double ratInvalidCerts;
	
	public double ratHonestIntros;
	public double ratNeutralIntros;
	
	public double ratMinHonestSigning;
	public double ratMaxHonestSigning;
	public double ratMinNeutralSigning;
	public double ratMaxNeutralSigning;
	public double ratMinDishonestSigning;
	public double ratMaxDishonestSigning;

	public double ratMinLittleSigning;
	public double ratMaxLittleSigning;
	public double ratMinNormalSigning;
	public double ratMaxNormalSigning;
	public double ratMinManySigning;
	public double ratMaxManySigning;

	public double ratExperiencedSigner;
	public double ratMediumSigner;
	public double ratNewbieSigner;
	
	public double ratMinDirectlySpecified;
	public double ratMaxDirectlySpecified;
	
	public int    completesNeeded;
	public int    marginalsNeeded;
	
	public double confidence_threshold;
	public double similarity_threshold;
	
	public double ratCandidateVector;
	
	public double complete_trust_threshold;
	public double marginal_trust_threshold;
	public double chain_trust_value_threshold;

	public double ratNeutralMistakeSigner;
	public double ratLittleMistakeSigner;

	public double ratMinManyMistakeHappen;
	public double ratMaxManyMistakeHappen;
	public double ratMinNeutralMistakeHappen;
	public double ratMaxNeutralMistakeHappen;
	public double ratMinLittleMistakeHappen;
	public double ratMaxLittleMistakeHappen;
	
	// Entry keys
	private final static String NODE_NUMBERS = "nodes.number";
	private final static String CERTIFICATES_INVALID_RATIO = "certificates.invalid.ratio";
	
	private final static String HONEST_RATIO = "honest.ratio";
	private final static String NEUTRAL_RATIO = "neutral.ratio";
	private final static String HONEST_HONEST_SIGNING_RATIO_MIN = "honest.honest.signing.ratio.min";
	private final static String HONEST_HONEST_SIGNING_RATIO_MAX = "honest.honest.signing.ratio.max";
	private final static String NEUTRAL_HONEST_SINGING_RATIO_MIN = "neutral.honest.singing.ratio.min";
	private final static String NEUTRAL_HONEST_SINGING_RATIO_MAX = "neutral.honest.singing.ratio.max";
	private final static String DISHONEST_HONEST_SINGING_RATIO_MIN = "dishonest.honest.singing.ratio.min";
	private final static String DISHONEST_HONEST_SINGING_RATIO_MAX = "dishonest.honest.singing.ratio.max";

	private final static String SIGNED_TARGETS_MANY_RATIO_MIN = "signed.targets.many.ratio.min";
	private final static String SIGNED_TARGETS_MANY_RATIO_MAX = "signed.targets.many.ratio.max";
	private final static String SIGNED_TARGETS_NORMAL_RATIO_MIN = "signed.targets.normal.ratio.min";
	private final static String SIGNED_TARGETS_NORMAL_RATIO_MAX = "signed.targets.normal.ratio.max";
	private final static String SIGNED_TARGETS_LITTLE_RATIO_MIN = "signed.targets.little.ratio.min";
	private final static String SIGNED_TARGETS_LITTLE_RATIO_MAX = "signed.targets.little.ratio.max";

	private final static String EXPERIENCED_SIGNER_RATIO = "experienced.signer.ratio";
	private final static String MEDIUM_SIGNER_RATIO = "medium.signer.ratio";
	private final static String NEWBIE_SIGNER_RATIO = "newbie.signer.ratio";

	private final static String DIRECTLY_SPECIFIED_RATIO_MIN = "directly.specified.ratio.min";
	private final static String DIRECTLY_SPECIFIED_RATIO_MAX = "directly.specified.ratio.max";

	private static final String SIGNER_NEUTRAL_MISTAKE_RATIO = "signer.neutral.mistake.ratio";
	private static final String SIGNER_LITTLE_MISTAKE_RATIO = "signer.little.mistake.ratio";

	private static final String MANY_MISTAKE_HAPPEN_RATIO_MIN = "many.mistake.happen.ratio.min";
	private static final String MANY_MISTAKE_HAPPEN_RATIO_MAX = "many.mistake.happen.ratio.max";
	private static final String NEUTRAL_MISTAKE_HAPPEN_RATIO_MIN = "neutral.mistake.happen.ratio.min";
	private static final String NEUTRAL_MISTAKE_HAPPEN_RATIO_MAX = "neutral.mistake.happen.ratio.max";
	private static final String LITTLE_MISTAKE_HAPPEN_RATIO_MIN = "little.mistake.happen.ratio.min";
	private static final String LITTLE_MISTAKE_HAPPEN_RATIO_MAX = "little.mistake.happen.ratio.max";

	private final static String COMPLETES_NEEDED = "completes.needed";
	private final static String MARGINALS_NEEDED = "marginals.needed";
	
	private final static String CONFIDENCE_THRESHOLD = "confidence.threshold";
	private final static String SIMILARITY_THRESHOLD = "similarity.threshold";
	
	private final static String CANDIDATE_VECTOR_RATIO = "candidate.vector.ratio";
	
	private final static String COMPLETE_TRUST_THRESHOLD = "complete.trust.threshold";
	private final static String MARGINAL_TRUST_THRESHOLD = "marginal.trust.threshold";
	
	private final static String RUN_TIMES = "run.times";
	private static Logger logger = Logger.getLogger(EnvironmentParams.class);
	
	private EnvironmentParams()
	{
	}

	public static EnvironmentParams createInstance()
	{
		if (params == null) params = new EnvironmentParams();
		return params;
	}

	public void loadConfigFile(String config)
	{
		Properties p = new Properties();
		try
		{
			p.load(new FileInputStream(config));

			numNodes = Integer.parseInt(p.getProperty(NODE_NUMBERS, "100"));
			ratInvalidCerts = Double.parseDouble(p.getProperty(CERTIFICATES_INVALID_RATIO, "0.05"));
			
			ratHonestIntros = Double.parseDouble(p.getProperty(HONEST_RATIO, "0.40"));
			ratNeutralIntros = Double.parseDouble(p.getProperty(NEUTRAL_RATIO, "0.30"));
			
			ratMinHonestSigning = Double.parseDouble(p.getProperty(HONEST_HONEST_SIGNING_RATIO_MIN, "0.80"));
			ratMaxHonestSigning = Double.parseDouble(p.getProperty(HONEST_HONEST_SIGNING_RATIO_MAX, "0.10"));
			ratMinNeutralSigning = Double.parseDouble(p.getProperty(NEUTRAL_HONEST_SINGING_RATIO_MIN, "0.40"));
			ratMaxNeutralSigning = Double.parseDouble(p.getProperty(NEUTRAL_HONEST_SINGING_RATIO_MAX, "0.60"));
			ratMinDishonestSigning = Double.parseDouble(p.getProperty(DISHONEST_HONEST_SINGING_RATIO_MIN, "0.0"));
			ratMaxDishonestSigning = Double.parseDouble(p.getProperty(DISHONEST_HONEST_SINGING_RATIO_MAX, "0.20"));

			ratMinManySigning = Double.parseDouble(p.getProperty(SIGNED_TARGETS_MANY_RATIO_MIN, "0.15"));
			ratMaxManySigning = Double.parseDouble(p.getProperty(SIGNED_TARGETS_MANY_RATIO_MAX, "0.20"));
			ratMinNormalSigning = Double.parseDouble(p.getProperty(SIGNED_TARGETS_NORMAL_RATIO_MIN, "0.08"));
			ratMaxNormalSigning = Double.parseDouble(p.getProperty(SIGNED_TARGETS_NORMAL_RATIO_MAX, "0.13"));
			ratMinLittleSigning = Double.parseDouble(p.getProperty(SIGNED_TARGETS_LITTLE_RATIO_MIN, "0.00"));
			ratMaxLittleSigning = Double.parseDouble(p.getProperty(SIGNED_TARGETS_LITTLE_RATIO_MAX, "0.05"));
			
			ratExperiencedSigner = Double.parseDouble(p.getProperty(EXPERIENCED_SIGNER_RATIO, "0.4"));
			ratMediumSigner = Double.parseDouble(p.getProperty(MEDIUM_SIGNER_RATIO, "0.5"));
			ratNewbieSigner = Double.parseDouble(p.getProperty(NEWBIE_SIGNER_RATIO, "0.1"));
			
			ratMinDirectlySpecified = Double.parseDouble(p.getProperty(DIRECTLY_SPECIFIED_RATIO_MIN, "0.0"));
			ratMaxDirectlySpecified = Double.parseDouble(p.getProperty(DIRECTLY_SPECIFIED_RATIO_MAX, "0.1"));
			
			ratNeutralMistakeSigner = Double.parseDouble(p.getProperty(SIGNER_NEUTRAL_MISTAKE_RATIO, "0.4"));
			ratLittleMistakeSigner = Double.parseDouble(p.getProperty(SIGNER_LITTLE_MISTAKE_RATIO, "0.4"));
			  
			ratMinManyMistakeHappen = Double.parseDouble(p.getProperty(MANY_MISTAKE_HAPPEN_RATIO_MIN, "0.78"));
			ratMaxManyMistakeHappen = Double.parseDouble(p.getProperty(MANY_MISTAKE_HAPPEN_RATIO_MAX, "1.0"));
			ratMinNeutralMistakeHappen = Double.parseDouble(p.getProperty(NEUTRAL_MISTAKE_HAPPEN_RATIO_MIN, "0.45"));
			ratMaxNeutralMistakeHappen = Double.parseDouble(p.getProperty(NEUTRAL_MISTAKE_HAPPEN_RATIO_MAX, "0.65"));
			ratMinLittleMistakeHappen = Double.parseDouble(p.getProperty(LITTLE_MISTAKE_HAPPEN_RATIO_MIN, "0.01"));
			ratMaxLittleMistakeHappen = Double.parseDouble(p.getProperty(LITTLE_MISTAKE_HAPPEN_RATIO_MAX, "0.21"));
			
			completesNeeded = Integer.parseInt(p.getProperty(COMPLETES_NEEDED, "1"));
			marginalsNeeded = Integer.parseInt(p.getProperty(MARGINALS_NEEDED, "2"));
			
			confidence_threshold = Double.parseDouble(p.getProperty(CONFIDENCE_THRESHOLD, "0.75"));
			similarity_threshold = Double.parseDouble(p.getProperty(SIMILARITY_THRESHOLD, "0.90"));
			
			ratCandidateVector = Double.parseDouble(p.getProperty(CANDIDATE_VECTOR_RATIO, "0.5"));
			
			complete_trust_threshold = Double.parseDouble(p.getProperty(COMPLETE_TRUST_THRESHOLD, "0.78"));
			marginal_trust_threshold = Double.parseDouble(p.getProperty(MARGINAL_TRUST_THRESHOLD, "0.48"));
			
			runTimes = Integer.parseInt(p.getProperty(RUN_TIMES, "1"));
			chain_trust_value_threshold = Double.parseDouble(p.getProperty("chain.trust.value.threshold", "0.55"));

		} catch (Exception e)
		{
			logger.error("Cannot load config file " + config);
			logger.error(e.getMessage());
		}
	}

}
