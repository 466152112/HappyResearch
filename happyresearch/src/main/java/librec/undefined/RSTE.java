package librec.undefined;

import librec.data.DenseMatrix;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.intf.SocialRecommender;

/**
 * Hao Ma, Irwin King and Michael R. Lyu, <strong>Learning to Recommend with
 * Social Trust Ensemble</strong>, SIGIR 2009.
 * 
 * @author guoguibing
 * 
 */
public class RSTE extends SocialRecommender {

	private double alpha;

	public RSTE(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "RSTE";

		initByNorm = false;
		alpha = 0.4;
	}

	@Override
	protected void buildModel() {
		for (int iter = 1; iter <= maxIters; iter++) {

			loss = 0;
			errs = 0;

			for (MatrixEntry me : trainMatrix) {
				int u = me.row();
				int j = me.column();
				double rate = me.get();

				if (rate <= 0)
					continue;

				double ruj = normalize(rate);
				double pred = predict(u, j, false);
				double euj = g(pred) - ruj;

				errs += euj * euj;
				loss += euj * euj;

				double csgd = gd(pred) * euj;

				SparseVector tu = socialMatrix.row(u);
				int[] tks = tu.getIndex();
				double[] sum_us = new double[numFactors];
				for (int k : tks) {
					for (int f = 0; f < numFactors; f++)
						sum_us[f] += tu.get(k) * P.get(k, f);
				}

				SparseVector bu = socialMatrix.column(u);
				int[] bps = bu.getIndex();
				double csgd_p = 0;
				for (int p : bps) {
					if (p < trainMatrix.numRows()) {
						double rpj = trainMatrix.get(p, j);
						if (rpj > 0) {
							double pp = predict(p, j, false);
							double epj = g(pp) - normalize(rpj);
							double tpu = bu.get(p);
							csgd_p += gd(pp) * epj * tpu;
						}
					}
				}

				for (int f = 0; f < numFactors; f++) {
					double usgd = alpha * csgd * Q.get(j, f) + regU * P.get(u, f) + (1 - alpha) * csgd_p * Q.get(j, f);
					double jsgd = csgd * (alpha * P.get(u, f) + (1 - alpha) * sum_us[f]) + regI * Q.get(j, f);

					P.add(u, f, -lRate * usgd);
					Q.add(j, f, -lRate * jsgd);
				}
			}
			loss *= 0.5;
			errs *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

	@Override
	protected double predict(int u, int j, boolean bound) {
		double pred1 = DenseMatrix.rowMult(P, u, Q, j);
		double pred2 = 0.0;
		SparseVector tu = socialMatrix.row(u);
		for (int k : tu.getIndex())
			pred2 += tu.get(k) * DenseMatrix.rowMult(P, k, Q, j);

		double pred = alpha * pred1 + (1 - alpha) * pred2;

		if (bound)
			return denormalize(g(pred));

		return pred;
	}

}
