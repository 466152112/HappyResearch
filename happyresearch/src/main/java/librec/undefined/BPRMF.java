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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package librec.undefined;

import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.math.Randoms;
import librec.data.DenseMatrix;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.intf.IterativeRecommender;

/**
 * 
 * Rendle et al., <strong>BPR: Bayesian Personalized Ranking from Implicit
 * Feedback</strong>, UAI 2009.
 * 
 * @author guoguibing
 * 
 */
public class BPRMF extends IterativeRecommender {

	private double regJ;

	public BPRMF(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "BPRMF";
		isRankingPred = true;
		initByNorm = false;

		regJ = cf.getDouble("BPRMF.reg.j");
	}

	@Override
	protected void buildModel() {

		for (int iter = 1; iter <= maxIters; iter++) {

			int sampleSize = numUsers * 100;
			if (verbose)
				Logs.debug("Sample size = {}, running at iteration = {}", sampleSize, iter);

			DenseMatrix Ps = new DenseMatrix(numUsers, numFactors);
			DenseMatrix Qs = new DenseMatrix(numItems, numFactors);

			for (int s = 0; s < sampleSize; s++) {

				// draw (u, i, j) from Ds with replacement
				int u = 0, i = 0, j = 0;

				while (true) {
					u = Randoms.uniform(numUsers);
					SparseVector pu = trainMatrix.row(u);

					if (pu.getCount() == 0)
						continue;

					int[] is = pu.getIndex();
					i = is[Randoms.uniform(is.length)];

					do {
						j = Randoms.uniform(numItems);
					} while (pu.contains(j));

					break;
				}

				// update \theta 
				double xui = predict(u, i);
				double xuj = predict(u, j);
				double xuij = xui - xuj;

				double cmg = 1.0 / (1 + Math.exp(xuij));

				for (int f = 0; f < numFactors; f++) {
					double wuf = P.get(u, f);
					double hif = Q.get(i, f);
					double hjf = Q.get(j, f);

					Ps.add(u, f, cmg * (hif - hjf) + regU * wuf);
					Qs.add(i, f, cmg * wuf + regI * hif);
					Qs.add(j, f, cmg * (-wuf) + regJ * hjf);
				}
			}

			P = P.add(Ps.scale(lRate));
			Q = Q.add(Qs.scale(lRate));
		}
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { numFactors, lRate, regU, regI, regJ, maxIters }, ",");
	}
}
