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

import java.util.ArrayList;
import java.util.List;

import happy.coding.io.Strings;
import happy.coding.math.Randoms;
import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.intf.SocialRecommender;

/**
 * Pan and Chen, <strong>GBPR: Group Preference Based Bayesian Personalized
 * Ranking for One-Class Collaborative Filtering</strong>, IJCAI 2013.
 * 
 * @author guibing
 * 
 */
public class GBPR extends SocialRecommender {

	private float rho;
	private int gLen;

	public GBPR(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		isRankingPred = true;
		initByNorm = false;
	}

	@Override
	protected void initModel() throws Exception {
		// initialization
		super.initModel();

		itemBiases = new DenseVector(numItems);
		itemBiases.init();

		rho = cf.getFloat("GBPR.rho");
		gLen = cf.getInt("GBPR.group.size");
	}

	@Override
	protected void buildModel() throws Exception {

		for (int iter = 1; iter <= numIters; iter++) {

			loss = 0;
			errs = 0;

			DenseMatrix PS = new DenseMatrix(numUsers, numFactors);
			DenseMatrix QS = new DenseMatrix(numItems, numFactors);

			for (int s = 0, smax = numUsers * 100; s < smax; s++) {

				// uniformly draw (u, i, g, j)
				int u = 0, i = 0, j = 0;

				// u
				SparseVector Ru = null; // row u
				do {
					u = Randoms.uniform(trainMatrix.numRows());
					Ru = trainMatrix.row(u);
				} while (Ru.getCount() == 0);

				// i
				int[] is = Ru.getIndex();
				i = is[Randoms.uniform(is.length)];

				// g
				SparseVector Ci = trainMatrix.column(i); // column i
				int[] ws = Ci.getIndex();
				List<Integer> g = new ArrayList<>();
				if (ws.length <= gLen) {
					for (int w : ws)
						g.add(w);
				} else {

					while (gLen > 1) {
						int[] idxes = Randoms.nextIntArray(gLen - 1, ws.length);
						boolean flag = false;
						for (int idx : idxes) {
							int w = ws[idx];
							if (w == u) {
								// make sure u is not added again
								flag = true;
								break;
							}

							g.add(w);
						}
						if (!flag)
							break;
						g.clear(); // clear last iteration
					}

					g.add(u); // u in G
				}

				double pgui = predict(u, i, g);

				// j
				do {
					j = Randoms.uniform(numItems);
				} while (Ru.contains(j));

				double puj = predict(u, j);

				double pgij = pgui - puj;
				double vals = -Math.log(g(pgij));
				loss += vals;
				errs += vals;

				double cmg = g(-pgij);

				// update bi, bj
				double bi = itemBiases.get(i);
				itemBiases.add(i, lRate * (cmg + regI * bi));
				loss += regI * bi * bi;

				double bj = itemBiases.get(j);
				itemBiases.add(j, lRate * (-cmg + regI * bj));
				loss += regI * bj * bj;

				// update Pw
				double n = 1.0 / g.size();
				for (int w : g) {
					double delta = w == u ? 1 : 0;
					for (int f = 0; f < numFactors; f++) {
						double pwf = P.get(w, f);
						double qif = Q.get(i, f);
						double qjf = Q.get(j, f);

						double delta_pwf = rho * n * qif + (1 - rho) * delta
								* qif - delta * qjf;
						PS.add(w, f, cmg * delta_pwf + regU * pwf);

						loss += regU * pwf * pwf;
					}
				}

				double sum_w[] = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					for (int w : g) {
						sum_w[f] += P.get(w, f);
					}
				}

				// update Qi, Qj
				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qif = Q.get(i, f);
					double qjf = Q.get(j, f);

					double delta_qif = rho * n * sum_w[f] + (1 - rho) * puf;
					QS.add(i, f, cmg * delta_qif + regI * qif);
					loss += regU * qif * qif;

					double delta_qjf = -puf;
					QS.add(j, f, cmg * delta_qjf + regI * qjf);
					loss += regU * qjf * qjf;

				}
			}

			P = P.add(PS.scale(lRate));
			Q = Q.add(QS.scale(lRate));

			if (isConverged(iter))
				break;
		}
	}

	@Override
	protected double predict(int u, int j) {
		return itemBiases.get(j) + DenseMatrix.rowMult(P, u, Q, j);
	}

	protected double predict(int u, int j, List<Integer> g) {
		double ruj = predict(u, j);

		double sum = 0;
		for (int w : g)
			sum += DenseMatrix.rowMult(P, w, Q, j);

		double rgj = sum / g.size() + itemBiases.get(j);

		return rho * rgj + (1 - rho) * ruj;
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { binThold, rho, gLen, numFactors,
				initLRate, regU, regI, numIters }, ",");
	}

}
