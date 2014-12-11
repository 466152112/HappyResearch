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

import happy.coding.io.Strings;
import happy.coding.math.Randoms;

import java.util.ArrayList;
import java.util.List;

import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;
import librec.intf.IterativeRecommender;

/**
 * FSM: Factored (User and Item) Similarity Models for Item Recommendation
 * 
 * @author guoguibing
 * 
 */
public class FSM extends IterativeRecommender {

	private int rho;
	private float alpha;

	private DenseMatrix X, Y;

	public FSM(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		isRankingPred = true;
	}

	@Override
	protected void initModel() {
		X = new DenseMatrix(numItems, numFactors);
		Y = new DenseMatrix(numItems, numFactors);
		X.init(smallValue);
		Y.init(smallValue);

		P = new DenseMatrix(numUsers, numFactors);
		Q = new DenseMatrix(numUsers, numFactors);
		P.init(smallValue);
		Q.init(smallValue);

		itemBias = new DenseVector(numItems);
		itemBias.init(smallValue);

		rho = cf.getInt("FISM.rho");
		alpha = cf.getFloat("FISM.alpha");
	}

	@Override
	protected void buildModel() {

		for (int iter = 1; iter <= numIters; iter++) {

			errs = 0;
			loss = 0;

			// item similarity
			DenseMatrix XS = new DenseMatrix(numItems, numFactors);
			DenseMatrix YS = new DenseMatrix(numItems, numFactors);

			// user similarity
			DenseMatrix PS = new DenseMatrix(numUsers, numFactors);
			DenseMatrix QS = new DenseMatrix(numUsers, numFactors);

			for (int u : trainMatrix.rows()) {
				SparseVector Ru = trainMatrix.row(u);

				for (VectorEntry ve : Ru) {
					int i = ve.index();
					double rui = ve.get();

					SparseVector Ci = trainMatrix.column(i);

					// make a random sample of negative feedback
					List<Integer> js = new ArrayList<>();
					int len = 0;
					while (len < rho) {
						int j = Randoms.uniform(numItems);
						if (Ru.contains(j) || js.contains(j))
							continue;

						js.add(j);
						len++;
					}

					double wci = Ci.getCount() - 1 > 0 ? Math.pow(Ci.getCount() - 1, -alpha) : 0;

					// user similarity
					double sum_ci = 0;
					double[] sum_cif = new double[numFactors];
					for (int f = 0; f < numFactors; f++) {
						for (VectorEntry vk : Ci) {
							int v = vk.index();
							if (u != v) {
								double pvf = P.get(v, f);
								sum_cif[f] += pvf;
								sum_ci += pvf * Q.get(u, f);
							}
						}
					}

					// item similarity
					double sum_ri = 0;
					double[] sum_rif = new double[numFactors];
					double[] sum_rjf = new double[numFactors];
					for (int f = 0; f < numFactors; f++) {
						for (VectorEntry vk : Ru) {
							int k = vk.index();
							double xkf = X.get(k, f);
							if (k != i) {
								sum_rif[f] += xkf;
								sum_ri += xkf * Y.get(i, f);
							}
							sum_rjf[f] += xkf;
						}
					}
					double wri = Ru.getCount() - 1 > 0 ? Math.pow(Ru.getCount() - 1, -alpha) : 0;
					double wrj = Ru.getCount() > 0 ? Math.pow(Ru.getCount(), -alpha) : 0;

					double ru[] = new double[numFactors];
					double ci[] = new double[numFactors];
					// update for each unrated item
					for (int j : js) {

						// declare variables first to speed up
						int v, f;
						double pvf, quf, delta;

						// item similarity
						double sum_rj = 0;
						for (VectorEntry vk : Ru) {
							int k = vk.index();
							sum_rj += DenseMatrix.rowMult(X, k, Y, j);
						}

						// user similarity
						SparseVector Cj = trainMatrix.column(j);
						double sum_cj = 0;
						double[] sum_cjf = new double[numFactors];
						for (f = 0; f < numFactors; f++) {
							for (VectorEntry vk : Cj) {
								v = vk.index();
								// sum_j += DenseMatrix.rowMult(P, v, Q, u);

								pvf = P.get(v, f);
								sum_cjf[f] += pvf;
								sum_cj += pvf * Q.get(u, f);
							}
						}
						double wcj = Cj.getCount() > 0 ? Math.pow(Cj.getCount(), -alpha) : 0;

						double bi = itemBias.get(i), bj = itemBias.get(j);
						double pui = bi + wri * sum_ri + wci * sum_ci;
						double puj = bj + wrj * sum_rj + wcj * sum_cj;
						double ruj = 0;
						double eij = (rui - ruj) - (pui - puj);

						errs += eij * eij;
						loss += eij * eij;

						// update bi
						itemBias.add(i, -lRate * (eij + regB * bi));

						// update bj
						itemBias.add(j, -lRate * (eij - regB * bj));

						loss += regB * bi * bi - regB * bj * bj;

						// update yif, yjf
						for (f = 0; f < numFactors; f++) {
							double yif = Y.get(i, f), yjf = Y.get(j, f);

							delta = eij * (-wri) * sum_rif[f] + regI * yif;
							YS.add(i, f, -lRate * delta);

							delta = eij * wrj * sum_rjf[f] - regI * yjf;
							YS.add(j, f, -lRate * delta);

							loss += regI * (yif * yif - yjf * yjf);

							ru[f] += eij * (wrj * yjf - wri * yif);
						}

						// update quf
						for (f = 0; f < numFactors; f++) {
							quf = Q.get(u, f);

							delta = eij * (wcj * sum_cjf[f] - wci * sum_cif[f]) + regU * quf;
							QS.add(u, f, -lRate * delta);

							ci[f] += -eij * quf;

							loss += regU * quf * quf;
						}

						// update pvf for v in Cj
						for (f = 0; f < numFactors; f++) {
							for (VectorEntry vk : Cj) {
								v = vk.index();
								pvf = P.get(v, f);
								delta = eij * wcj * Q.get(u, f) - regU * pvf;
								PS.add(v, f, -lRate * delta);

								loss -= regU * pvf * pvf;
							}
						}
					}

					// update xkf for k in Ru
					for (int f = 0; f < numFactors; f++) {
						for (VectorEntry vk : Ru) {
							int k = vk.index();
							if (k != i) {
								double xkf = X.get(k, f);
								double delta = ru[f] / rho + regI * xkf;
								XS.add(k, f, -lRate * delta);

								loss += regI * xkf * xkf;
							}
						}
					}

					// update pvf for v in Ci
					for (int f = 0; f < numFactors; f++) {
						for (VectorEntry vk : Ci) {
							int v = vk.index();
							if (v != u) {
								double pvf = P.get(v, f);
								double delta = wci * ci[f] / rho + regU * pvf;
								PS.add(v, f, -lRate * delta);

								loss += regU * pvf * pvf;
							}
						}
					}
				}
			}

			X = X.add(XS);
			Y = Y.add(YS);

			P = P.add(PS);
			Q = Q.add(QS);

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

	@Override
	protected double predict(int u, int i) {

		double sum_r = 0, sum_c = 0;
		int count_r = 0, count_c = 0;

		// item similarity
		SparseVector Ru = trainMatrix.row(u);
		for (VectorEntry ve : Ru) {
			int k = ve.index();
			if (k != i) {
				sum_r += DenseMatrix.rowMult(X, k, Y, i);
				count_r++;
			}
		}
		double wr = count_r > 0 ? Math.pow(count_r, -alpha) : 0;

		// user similarity
		SparseVector Ci = trainMatrix.column(i);
		for (VectorEntry ve : Ci) {
			int v = ve.index();
			// for test, i and j will be always unequal as j is unrated
			if (v != u) {
				sum_c += DenseMatrix.rowMult(P, v, Q, u);
				count_c++;
			}
		}
		double wc = count_c > 0 ? Math.pow(count_c, -alpha) : 0;

		return itemBias.get(i) + wr * sum_r + wc * sum_c;
	}

	@Override
	public String toString() {
		return Strings
				.toString(new Object[] { binThold, rho, alpha, numFactors, initLRate, regU, regI, regB, numIters });
	}
}
