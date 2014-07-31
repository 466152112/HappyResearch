package librec.undefined;

import librec.data.DenseMatrix;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.intf.SocialRecommender;

public class TrustPredictor extends SocialRecommender {
	
	public static void update(){
		LibRec.rateMatrix = socialMatrix;
	}

	public TrustPredictor(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);
	}
	
	@Override
	protected void initModel() {
		P = new DenseMatrix(numUsers, numFactors);
		Q = new DenseMatrix(numUsers, numFactors);
		P.init(0.01);
		Q.init(0.01);
	}
	
	@Override
	protected void buildModel() {
		for (int iter = 1; iter <= numIters; iter++) {

			loss = 0;
			errs = 0;
			for (MatrixEntry me : trainMatrix) {

				int u = me.row(); // user
				int j = me.column(); // item

				double ruj = me.get();
				if (ruj <= 0.0)
					continue;

				double pred = predict(u, j, false);
				double euj = ruj - pred;

				errs += euj * euj;
				loss += euj * euj;

				// update factors
				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					double delta_u = euj * qjf - regU * puf;
					double delta_j = euj * puf - regI * qjf;

					P.add(u, f, lRate * delta_u);
					Q.add(j, f, lRate * delta_j);

					loss += regU * puf * puf + regI * qjf * qjf;
				}

			}

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;

		}// end of training
	}

}
