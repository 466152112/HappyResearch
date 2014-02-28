// Copyright (C) 2014 Guibing Guo
//
// This file is part of LibRec.
//
// LibRec is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LibRec is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package librec.undefined;

import happy.coding.io.Strings;
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

			DenseMatrix PS = new DenseMatrix(numUsers, numFactors);
			DenseMatrix QS = new DenseMatrix(numItems, numFactors);

			// ratings
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

				double[] sum_us = new double[numFactors];
				SparseVector tu = socialMatrix.row(u);
				int[] tks = tu.getIndex();
				for (int f = 0; f < numFactors; f++) {
					for (int k : tks)
						sum_us[f] += tu.get(k) * P.get(k, f); // /tks.length;
				}

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					double usgd = alpha * csgd * qjf + regU * puf;
					double jsgd = csgd * (alpha * puf + (1 - alpha) * sum_us[f]) + regI * qjf;

					PS.add(u, f, usgd);
					QS.add(j, f, jsgd);

					loss += regU * puf * puf + regI * qjf * qjf;
				}
			}

			// social
			for (int u = 0; u < numUsers; u++) {
				SparseVector bu = socialMatrix.column(u);
				for (int p : bu.getIndex()) {
					if (p >= trainMatrix.numRows())
						continue;

					SparseVector pr = trainMatrix.row(p);
					for (int j : pr.getIndex()) {
						double pred = predict(p, j, false);
						double epj = g(pred) - normalize(pr.get(j));
						double csgd = gd(pred) * epj * bu.get(p);

						for (int f = 0; f < numFactors; f++)
							PS.add(u, f, (1 - alpha) * csgd * Q.get(j, f));
					}
				}
			}

			loss *= 0.5;
			errs *= 0.5;

			P = P.add(PS.scale(-lRate));
			Q = Q.add(QS.scale(-lRate));

			if (isConverged(iter))
				break;
		}
	}

	@Override
	protected double predict(int u, int j, boolean bound) {
		double pred1 = DenseMatrix.rowMult(P, u, Q, j);
		double pred2 = 0.0;
		SparseVector tu = socialMatrix.row(u);
		//int count = tu.getCount();
		for (int k : tu.getIndex())
			pred2 += tu.get(k) * DenseMatrix.rowMult(P, k, Q, j);

		double pred = alpha * pred1 + (1 - alpha) * pred2;

		if (bound)
			return denormalize(g(pred));

		return pred;
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { initLRate, (float) regU, (float) regI, numFactors, maxIters,
				isBoldDriver, (float) alpha }, ",");
	}
}
