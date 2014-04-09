package happy.research.cf;


public class RunCF {

	private final static String on = "on";

	public static void main(String[] args) throws Exception {
		// Logs.config("log4j.xml", true);
		
		ConfigParams params = ConfigParams.defaultInstance();
		AbstractCF.params = params;
		params.printSpecs();

		runMethod(params);

		if (!params.BATCH_RUN)
			AbstractCF.collectResults();
	}

	private static void runMethod(ConfigParams params) throws Exception {
		if (params.CLASSIC_CF)
			new ClassicCF_mt().execute();
		if (params.VR_CF)
			new VirRatingsCF().execute();
		if (params.HYBRID_CT)
			new HybridCT_mt().execute();
		if (params.TCF)
			new TCF_mt().execute();

		if (params.TRUST_ALL)
			new TrustAll_mt().execute();
		if (params.TRUST_ALL_PLUS)
			new TrustAllPlus_mt().execute();
		if (params.GLOBAL_AVERAGE)
			new GlobleAvg_mt().execute();

		if (params.MOLETRUST)
			new MTx_mt().execute();
		if (params.TIDALTRUST)
			new TTx_mt().execute();

		if (params.DT_IMPUTE)
			new DT_Impute_mt().execute();

		if (params.TRUST_WALKER)
			new TrustWalker_mt().execute();
		if (params.RECONSTRUCTION)
			new RN_mt().execute();

		/* only k-fold cross validation */
		if (params.kNRTrust)
			new KNRTrust_mt().execute();
		if (params.COGTRUST)
			new CogTrust_mt().execute();
		if (params.MultAspect)
			new MATrust_mt().execute();

		if (params.readParam("SlopeOne.run").equals(on))
			new SlopeOne_mt().execute();

		/* run ad-hoc methods */
		switch (params.readParam("Run.method")) {
		case "TSF":
			new TSF_mt().execute();
			break;
		case "Merge":
			new Merge_mt().execute();
			break;
		case "iTrust":
			new ITrust_mt().execute();
			break;
		case "CBF":
			new CBF_mt().execute();
			break;
		case "recsys-course":
			new RecSysCourse_mt().execute();
			break;
		default:
			break;
		}
	}
}
